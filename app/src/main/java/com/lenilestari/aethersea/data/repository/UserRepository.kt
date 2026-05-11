package com.lenilestari.aethersea.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.lenilestari.aethersea.data.model.User
import com.lenilestari.aethersea.util.AppLogger
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

class UserRepository(private val userId: String) {
    private val db = FirebaseFirestore.getInstance()
    private val userDoc = db.collection("users").document(userId)
    private companion object { const val TAG = "UserRepo" }

    suspend fun getProfile(): User? = try {
        val snap = try {
            userDoc.get(Source.CACHE).await().also { AppLogger.d(TAG, "getProfile cache hit") }
        } catch (_: Exception) {
            AppLogger.w(TAG, "getProfile cache miss → server")
            userDoc.get().await()
        }
        if (snap.exists()) snap.toObject(User::class.java)?.copy(uid = snap.id) else null
    } catch (e: Exception) { AppLogger.e(TAG, "getProfile FAILED", e); null }

    suspend fun createOrUpdateProfile(user: User): Result<Unit> = try {
        AppLogger.d(TAG, "createOrUpdateProfile uid=${user.uid}")
        val serverAck = withTimeoutOrNull(8_000L) { userDoc.set(user).await() }
        if (serverAck == null) AppLogger.w(TAG, "createOrUpdateProfile → timeout (offline), cached locally")
        else AppLogger.d(TAG, "createOrUpdateProfile → server ACK")
        Result.success(Unit)
    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
    catch (e: Exception) { AppLogger.e(TAG, "createOrUpdateProfile FAILED", e); Result.failure(e) }

    suspend fun updateLastResetAt(timestamp: Timestamp): Result<Unit> = try {
        AppLogger.d(TAG, "updateLastResetAt ts=$timestamp")
        val serverAck = withTimeoutOrNull(8_000L) { userDoc.update("lastResetAt", timestamp).await() }
        if (serverAck == null) AppLogger.w(TAG, "updateLastResetAt → timeout (offline), cached locally")
        else AppLogger.d(TAG, "updateLastResetAt → server ACK")
        Result.success(Unit)
    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
    catch (e: Exception) { AppLogger.e(TAG, "updateLastResetAt FAILED", e); Result.failure(e) }

    suspend fun updateDisplayName(name: String): Result<Unit> = try {
        AppLogger.d(TAG, "updateDisplayName name=$name")
        val serverAck = withTimeoutOrNull(8_000L) { userDoc.update("displayName", name).await() }
        if (serverAck == null) AppLogger.w(TAG, "updateDisplayName → timeout (offline), cached locally")
        else AppLogger.d(TAG, "updateDisplayName → server ACK")
        Result.success(Unit)
    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
    catch (e: Exception) { AppLogger.e(TAG, "updateDisplayName FAILED", e); Result.failure(e) }

    suspend fun updatePhotoUrl(url: String): Result<Unit> = try {
        AppLogger.d(TAG, "updatePhotoUrl url=${url.take(60)}")
        val serverAck = withTimeoutOrNull(8_000L) { userDoc.update("photoUrl", url).await() }
        if (serverAck == null) AppLogger.w(TAG, "updatePhotoUrl → timeout (offline), cached locally")
        else AppLogger.d(TAG, "updatePhotoUrl → server ACK")
        Result.success(Unit)
    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
    catch (e: Exception) { AppLogger.e(TAG, "updatePhotoUrl FAILED", e); Result.failure(e) }
}
