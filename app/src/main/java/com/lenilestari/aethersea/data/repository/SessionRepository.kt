package com.lenilestari.aethersea.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.lenilestari.aethersea.data.model.ShoppingSession
import com.lenilestari.aethersea.util.AppLogger
import com.lenilestari.aethersea.util.FirestoreInstance
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

class SessionRepository(private val userId: String) {
    private val db = FirestoreInstance.db
    private val collection = db.collection("users").document(userId).collection("sessions")
    private companion object { const val TAG = "SessionRepo" }

    suspend fun addSession(session: ShoppingSession): Result<String> = try {
        val ref = collection.document()
        val id = ref.id
        val now = com.google.firebase.Timestamp.now()
        AppLogger.d(TAG, "addSession → docId=$id period=${session.period} date=${session.date}")
        val serverAck = withTimeoutOrNull(10_000L) {
            ref.set(session.copy(id = id, createdAt = now, updatedAt = now)).await()
        }
        if (serverAck == null) AppLogger.w(TAG, "addSession → timeout (offline), cached locally id=$id")
        else AppLogger.d(TAG, "addSession → server ACK id=$id")
        Result.success(id)
    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
    catch (e: Exception) { AppLogger.e(TAG, "addSession FAILED", e); Result.failure(e) }

    suspend fun updateSession(session: ShoppingSession): Result<Unit> = try {
        AppLogger.d(TAG, "updateSession → id=${session.id}")
        val serverAck = withTimeoutOrNull(8_000L) {
            collection.document(session.id).set(session.copy(updatedAt = com.google.firebase.Timestamp.now())).await()
        }
        if (serverAck == null) AppLogger.w(TAG, "updateSession → timeout (offline), cached locally")
        else AppLogger.d(TAG, "updateSession → server ACK id=${session.id}")
        Result.success(Unit)
    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
    catch (e: Exception) { AppLogger.e(TAG, "updateSession FAILED", e); Result.failure(e) }

    suspend fun deleteSession(id: String): Result<Unit> = try {
        AppLogger.d(TAG, "deleteSession → id=$id")
        val serverAck = withTimeoutOrNull(8_000L) { collection.document(id).delete().await() }
        if (serverAck == null) AppLogger.w(TAG, "deleteSession → timeout (offline), cached locally")
        else AppLogger.d(TAG, "deleteSession → server ACK id=$id")
        Result.success(Unit)
    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
    catch (e: Exception) { AppLogger.e(TAG, "deleteSession FAILED", e); Result.failure(e) }

    suspend fun deleteAllSessions(): Result<Unit> = try {
        val docs = try {
            collection.get(Source.CACHE).await().documents
        } catch (_: Exception) {
            collection.get().await().documents
        }
        docs.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.delete(it.reference) }
            withTimeoutOrNull(15_000L) { batch.commit().await() }
        }
        Result.success(Unit)
    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
    catch (e: Exception) { Result.failure(e) }

    suspend fun getSession(id: String): ShoppingSession? = try {
        val snap = try {
            collection.document(id).get(Source.CACHE).await()
        } catch (_: Exception) {
            collection.document(id).get().await()
        }
        snap.toObject(ShoppingSession::class.java)?.copy(id = snap.id)
    } catch (_: Exception) { null }

    // limit = null → unlimited (untuk export). limit = angka → untuk UI display.
    suspend fun getAllSessions(limit: Long? = null): List<ShoppingSession> {
        AppLogger.d(TAG, "getAllSessions limit=$limit")
        var query = collection.orderBy("date", Query.Direction.DESCENDING)
        if (limit != null) query = query.limit(limit)
        return try {
            val result = query.get(Source.CACHE).await()
                .documents.mapNotNull { it.toObject(ShoppingSession::class.java)?.copy(id = it.id) }
            AppLogger.d(TAG, "getAllSessions cache → ${result.size} items")
            result
        } catch (_: Exception) {
            AppLogger.w(TAG, "getAllSessions cache miss → server")
            try {
                val result = query.get().await()
                    .documents.mapNotNull { it.toObject(ShoppingSession::class.java)?.copy(id = it.id) }
                AppLogger.d(TAG, "getAllSessions server → ${result.size} items")
                result
            } catch (e: Exception) { AppLogger.e(TAG, "getAllSessions FAILED", e); emptyList() }
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

    // Pakai composite index: period ASC + date DESC — lebih efisien dari range timestamp
    suspend fun getSessionsByPeriod(period: String): List<ShoppingSession> {
        AppLogger.d(TAG, "getSessionsByPeriod period=$period")
        val query = collection
            .whereEqualTo("period", period)
            .orderBy("date", Query.Direction.DESCENDING)
        return try {
            val result = query.get(Source.CACHE).await()
                .documents.mapNotNull { it.toObject(ShoppingSession::class.java)?.copy(id = it.id) }
            AppLogger.d(TAG, "getSessionsByPeriod cache → ${result.size} items")
            result
        } catch (_: Exception) {
            AppLogger.w(TAG, "getSessionsByPeriod cache miss → server")
            try {
                val result = query.get().await()
                    .documents.mapNotNull { it.toObject(ShoppingSession::class.java)?.copy(id = it.id) }
                AppLogger.d(TAG, "getSessionsByPeriod server → ${result.size} items")
                result
            } catch (e: Exception) { AppLogger.e(TAG, "getSessionsByPeriod FAILED", e); emptyList() }
        }
    }
}
