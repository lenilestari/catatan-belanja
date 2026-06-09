package com.lenilestari.aethersea.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.lenilestari.aethersea.data.model.MonthlyBudget
import com.lenilestari.aethersea.util.AppLogger
import com.lenilestari.aethersea.util.FirestoreInstance
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

class MonthlyBudgetRepository(private val userId: String) {
    private val db = FirestoreInstance.db
    private val collection = db.collection("users").document(userId).collection("monthlyBudgets")
    private companion object { const val TAG = "MonthlyBudgetRepo" }

    suspend fun get(period: String): MonthlyBudget? = try {
        val snap = try {
            collection.document(period).get(Source.CACHE).await()
                .also { AppLogger.d(TAG, "get($period) cache hit exists=${it.exists()}") }
        } catch (_: Exception) {
            AppLogger.w(TAG, "get($period) cache miss → server")
            collection.document(period).get().await()
        }
        if (snap.exists()) snap.toObject(MonthlyBudget::class.java)?.copy(period = snap.id) else null
    } catch (e: Exception) { AppLogger.e(TAG, "get($period) FAILED", e); null }

    suspend fun getOrCreate(period: String): MonthlyBudget {
        return get(period) ?: MonthlyBudget(period = period).also { budget ->
            AppLogger.d(TAG, "getOrCreate($period) → creating new")
            try {
                val now = Timestamp.now()
                withTimeoutOrNull(8_000L) {
                    collection.document(period).set(budget.copy(updatedAt = now)).await()
                }
                AppLogger.d(TAG, "getOrCreate($period) → created")
            } catch (e: Exception) {
                AppLogger.e(TAG, "getOrCreate($period) create FAILED", e)
            }
        }
    }

    suspend fun create(budget: MonthlyBudget): Result<Unit> = try {
        AppLogger.d(TAG, "create(${budget.period})")
        val now = Timestamp.now()
        val serverAck = withTimeoutOrNull(8_000L) {
            collection.document(budget.period).set(budget.copy(updatedAt = now)).await()
        }
        if (serverAck == null) AppLogger.w(TAG, "create(${budget.period}) → timeout (offline), cached locally")
        else AppLogger.d(TAG, "create(${budget.period}) → server ACK")
        Result.success(Unit)
    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
    catch (e: Exception) { AppLogger.e(TAG, "create FAILED", e); Result.failure(e) }

    suspend fun update(budget: MonthlyBudget): Result<Unit> = try {
        AppLogger.d(TAG, "update(${budget.period}) spending=${budget.totalSpending} left=${budget.leftAmount}")
        val now = Timestamp.now()
        val serverAck = withTimeoutOrNull(8_000L) {
            collection.document(budget.period).set(budget.copy(updatedAt = now)).await()
        }
        if (serverAck == null) AppLogger.w(TAG, "update(${budget.period}) → timeout (offline), cached locally")
        else AppLogger.d(TAG, "update(${budget.period}) → server ACK")
        Result.success(Unit)
    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
    catch (e: Exception) { AppLogger.e(TAG, "update FAILED", e); Result.failure(e) }

    suspend fun getOpenMonths(): List<MonthlyBudget> {
        val query = collection.whereEqualTo("isClosed", false)
        return try {
            val result = query.get(Source.CACHE).await()
                .documents.mapNotNull { it.toObject(MonthlyBudget::class.java)?.copy(period = it.id) }
            AppLogger.d(TAG, "getOpenMonths cache → ${result.size} months")
            result
        } catch (_: Exception) {
            AppLogger.w(TAG, "getOpenMonths cache miss → server")
            try {
                val result = query.get().await()
                    .documents.mapNotNull { it.toObject(MonthlyBudget::class.java)?.copy(period = it.id) }
                AppLogger.d(TAG, "getOpenMonths server → ${result.size} months")
                result
            } catch (e: Exception) { AppLogger.e(TAG, "getOpenMonths FAILED", e); emptyList() }
        }
    }

    suspend fun getAll(): List<MonthlyBudget> {
        val query = collection.orderBy("period", Query.Direction.DESCENDING)
        return try {
            val result = query.get(Source.CACHE).await()
                .documents.mapNotNull { it.toObject(MonthlyBudget::class.java)?.copy(period = it.id) }
            AppLogger.d(TAG, "getAll cache → ${result.size} months")
            result
        } catch (_: Exception) {
            AppLogger.w(TAG, "getAll cache miss → server")
            try {
                val result = query.get().await()
                    .documents.mapNotNull { it.toObject(MonthlyBudget::class.java)?.copy(period = it.id) }
                AppLogger.d(TAG, "getAll server → ${result.size} months")
                result
            } catch (e: Exception) { AppLogger.e(TAG, "getAll FAILED", e); emptyList() }
        }
    }
}
