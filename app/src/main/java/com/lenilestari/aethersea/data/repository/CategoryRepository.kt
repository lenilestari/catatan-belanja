package com.lenilestari.aethersea.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.lenilestari.aethersea.data.model.Category
import com.lenilestari.aethersea.util.Constants
import kotlinx.coroutines.tasks.await

class CategoryRepository(private val userId: String) {
    private val db = FirebaseFirestore.getInstance()
    private val collection = db.collection("users").document(userId).collection("categories")

    private val defaultMainCategories = listOf(
        Category(Constants.CAT_PRIBADI, "Catatan Pribadi", "👤", null, true, 0),
        Category(Constants.CAT_RUMAH, "Kebutuhan Rumah", "🏠", null, true, 1),
        Category(Constants.CAT_BISNIS, "Kebutuhan Bisnis", "💼", null, true, 2),
        Category(Constants.CAT_PENDIDIKAN, "Kebutuhan Pendidikan", "🎓", null, true, 3)
    )

    private val defaultSubCategories = listOf(
        Category("sub_makanan", "Bahan Makanan", "🍎", Constants.CAT_RUMAH, true, 0),
        Category("sub_kebersihan", "Kebersihan", "🧼", Constants.CAT_RUMAH, true, 1),
        Category("sub_peralatan", "Peralatan", "🔧", Constants.CAT_RUMAH, true, 2),
        Category("sub_bensin", "Bensin", "⛽", Constants.CAT_RUMAH, true, 3),
        Category("sub_listrik", "Listrik", "💡", Constants.CAT_RUMAH, true, 4),
        Category("sub_kesehatan", "Kesehatan", "💊", Constants.CAT_RUMAH, true, 5)
    )

    suspend fun seedDefaultsIfNeeded() {
        android.util.Log.d("DBG_AETHER", "   seedDefaults: read cache...")
        val existing = try {
            val r = collection.whereEqualTo("isDefault", true).get(Source.CACHE).await()
            android.util.Log.d("DBG_AETHER", "   seedDefaults: cache hit → ${r.size()} dok")
            r
        } catch (e1: Exception) {
            android.util.Log.w("DBG_AETHER", "   seedDefaults: cache miss (${e1.message}) → server...")
            try {
                val r = collection.whereEqualTo("isDefault", true).get().await()
                android.util.Log.d("DBG_AETHER", "   seedDefaults: server hit → ${r.size()} dok")
                r
            } catch (e2: Exception) {
                android.util.Log.e("DBG_AETHER", "   seedDefaults: server FAIL (${e2.message}) → skip")
                return
            }
        }
        if (existing.isEmpty) {
            android.util.Log.d("DBG_AETHER", "   seedDefaults: kosong → menulis default kategori...")
            try {
                val batch = db.batch()
                (defaultMainCategories + defaultSubCategories).forEach { cat ->
                    batch.set(collection.document(cat.id), cat)
                }
                batch.commit().await()
                android.util.Log.d("DBG_AETHER", "   seedDefaults: batch commit OK")
            } catch (e: Exception) {
                android.util.Log.e("DBG_AETHER", "   seedDefaults: batch commit FAIL → ${e.message}")
            }
        } else {
            android.util.Log.d("DBG_AETHER", "   seedDefaults: sudah ada data, skip")
        }
    }

    suspend fun getMainCategories(): List<Category> {
        android.util.Log.d("DBG_AETHER", "   getMainCategories: read cache...")
        val query = collection.whereEqualTo("parentId", null)
        return try {
            val r = query.get(Source.CACHE).await()
                .documents.mapNotNull { it.toObject(Category::class.java)?.copy(id = it.id) }
                .sortedBy { it.sortOrder }
            android.util.Log.d("DBG_AETHER", "   getMainCategories: cache → ${r.size} item")
            r
        } catch (e1: Exception) {
            android.util.Log.w("DBG_AETHER", "   getMainCategories: cache miss (${e1.message}) → server...")
            try {
                val r = query.get().await()
                    .documents.mapNotNull { it.toObject(Category::class.java)?.copy(id = it.id) }
                    .sortedBy { it.sortOrder }
                android.util.Log.d("DBG_AETHER", "   getMainCategories: server → ${r.size} item")
                r
            } catch (e2: Exception) {
                android.util.Log.e("DBG_AETHER", "   getMainCategories: server FAIL → ${e2.message}")
                emptyList()
            }
        }
    }

    suspend fun getSubCategories(parentId: String): List<Category> {
        val query = collection.whereEqualTo("parentId", parentId)
        return try {
            query.get(Source.CACHE).await()
                .documents.mapNotNull { it.toObject(Category::class.java)?.copy(id = it.id) }
                .sortedBy { it.sortOrder }
        } catch (_: Exception) {
            try {
                query.get().await()
                    .documents.mapNotNull { it.toObject(Category::class.java)?.copy(id = it.id) }
                    .sortedBy { it.sortOrder }
            } catch (_: Exception) { emptyList() }
        }
    }

    suspend fun addCategory(category: Category): Result<String> = try {
        val ref = if (category.id.isNotEmpty()) collection.document(category.id)
        else collection.document()
        ref.set(category.copy(id = ref.id)).await()
        Result.success(ref.id)
    } catch (e: Exception) { Result.failure(e) }

    suspend fun deleteCategory(id: String): Result<Unit> = try {
        collection.document(id).delete().await()
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    suspend fun hasSubCategories(parentId: String): Boolean {
        return try {
            val query = collection.whereEqualTo("parentId", parentId)
            val snap = try {
                query.get(Source.CACHE).await()
            } catch (_: Exception) {
                query.get().await()
            }
            !snap.isEmpty
        } catch (_: Exception) { false }
    }
}
