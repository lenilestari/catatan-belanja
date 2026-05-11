package com.lenilestari.aethersea

import android.app.Application
import coil.Coil
import coil.ImageLoader
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.lenilestari.aethersea.util.KamusMigrationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CatatanBelanjaApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        FirebaseFirestore.getInstance().firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(
                PersistentCacheSettings.newBuilder()
                    .setSizeBytes(100L * 1024 * 1024) // 100 MB cache
                    .build()
            )
            .build()

        Coil.setImageLoader(ImageLoader.Builder(this).crossfade(true).build())

        // Upload kamus ke Firestore sekali — background, tidak blocking startup
        appScope.launch {
            KamusMigrationManager(this@CatatanBelanjaApp).migrateIfNeeded()
        }
    }
}
