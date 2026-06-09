package com.lenilestari.aethersea.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.lenilestari.aethersea.data.model.LearningCandidate
import com.lenilestari.aethersea.data.model.NewAliasSuggestion
import com.lenilestari.aethersea.data.model.NewItemSuggestion
import com.lenilestari.aethersea.data.model.NewUnitSuggestion
import com.lenilestari.aethersea.util.AppLogger
import com.lenilestari.aethersea.util.FirestoreInstance
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

class LearningRepository {
    private val db = FirestoreInstance.db
    private val candidates = db.collection("learning_candidates")
    private val kamus = db.collection("kamus")
    private companion object { const val TAG = "LearningRepo" }

    suspend fun upsertNewItem(suggestion: NewItemSuggestion) {
        if (suggestion.name.isBlank()) return
        val docId = "item_${suggestion.name.lowercase().replace(" ", "_")}"
        upsert(docId) {
            LearningCandidate(
                id = docId,
                type = "new_item",
                itemName = suggestion.name,
                category = suggestion.category,
                commonUnits = suggestion.commonUnits,
                lastSeenMs = System.currentTimeMillis()
            )
        }
    }

    suspend fun upsertNewAlias(suggestion: NewAliasSuggestion) {
        if (suggestion.alias.isBlank() || suggestion.itemName.isBlank()) return
        val docId = "alias_${suggestion.alias.lowercase().replace(" ", "_")}"
        upsert(docId) {
            LearningCandidate(
                id = docId,
                type = "new_alias",
                itemName = suggestion.itemName,
                alias = suggestion.alias,
                lastSeenMs = System.currentTimeMillis()
            )
        }
    }

    suspend fun upsertNewUnit(suggestion: NewUnitSuggestion) {
        if (suggestion.original.isBlank()) return
        val docId = "unit_${suggestion.original.lowercase().replace(" ", "_")}"
        upsert(docId) {
            LearningCandidate(
                id = docId,
                type = "new_unit",
                originalUnit = suggestion.original,
                normalizedUnit = suggestion.normalized.ifBlank { suggestion.original },
                lastSeenMs = System.currentTimeMillis()
            )
        }
    }

    private suspend fun upsert(docId: String, buildNew: () -> LearningCandidate) {
        try {
            val ref = candidates.document(docId)
            val snap = withTimeoutOrNull(8_000L) { ref.get().await() }
            if (snap == null) {
                AppLogger.w(TAG, "upsert timeout getting [$docId]")
                return
            }
            if (snap.exists()) {
                val currentCount = snap.getLong("count") ?: 0
                withTimeoutOrNull(8_000L) {
                    ref.update(mapOf(
                        "count"      to FieldValue.increment(1),
                        "lastSeenMs" to System.currentTimeMillis(),
                        "updatedAt"  to Timestamp.now()
                    )).await()
                }
                AppLogger.d(TAG, "upsert INCREMENT [$docId] count: $currentCount → ${currentCount + 1}")
            } else {
                val now       = Timestamp.now()
                val candidate = buildNew().copy(createdAt = now, updatedAt = now)
                withTimeoutOrNull(8_000L) { ref.set(candidate).await() }
                AppLogger.d(TAG, "upsert NEW [$docId] type=${candidate.type} itemName=${candidate.itemName}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e(TAG, "upsert FAILED [$docId]", e)
        }
    }

    private suspend fun promoteItem(c: LearningCandidate) {
        if (c.itemName.isBlank()) return
        try {
            val docId = c.itemName.replace(" ", "_")
            val data = mapOf(
                "id" to docId,
                "name" to c.itemName,
                "category" to c.category,
                "aliases" to emptyList<String>(),
                "commonUnits" to c.commonUnits
            )
            withTimeoutOrNull(8_000L) { kamus.document(docId).set(data).await() }
            AppLogger.d(TAG, "PROMOTE item → kamus: \"${c.itemName}\" [${c.category}]")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e(TAG, "PROMOTE item FAILED \"${c.itemName}\"", e)
        }
    }

    private suspend fun promoteAlias(c: LearningCandidate) {
        if (c.itemName.isBlank() || c.alias.isBlank()) return
        try {
            val snap = withTimeoutOrNull(8_000L) {
                kamus.whereEqualTo("name", c.itemName).get().await()
            }
            if (snap == null) {
                AppLogger.w(TAG, "PROMOTE alias timeout looking up \"${c.itemName}\"")
                return
            }
            if (!snap.isEmpty) {
                withTimeoutOrNull(8_000L) {
                    snap.documents.first().reference
                        .update("aliases", FieldValue.arrayUnion(c.alias))
                        .await()
                }
                AppLogger.d(TAG, "PROMOTE alias → kamus: \"${c.alias}\" → \"${c.itemName}\"")
            } else {
                AppLogger.w(TAG, "PROMOTE alias SKIP: item \"${c.itemName}\" tidak ada di kamus")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e(TAG, "PROMOTE alias FAILED \"${c.alias}\"", e)
        }
    }

    suspend fun promoteAboveThreshold(threshold: Int) {
        try {
            val snap = withTimeoutOrNull(10_000L) {
                candidates.whereGreaterThanOrEqualTo("count", threshold).get().await()
            }
            if (snap == null) {
                AppLogger.w(TAG, "promoteAboveThreshold($threshold) timeout")
                return
            }
            AppLogger.d(TAG, "promoteAboveThreshold($threshold): ${snap.size()} kandidat")
            snap.documents.forEach { doc ->
                val candidate = doc.toObject(LearningCandidate::class.java) ?: return@forEach
                AppLogger.d(TAG, "→ promote [${candidate.type}] \"${candidate.itemName}\" count=${candidate.count}")
                when (candidate.type) {
                    "new_item" -> promoteItem(candidate)
                    "new_alias" -> promoteAlias(candidate)
                }
                withTimeoutOrNull(5_000L) { doc.reference.delete().await() }
                AppLogger.d(TAG, "→ kandidat [${doc.id}] dihapus")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e(TAG, "promoteAboveThreshold FAILED", e)
        }
    }
}
