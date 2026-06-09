package com.lenilestari.aethersea

import android.app.Application
import android.os.Build
import coil.Coil
import coil.ImageLoader
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.lenilestari.aethersea.util.AppLogger
import com.lenilestari.aethersea.util.FirestoreInstance
import com.lenilestari.aethersea.util.KamusSeeder
import com.lenilestari.aethersea.util.KnowledgeLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class CatatanBelanjaApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // Inisialisasi Firestore ke database "aethersea" (BUKAN default).
        // Lazy init di FirestoreInstance.db juga set PersistentCacheSettings 100MB.
        FirestoreInstance.db

        Coil.setImageLoader(ImageLoader.Builder(this).crossfade(true).build())

        // Debug ping — tulis ke debug_test/ping, cek di Firebase Console > aethersea
        appScope.launch { debugPing() }

        // Load knowledge ke in-memory cache (Firestore → JSON fallback) — tidak blocking
        appScope.launch { KnowledgeLoader(this@CatatanBelanjaApp).loadIfNeeded() }

        // Seed 4 koleksi Firestore dari JSON — version-gated, hanya jika server ACK sukses
        appScope.launch { KamusSeeder(this@CatatanBelanjaApp).seedIfNeeded() }
    }

    private suspend fun debugPing() {
        val tag = "DebugPing"
        try {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "unauthenticated"
            AppLogger.d(tag, "Ping → db=${FirestoreInstance.DATABASE_ID}/debug_test/ping uid=$uid ...")

            val pingData = mapOf(
                "message"    to "ping dari Android app",
                "databaseId" to FirestoreInstance.DATABASE_ID,
                "projectId"  to "aethersea-562a6",
                "authUid"    to uid,
                "appVersion" to "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                "buildType"  to BuildConfig.BUILD_TYPE,
                "device"     to "${Build.MANUFACTURER} ${Build.MODEL} (API ${Build.VERSION.SDK_INT})",
                "emulator"   to isEmulator(),
                "platform"   to "android",
                "ts"         to Timestamp.now()
            )

            FirestoreInstance.db
                .collection("debug_test")
                .document("ping")
                .set(pingData)
                .await()

            AppLogger.d(tag, "✓ ping SUKSES → cek Firebase Console > aethersea > debug_test > ping")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e(tag, "✗ ping GAGAL — ${e.message}", e)
        }
    }

    private fun isEmulator(): Boolean =
        Build.FINGERPRINT.startsWith("generic") ||
        Build.FINGERPRINT.startsWith("unknown") ||
        Build.MODEL.contains("google_sdk") ||
        Build.MODEL.contains("Emulator") ||
        Build.MODEL.contains("Android SDK") ||
        Build.MANUFACTURER.contains("Genymotion") ||
        Build.BRAND.startsWith("generic")
}
