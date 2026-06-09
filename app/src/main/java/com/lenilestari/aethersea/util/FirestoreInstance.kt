package com.lenilestari.aethersea.util

import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings

/**
 * Single source of truth untuk Firestore instance.
 * Database ID "aethersea" — BUKAN "(default)".
 * Semua repository wajib pakai FirestoreInstance.db, bukan FirebaseFirestore.getInstance().
 */
object FirestoreInstance {

    const val DATABASE_ID = "aethersea"

    val db: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance(FirebaseApp.getInstance(), DATABASE_ID).also { fs ->
            fs.firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(
                    PersistentCacheSettings.newBuilder()
                        .setSizeBytes(100L * 1024 * 1024)
                        .build()
                )
                .build()
        }
    }
}
