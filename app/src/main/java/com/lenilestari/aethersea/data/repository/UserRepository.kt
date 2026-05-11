package com.lenilestari.aethersea.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.lenilestari.aethersea.data.model.User
import kotlinx.coroutines.tasks.await

class UserRepository(private val userId: String) {
    private val db = FirebaseFirestore.getInstance()
    private val userDoc = db.collection("users").document(userId)

    suspend fun getProfile(): User? = try {
        val snap = try {
            userDoc.get(Source.CACHE).await()
        } catch (_: Exception) {
            userDoc.get().await()
        }
        if (snap.exists()) snap.toObject(User::class.java)?.copy(uid = snap.id) else null
    } catch (_: Exception) { null }

    suspend fun createOrUpdateProfile(user: User): Result<Unit> = try {
        userDoc.set(user).await()
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    suspend fun updateLastResetAt(timestamp: Timestamp): Result<Unit> = try {
        userDoc.update("lastResetAt", timestamp).await()
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    suspend fun updateDisplayName(name: String): Result<Unit> = try {
        userDoc.update("displayName", name).await()
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    suspend fun updatePhotoUrl(url: String): Result<Unit> = try {
        userDoc.update("photoUrl", url).await()
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }
}
