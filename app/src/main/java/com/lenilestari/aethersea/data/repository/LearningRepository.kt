package com.lenilestari.aethersea.data.repository

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.lenilestari.aethersea.data.model.LearningCandidate
import com.lenilestari.aethersea.data.model.NewAliasSuggestion
import com.lenilestari.aethersea.data.model.NewItemSuggestion
import com.lenilestari.aethersea.data.model.NewUnitSuggestion
import kotlinx.coroutines.tasks.await

private const val TAG = "LearningRepo"

class LearningRepository {
    private val db = FirebaseFirestore.getInstance()
    private val candidates = db.collection("learning_candidates")
    private val kamus = db.collection("kamus")

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
            val ref  = candidates.document(docId)
            val snap = ref.get().await()
            if (snap.exists()) {
                val currentCount = snap.getLong("count") ?: 0
                ref.update(
                    mapOf(
                        "count"      to FieldValue.increment(1),
                        "lastSeenMs" to System.currentTimeMillis()
                    )
                ).await()
                Log.d(TAG, "  upsert INCREMENT [$docId] count: $currentCount → ${currentCount + 1}")
            } else {
                val candidate = buildNew()
                ref.set(candidate).await()
                Log.d(TAG, "  upsert NEW [$docId] type=${candidate.type} itemName=${candidate.itemName}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "  upsert GAGAL [$docId]: ${e.message}")
        }
    }

    private suspend fun promoteItem(c: LearningCandidate) {
        if (c.itemName.isBlank()) return
        try {
            val docId = c.itemName.replace(" ", "_")
            val data = mapOf(
                "id"          to docId,
                "name"        to c.itemName,
                "category"    to c.category,
                "aliases"     to emptyList<String>(),
                "commonUnits" to c.commonUnits
            )
            kamus.document(docId).set(data).await()
            Log.d(TAG, "  PROMOTE item_new → kamus: \"${c.itemName}\" [${c.category}]")
        } catch (e: Exception) {
            Log.e(TAG, "  PROMOTE item GAGAL \"${c.itemName}\": ${e.message}")
        }
    }

    private suspend fun promoteAlias(c: LearningCandidate) {
        if (c.itemName.isBlank() || c.alias.isBlank()) return
        try {
            val snap = kamus.whereEqualTo("name", c.itemName).get().await()
            if (!snap.isEmpty) {
                snap.documents.first().reference
                    .update("aliases", FieldValue.arrayUnion(c.alias))
                    .await()
                Log.d(TAG, "  PROMOTE alias → kamus: \"${c.alias}\" → \"${c.itemName}\"")
            } else {
                Log.w(TAG, "  PROMOTE alias SKIP: item \"${c.itemName}\" tidak ada di kamus")
            }
        } catch (e: Exception) {
            Log.e(TAG, "  PROMOTE alias GAGAL \"${c.alias}\": ${e.message}")
        }
    }

    suspend fun promoteAboveThreshold(threshold: Int) {
        try {
            val snap = candidates.whereGreaterThanOrEqualTo("count", threshold).get().await()
            Log.d(TAG, "  promoteAboveThreshold($threshold): ${snap.size()} kandidat siap dipromosi")
            snap.documents.forEach { doc ->
                val candidate = doc.toObject(LearningCandidate::class.java) ?: return@forEach
                Log.d(TAG, "    → promote [${candidate.type}] \"${candidate.itemName}\" count=${candidate.count}")
                when (candidate.type) {
                    "new_item"  -> promoteItem(candidate)
                    "new_alias" -> promoteAlias(candidate)
                }
                doc.reference.delete().await()
                Log.d(TAG, "    → hapus kandidat [${doc.id}]")
            }
        } catch (e: Exception) {
            Log.e(TAG, "  promoteAboveThreshold GAGAL: ${e.message}")
        }
    }
}
