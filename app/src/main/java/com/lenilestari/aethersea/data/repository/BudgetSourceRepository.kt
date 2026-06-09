package com.lenilestari.aethersea.data.repository

import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.lenilestari.aethersea.data.model.BudgetSource
import com.lenilestari.aethersea.util.AppLogger
import com.lenilestari.aethersea.util.FirestoreInstance
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

class BudgetSourceRepository(private val userId: String) {
    private val db = FirestoreInstance.db
    private val collection = db.collection("users").document(userId).collection("budgetSources")
    private companion object { const val TAG = "BudgetSourceRepo" }

    suspend fun getByPeriod(period: String): List<BudgetSource> {
        AppLogger.d(TAG, "getByPeriod period=$period")
        val query = collection.whereEqualTo("period", period)
            .orderBy("receivedDate", Query.Direction.ASCENDING)
        return try {
            val result = query.get(Source.CACHE).await()
                .documents.mapNotNull { it.toObject(BudgetSource::class.java)?.copy(id = it.id) }
            AppLogger.d(TAG, "getByPeriod cache hit → ${result.size} items")
            result
        } catch (_: Exception) {
            AppLogger.w(TAG, "getByPeriod cache miss → fetching from server")
            try {
                val result = query.get().await()
                    .documents.mapNotNull { it.toObject(BudgetSource::class.java)?.copy(id = it.id) }
                AppLogger.d(TAG, "getByPeriod server → ${result.size} items")
                result
            } catch (e: Exception) { AppLogger.e(TAG, "getByPeriod server FAILED", e); emptyList() }
        }
    }

    suspend fun getAll(): List<BudgetSource> {
        AppLogger.d(TAG, "getAll")
        val query = collection.orderBy("receivedDate", Query.Direction.DESCENDING)
        return try {
            val result = query.get(Source.CACHE).await()
                .documents.mapNotNull { it.toObject(BudgetSource::class.java)?.copy(id = it.id) }
            AppLogger.d(TAG, "getAll cache hit → ${result.size} items")
            result
        } catch (_: Exception) {
            AppLogger.w(TAG, "getAll cache miss → fetching from server")
            try {
                val result = query.get().await()
                    .documents.mapNotNull { it.toObject(BudgetSource::class.java)?.copy(id = it.id) }
                AppLogger.d(TAG, "getAll server → ${result.size} items")
                result
            } catch (e: Exception) { AppLogger.e(TAG, "getAll server FAILED", e); emptyList() }
        }
    }

    suspend fun add(source: BudgetSource): Result<String> = try {
        val ref = collection.document()
        val id = ref.id
        val now = com.google.firebase.Timestamp.now()
        AppLogger.d(TAG, "add → docId=$id name=${source.name} period=${source.period}")
        val serverAck = withTimeoutOrNull(8_000L) {
            ref.set(source.copy(id = id, createdAt = now, updatedAt = now)).await()
        }
        if (serverAck == null) AppLogger.w(TAG, "add → timeout (offline), cached locally id=$id")
        else AppLogger.d(TAG, "add → server ACK id=$id")
        Result.success(id)
    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
    catch (e: Exception) { AppLogger.e(TAG, "add FAILED", e); Result.failure(e) }

    suspend fun update(source: BudgetSource): Result<Unit> = try {
        AppLogger.d(TAG, "update → id=${source.id} name=${source.name}")
        val serverAck = withTimeoutOrNull(8_000L) {
            collection.document(source.id).set(source.copy(updatedAt = com.google.firebase.Timestamp.now())).await()
        }
        if (serverAck == null) AppLogger.w(TAG, "update → timeout (offline), cached locally")
        else AppLogger.d(TAG, "update → server ACK id=${source.id}")
        Result.success(Unit)
    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
    catch (e: Exception) { AppLogger.e(TAG, "update FAILED", e); Result.failure(e) }

    suspend fun delete(id: String): Result<Unit> = try {
        AppLogger.d(TAG, "delete → id=$id")
        val serverAck = withTimeoutOrNull(8_000L) { collection.document(id).delete().await() }
        if (serverAck == null) AppLogger.w(TAG, "delete → timeout (offline), cached locally")
        else AppLogger.d(TAG, "delete → server ACK id=$id")
        Result.success(Unit)
    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
    catch (e: Exception) { AppLogger.e(TAG, "delete FAILED", e); Result.failure(e) }
}
