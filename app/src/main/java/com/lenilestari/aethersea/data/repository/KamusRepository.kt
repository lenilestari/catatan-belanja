package com.lenilestari.aethersea.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.lenilestari.aethersea.data.model.KamusItem
import kotlinx.coroutines.tasks.await

class KamusRepository {
    private val collection = FirebaseFirestore.getInstance().collection("kamus")

    // Exact match by name or alias — used after voice recognition
    suspend fun findExact(query: String): KamusItem? {
        val q = query.trim().lowercase()
        return try {
            // Try by name first
            val byName = try {
                collection.whereEqualTo("name", q).get(Source.CACHE).await()
            } catch (_: Exception) {
                collection.whereEqualTo("name", q).get().await()
            }
            if (!byName.isEmpty) {
                return byName.documents.first()
                    .toObject(KamusItem::class.java)?.copy(id = byName.documents.first().id)
            }
            // Try by alias
            val byAlias = try {
                collection.whereArrayContains("aliases", q).get(Source.CACHE).await()
            } catch (_: Exception) {
                collection.whereArrayContains("aliases", q).get().await()
            }
            if (!byAlias.isEmpty) {
                byAlias.documents.first()
                    .toObject(KamusItem::class.java)?.copy(id = byAlias.documents.first().id)
            } else null
        } catch (_: Exception) { null }
    }

    // Get all items for local fuzzy matching (Levenshtein)
    suspend fun getAllCached(): List<KamusItem> = try {
        val snap = try {
            collection.get(Source.CACHE).await()
        } catch (_: Exception) {
            collection.get().await()
        }
        snap.documents.mapNotNull { it.toObject(KamusItem::class.java)?.copy(id = it.id) }
    } catch (_: Exception) { emptyList() }

    // Upload a batch of items (used by migration only)
    suspend fun uploadBatch(items: List<KamusItem>): Result<Unit> = try {
        val db = FirebaseFirestore.getInstance()
        items.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { item ->
                val docId = item.name.replace(" ", "_").replace("/", "_")
                batch.set(collection.document(docId), item.copy(id = docId))
            }
            batch.commit().await() // await for migration (one-time, ok to be slow)
        }
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }
}
