package com.lenilestari.aethersea.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.lenilestari.aethersea.data.model.ShoppingSession
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

class SessionRepository(private val userId: String) {
    private val db = FirebaseFirestore.getInstance()
    private val collection = db.collection("users").document(userId).collection("sessions")

    suspend fun addSession(session: ShoppingSession): Result<String> = try {
        val ref = collection.document()
        val id = ref.id
        // Local write terjadi langsung; timeout hanya batasi tunggu server ACK
        // Jika timeout, data sudah ada di cache lokal dan akan sync otomatis
        withTimeoutOrNull(10_000L) { ref.set(session.copy(id = id)).await() }
        Result.success(id)
    } catch (e: Exception) { Result.failure(e) }

    suspend fun updateSession(session: ShoppingSession): Result<Unit> = try {
        collection.document(session.id).set(session).await()
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    suspend fun deleteSession(id: String): Result<Unit> = try {
        collection.document(id).delete().await()
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    suspend fun deleteAllSessions(): Result<Unit> = try {
        val docs = try {
            collection.get(Source.CACHE).await().documents
        } catch (_: Exception) {
            collection.get().await().documents
        }
        // BUG-05: Firestore batch max 500 ops — chunked delete
        docs.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    suspend fun getSession(id: String): ShoppingSession? = try {
        val snap = try {
            collection.document(id).get(Source.CACHE).await()
        } catch (_: Exception) {
            collection.document(id).get().await()
        }
        snap.toObject(ShoppingSession::class.java)?.copy(id = snap.id)
    } catch (_: Exception) { null }

    suspend fun getAllSessions(): List<ShoppingSession> {
        val query = collection.orderBy("date", Query.Direction.DESCENDING)
        return try {
            query.get(Source.CACHE).await()
                .documents.mapNotNull { it.toObject(ShoppingSession::class.java)?.copy(id = it.id) }
        } catch (_: Exception) {
            try {
                query.get().await()
                    .documents.mapNotNull { it.toObject(ShoppingSession::class.java)?.copy(id = it.id) }
            } catch (_: Exception) { emptyList() }
        }
    }

    suspend fun getSessionsInRange(start: Timestamp, end: Timestamp): List<ShoppingSession> {
        val query = collection.whereGreaterThanOrEqualTo("date", start)
            .whereLessThanOrEqualTo("date", end)
        return try {
            query.get(Source.CACHE).await()
                .documents.mapNotNull { it.toObject(ShoppingSession::class.java)?.copy(id = it.id) }
        } catch (_: Exception) {
            try {
                query.get().await()
                    .documents.mapNotNull { it.toObject(ShoppingSession::class.java)?.copy(id = it.id) }
            } catch (_: Exception) { emptyList() }
        }
    }

    suspend fun getSessionsThisMonth(start: Timestamp): List<ShoppingSession> {
        val query = collection.whereGreaterThanOrEqualTo("date", start)
            .orderBy("date", Query.Direction.DESCENDING)
        return try {
            query.get(Source.CACHE).await()
                .documents.mapNotNull { it.toObject(ShoppingSession::class.java)?.copy(id = it.id) }
        } catch (_: Exception) {
            try {
                query.get().await()
                    .documents.mapNotNull { it.toObject(ShoppingSession::class.java)?.copy(id = it.id) }
            } catch (_: Exception) { emptyList() }
        }
    }
}
