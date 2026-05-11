package com.lenilestari.aethersea.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.lenilestari.aethersea.data.model.Wishlist
import com.lenilestari.aethersea.util.AppLogger
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

class WishlistRepository(private val userId: String) {
    private val db = FirebaseFirestore.getInstance()
    private val collection = db.collection("users").document(userId).collection("wishlists")
    private companion object { const val TAG = "WishlistRepo" }

    suspend fun getAll(): List<Wishlist> {
        AppLogger.d(TAG, "getAll")
        val query = collection.orderBy("createdAt", Query.Direction.DESCENDING)
        return try {
            val result = query.get(Source.CACHE).await()
                .documents.mapNotNull { it.toObject(Wishlist::class.java)?.copy(id = it.id) }
            AppLogger.d(TAG, "getAll cache hit → ${result.size} items")
            result
        } catch (_: Exception) {
            AppLogger.w(TAG, "getAll cache miss → fetching from server")
            try {
                val result = query.get().await()
                    .documents.mapNotNull { it.toObject(Wishlist::class.java)?.copy(id = it.id) }
                AppLogger.d(TAG, "getAll server → ${result.size} items")
                result
            } catch (e: Exception) { AppLogger.e(TAG, "getAll server FAILED", e); emptyList() }
        }
    }

    suspend fun add(wishlist: Wishlist): Result<String> = try {
        val ref = collection.document()
        val id = ref.id
        val now = com.google.firebase.Timestamp.now()
        AppLogger.d(TAG, "add → docId=$id name=${wishlist.name}")
        val serverAck = withTimeoutOrNull(8_000L) {
            ref.set(wishlist.copy(id = id, createdAt = now, updatedAt = now)).await()
        }
        if (serverAck == null) AppLogger.w(TAG, "add → timeout (offline), cached locally id=$id")
        else AppLogger.d(TAG, "add → server ACK id=$id")
        Result.success(id)
    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
    catch (e: Exception) { AppLogger.e(TAG, "add FAILED", e); Result.failure(e) }

    suspend fun update(wishlist: Wishlist): Result<Unit> = try {
        AppLogger.d(TAG, "update → id=${wishlist.id} name=${wishlist.name}")
        val serverAck = withTimeoutOrNull(8_000L) {
            collection.document(wishlist.id).set(wishlist.copy(updatedAt = com.google.firebase.Timestamp.now())).await()
        }
        if (serverAck == null) AppLogger.w(TAG, "update → timeout (offline), cached locally")
        else AppLogger.d(TAG, "update → server ACK id=${wishlist.id}")
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

    suspend fun getById(id: String): Wishlist? = try {
        val snap = try {
            collection.document(id).get(Source.CACHE).await()
        } catch (_: Exception) {
            collection.document(id).get().await()
        }
        snap.toObject(Wishlist::class.java)?.copy(id = snap.id)
    } catch (_: Exception) { null }
}
