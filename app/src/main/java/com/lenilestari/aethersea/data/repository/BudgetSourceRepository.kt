package com.lenilestari.aethersea.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.lenilestari.aethersea.data.model.BudgetSource
import kotlinx.coroutines.tasks.await

class BudgetSourceRepository(private val userId: String) {
    private val db = FirebaseFirestore.getInstance()
    private val collection = db.collection("users").document(userId).collection("budgetSources")

    suspend fun getByPeriod(period: String): List<BudgetSource> {
        val query = collection.whereEqualTo("period", period)
            .orderBy("receivedDate", Query.Direction.ASCENDING)
        return try {
            query.get(Source.CACHE).await()
                .documents.mapNotNull { it.toObject(BudgetSource::class.java)?.copy(id = it.id) }
        } catch (_: Exception) {
            try {
                query.get().await()
                    .documents.mapNotNull { it.toObject(BudgetSource::class.java)?.copy(id = it.id) }
            } catch (_: Exception) { emptyList() }
        }
    }

    suspend fun getAll(): List<BudgetSource> {
        val query = collection.orderBy("receivedDate", Query.Direction.DESCENDING)
        return try {
            query.get(Source.CACHE).await()
                .documents.mapNotNull { it.toObject(BudgetSource::class.java)?.copy(id = it.id) }
        } catch (_: Exception) {
            try {
                query.get().await()
                    .documents.mapNotNull { it.toObject(BudgetSource::class.java)?.copy(id = it.id) }
            } catch (_: Exception) { emptyList() }
        }
    }

    suspend fun add(source: BudgetSource): Result<String> = try {
        val ref = collection.document()
        ref.set(source.copy(id = ref.id)).await()
        Result.success(ref.id)
    } catch (e: Exception) { Result.failure(e) }

    suspend fun update(source: BudgetSource): Result<Unit> = try {
        collection.document(source.id).set(source).await()
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    suspend fun delete(id: String): Result<Unit> = try {
        collection.document(id).delete().await()
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }
}
