package com.lenilestari.aethersea.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.lenilestari.aethersea.data.model.Wishlist
import kotlinx.coroutines.tasks.await

class WishlistRepository(private val userId: String) {
    private val db = FirebaseFirestore.getInstance()
    private val collection = db.collection("users").document(userId).collection("wishlists")

    suspend fun getAll(): List<Wishlist> {
        android.util.Log.d("DBG_AETHER", "   wishlist.getAll: cache...")
        val query = collection.orderBy("createdAt", Query.Direction.DESCENDING)
        return try {
            val r = query.get(Source.CACHE).await()
                .documents.mapNotNull { it.toObject(Wishlist::class.java)?.copy(id = it.id) }
            android.util.Log.d("DBG_AETHER", "   wishlist.getAll: cache → ${r.size} item")
            r
        } catch (e1: Exception) {
            android.util.Log.w("DBG_AETHER", "   wishlist.getAll: cache miss (${e1.message}) → server...")
            try {
                val r = query.get().await()
                    .documents.mapNotNull { it.toObject(Wishlist::class.java)?.copy(id = it.id) }
                android.util.Log.d("DBG_AETHER", "   wishlist.getAll: server → ${r.size} item")
                r
            } catch (e2: Exception) {
                android.util.Log.e("DBG_AETHER", "   wishlist.getAll: server FAIL → ${e2.message}")
                emptyList()
            }
        }
    }

    suspend fun add(wishlist: Wishlist): Result<String> = try {
        val ref = collection.document()
        ref.set(wishlist.copy(id = ref.id)).await()
        Result.success(ref.id)
    } catch (e: Exception) { Result.failure(e) }

    suspend fun update(wishlist: Wishlist): Result<Unit> = try {
        collection.document(wishlist.id).set(wishlist).await()
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    suspend fun delete(id: String): Result<Unit> = try {
        collection.document(id).delete().await()
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    suspend fun getById(id: String): Wishlist? = try {
        val snap = try {
            collection.document(id).get(Source.CACHE).await()
        } catch (_: Exception) {
            collection.document(id).get().await()
        }
        snap.toObject(Wishlist::class.java)?.copy(id = snap.id)
    } catch (_: Exception) { null }
}
