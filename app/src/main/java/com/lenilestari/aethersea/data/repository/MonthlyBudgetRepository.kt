package com.lenilestari.aethersea.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.lenilestari.aethersea.data.model.MonthlyBudget
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

class MonthlyBudgetRepository(private val userId: String) {
    private val db = FirebaseFirestore.getInstance()
    private val collection = db.collection("users").document(userId).collection("monthlyBudgets")

    suspend fun get(period: String): MonthlyBudget? = try {
        val snap = try {
            collection.document(period).get(Source.CACHE).await()
        } catch (_: Exception) {
            collection.document(period).get().await()
        }
        if (snap.exists()) snap.toObject(MonthlyBudget::class.java)?.copy(period = snap.id) else null
    } catch (_: Exception) { null }

    suspend fun getOrCreate(period: String): MonthlyBudget {
        return get(period) ?: MonthlyBudget(period = period).also {
            try { withTimeoutOrNull(8_000L) { collection.document(period).set(it).await() } } catch (_: Exception) {}
        }
    }

    suspend fun create(budget: MonthlyBudget): Result<Unit> = try {
        val r = withTimeoutOrNull(8_000L) { collection.document(budget.period).set(budget).await() }
        if (r == null) Result.failure(Exception("Timeout saat create ${budget.period}"))
        else Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    suspend fun update(budget: MonthlyBudget): Result<Unit> = try {
        val r = withTimeoutOrNull(8_000L) { collection.document(budget.period).set(budget).await() }
        if (r == null) Result.failure(Exception("Timeout saat update ${budget.period}"))
        else Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    suspend fun getOpenMonths(): List<MonthlyBudget> {
        val query = collection.whereEqualTo("isClosed", false)
        return try {
            query.get(Source.CACHE).await()
                .documents.mapNotNull { it.toObject(MonthlyBudget::class.java)?.copy(period = it.id) }
        } catch (_: Exception) {
            try {
                query.get().await()
                    .documents.mapNotNull { it.toObject(MonthlyBudget::class.java)?.copy(period = it.id) }
            } catch (_: Exception) { emptyList() }
        }
    }

    suspend fun getAll(): List<MonthlyBudget> {
        val query = collection.orderBy("period", Query.Direction.DESCENDING)
        return try {
            query.get(Source.CACHE).await()
                .documents.mapNotNull { it.toObject(MonthlyBudget::class.java)?.copy(period = it.id) }
        } catch (_: Exception) {
            try {
                query.get().await()
                    .documents.mapNotNull { it.toObject(MonthlyBudget::class.java)?.copy(period = it.id) }
            } catch (_: Exception) { emptyList() }
        }
    }
}
