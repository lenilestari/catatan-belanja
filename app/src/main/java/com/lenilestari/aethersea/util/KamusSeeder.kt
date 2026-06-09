package com.lenilestari.aethersea.util

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.SetOptions
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.lenilestari.aethersea.BuildConfig
import com.lenilestari.aethersea.data.model.KamusItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Seeder produksi — seed 4 koleksi Firestore dari assets/kamus_items.json:
 *   /kamus              → item belanja (nama, alias, satuan, kategori)
 *   /unit_normalization → normalisasi satuan (kilo→kg, ltr→liter)
 *   /number_words       → kata angka (satu→1, setengah→0.5)
 *   /multipliers        → pengali (ribu→1000, juta→1_000_000)
 *
 * Semua write ke database "aethersea" via FirestoreInstance.db.
 * seeded_version HANYA di-set jika server ACK sukses — bukan timeout.
 * Timeout = device offline → seeder retry di launch berikutnya.
 */
class KamusSeeder(private val context: Context) {

    private val db    = FirestoreInstance.db
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private companion object {
        const val TAG = "KamusSeeder"
        // v3 = migrasi database default → aethersea; semua device re-seed ke database yang benar
        const val PREFS_NAME            = "kamus_seeder_v3"
        const val KEY_SEEDED_VERSION    = "seeded_version"
        const val BATCH_SIZE            = 400
        const val BATCH_TIMEOUT_MS      = 15_000L
        const val SMALL_BATCH_TIMEOUT_MS = 8_000L
        const val MAX_RETRY             = 3
        const val RETRY_BASE_DELAY_MS   = 2_000L
        const val RETRY_MAX_DELAY_MS    = 30_000L
    }

    suspend fun seedIfNeeded() {
        val uid          = FirebaseAuth.getInstance().currentUser?.uid ?: "unauthenticated"
        val jsonVersion  = readJsonVersion()
        val seededVersion = prefs.getString(KEY_SEEDED_VERSION, null)

        AppLogger.d(TAG, "══════════════════════════════════════════════")
        AppLogger.d(TAG, "KamusSeeder CHECK")
        AppLogger.d(TAG, "  databaseId    = ${FirestoreInstance.DATABASE_ID}")
        AppLogger.d(TAG, "  projectId     = aethersea-562a6")
        AppLogger.d(TAG, "  authUid       = $uid")
        AppLogger.d(TAG, "  jsonVersion   = $jsonVersion")
        AppLogger.d(TAG, "  seededVersion = $seededVersion")
        AppLogger.d(TAG, "  buildType     = ${BuildConfig.BUILD_TYPE}")
        AppLogger.d(TAG, "  emulator      = ${isEmulator()}")
        AppLogger.d(TAG, "══════════════════════════════════════════════")

        if (seededVersion == jsonVersion) {
            AppLogger.d(TAG, "Sudah up-to-date (v$jsonVersion) → skip")
            return
        }

        val json = readJson() ?: run {
            AppLogger.e(TAG, "Gagal baca kamus_items.json — seeding dibatalkan")
            return
        }

        val startMs  = System.currentTimeMillis()
        var batchOk  = 0
        var batchFail = 0

        fun track(label: String, ok: Boolean) {
            if (ok) { batchOk++; AppLogger.d(TAG, "  ✓ $label: OK") }
            else    { batchFail++; AppLogger.e(TAG, "  ✗ $label: GAGAL") }
        }

        track("unit_normalization", seedSmallCollection(json, "unit_normalization"))
        track("number_words",       seedSmallCollection(json, "number_words"))
        track("multipliers",        seedSmallCollection(json, "multipliers"))
        track("kamus",              seedKamus(json))

        val elapsed = System.currentTimeMillis() - startMs
        AppLogger.d(TAG, "══════════════════════════════════════════════")
        AppLogger.d(TAG, "KamusSeeder RESULT: ok=$batchOk fail=$batchFail elapsed=${elapsed}ms")

        if (batchFail == 0) {
            prefs.edit().putString(KEY_SEEDED_VERSION, jsonVersion).apply()
            AppLogger.d(TAG, "✓ seeded_version='$jsonVersion' disimpan → skip di launch berikutnya")
        } else {
            AppLogger.e(TAG, "✗ $batchFail collection GAGAL → seeded_version TIDAK disimpan → retry saat online")
        }
        AppLogger.d(TAG, "══════════════════════════════════════════════")
    }

    // ── Collection seeders ──────────────────────────────────────────────────────

    private suspend fun seedSmallCollection(root: JsonObject, collectionName: String): Boolean {
        val obj = root.getAsJsonObject(collectionName)
        if (obj == null) {
            AppLogger.w(TAG, "[$collectionName] tidak ada di JSON — skip")
            return true
        }

        val batch = db.batch()
        val now   = Timestamp.now()
        val col   = db.collection(collectionName)
        var count = 0

        obj.entrySet().forEach { (key, valueEl) ->
            val docId = key.trim().lowercase().replace(" ", "_")
            val data  = buildSmallCollectionData(collectionName, key.trim().lowercase(), valueEl.asString, now)
            batch.set(col.document(docId), data, SetOptions.merge())
            count++
        }

        if (count == 0) return true

        AppLogger.d(TAG, "[$collectionName] seed $count docs → db=${FirestoreInstance.DATABASE_ID}/$collectionName")
        return runBatch(batch, "[$collectionName] $count docs", SMALL_BATCH_TIMEOUT_MS)
    }

    private suspend fun seedKamus(root: JsonObject): Boolean {
        val categoriesObj = root.getAsJsonObject("categories")
        if (categoriesObj == null) {
            AppLogger.e(TAG, "[kamus] key 'categories' tidak ada di JSON")
            return false
        }

        val items = parseKamusItems(categoriesObj)
        if (items.isEmpty()) {
            AppLogger.e(TAG, "[kamus] 0 items parsed — seeding dibatalkan")
            return false
        }

        AppLogger.d(TAG, "[kamus] ${items.size} items akan di-seed → db=${FirestoreInstance.DATABASE_ID}/kamus")
        logCategoryBreakdown(items)

        val chunks       = items.chunked(BATCH_SIZE)
        var totalWritten = 0

        for ((index, chunk) in chunks.withIndex()) {
            val label   = "[kamus] batch ${index + 1}/${chunks.size} (${chunk.size} items)"
            val success = writeKamusChunkWithRetry(chunk, label)
            if (!success) {
                AppLogger.e(TAG, "  ✗ $label GAGAL setelah $MAX_RETRY attempt → hentikan seeding")
                return false
            }
            totalWritten += chunk.size
            AppLogger.d(TAG, "  ✓ $label → total $totalWritten/${items.size}")
        }

        AppLogger.d(TAG, "[kamus] selesai: $totalWritten/${items.size} items berhasil ke server")
        return true
    }

    // ── Batch writers ───────────────────────────────────────────────────────────

    /**
     * runBatch: timeout = GAGAL, bukan sukses.
     * seeded_version TIDAK di-set jika tidak ada server ACK.
     */
    private suspend fun runBatch(
        batch: com.google.firebase.firestore.WriteBatch,
        label: String,
        timeoutMs: Long
    ): Boolean {
        return try {
            val ack = withTimeoutOrNull(timeoutMs) { batch.commit().await() }
            if (ack != null) {
                AppLogger.d(TAG, "  ✓ $label: server ACK ✓")
                true
            } else {
                AppLogger.w(TAG, "  ✗ $label: timeout ${timeoutMs}ms — device offline? → GAGAL, retry saat online")
                false
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e(TAG, "  ✗ $label: exception — ${e.message}", e)
            false
        }
    }

    /**
     * writeKamusChunkWithRetry: retry jika timeout atau exception.
     * Return true HANYA jika dapat server ACK eksplisit.
     */
    private suspend fun writeKamusChunkWithRetry(chunk: List<KamusItem>, label: String): Boolean {
        var delayMs = RETRY_BASE_DELAY_MS

        repeat(MAX_RETRY) { attempt ->
            try {
                val batch = db.batch()
                val now   = Timestamp.now()
                val col   = db.collection("kamus")

                chunk.forEach { item ->
                    val docId = buildDocId(item.name)
                    val data  = hashMapOf<String, Any>(
                        "id"          to docId,
                        "name"        to item.name,
                        "aliases"     to item.aliases,
                        "category"    to item.category,
                        "commonUnits" to item.commonUnits,
                        "isActive"    to true,
                        "updatedAt"   to now,
                        "createdAt"   to now
                    )
                    batch.set(col.document(docId), data, SetOptions.merge())
                }

                val ack = withTimeoutOrNull(BATCH_TIMEOUT_MS) { batch.commit().await() }
                if (ack != null) {
                    AppLogger.d(TAG, "  $label attempt ${attempt + 1}: server ACK ✓")
                    return true
                }

                // Timeout — bukan sukses, retry jika masih ada kesempatan
                val isLast = attempt == MAX_RETRY - 1
                if (isLast) {
                    AppLogger.e(TAG, "  $label attempt ${attempt + 1}: timeout ${BATCH_TIMEOUT_MS}ms FINAL → GAGAL")
                } else {
                    AppLogger.w(TAG, "  $label attempt ${attempt + 1}: timeout → retry dalam ${delayMs}ms")
                    delay(delayMs)
                    delayMs = minOf(delayMs * 2, RETRY_MAX_DELAY_MS)
                }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val isLast = attempt == MAX_RETRY - 1
                if (isLast) {
                    AppLogger.e(TAG, "  $label attempt ${attempt + 1}: GAGAL FINAL — ${e.message}", e)
                } else {
                    AppLogger.w(TAG, "  $label attempt ${attempt + 1}: gagal (${e.message}) → retry dalam ${delayMs}ms")
                    delay(delayMs)
                    delayMs = minOf(delayMs * 2, RETRY_MAX_DELAY_MS)
                }
            }
        }
        return false
    }

    // ── JSON parsers ────────────────────────────────────────────────────────────

    private fun readJson(): JsonObject? = try {
        val text = context.assets.open("kamus_items.json").bufferedReader().readText()
        Gson().fromJson(text, JsonObject::class.java)
    } catch (e: Exception) {
        AppLogger.e(TAG, "readJson GAGAL", e)
        null
    }

    private fun readJsonVersion(): String = try {
        val text = context.assets.open("kamus_items.json").bufferedReader().readText()
        Gson().fromJson(text, JsonObject::class.java)
            .getAsJsonObject("_meta")?.get("version")?.asString?.trim() ?: "unknown"
    } catch (_: Exception) { "unknown" }

    private fun parseKamusItems(categoriesObj: JsonObject): List<KamusItem> {
        val items = mutableListOf<KamusItem>()
        categoriesObj.keySet().forEach { categoryKey ->
            val catObj   = categoriesObj.getAsJsonObject(categoryKey) ?: return@forEach
            val itemsArr = catObj.getAsJsonArray("items") ?: return@forEach
            itemsArr.forEach itemLoop@{ el ->
                val obj  = runCatching { el.asJsonObject }.getOrNull() ?: return@itemLoop
                val name = obj.get("name")?.asString?.trim()?.lowercase()?.ifBlank { null }
                    ?: return@itemLoop
                val aliases = obj.getAsJsonArray("aliases")
                    ?.mapNotNull { a -> a.asString?.trim()?.lowercase()?.ifBlank { null } }
                    ?: emptyList()
                val units = obj.getAsJsonArray("common_units")
                    ?.mapNotNull { u -> u.asString?.trim()?.ifBlank { null } }
                    ?: emptyList()
                items.add(KamusItem(name = name, aliases = aliases, commonUnits = units, category = categoryKey))
            }
        }
        return items
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private fun buildDocId(name: String): String =
        name.trim().lowercase()
            .replace(" ", "_").replace("/", "_")
            .replace(Regex("[^a-z0-9_\\-]"), "")

    private fun logCategoryBreakdown(items: List<KamusItem>) =
        items.groupBy { it.category }.forEach { (cat, list) ->
            AppLogger.d(TAG, "  [$cat]: ${list.size} items")
        }

    private fun buildSmallCollectionData(
        collectionName: String,
        key: String,
        rawValue: String,
        now: Timestamp
    ): Map<String, Any> = when (collectionName) {
        "unit_normalization" -> mapOf(
            "input"      to key,
            "normalized" to rawValue,
            "isActive"   to true,
            "updatedAt"  to now,
            "createdAt"  to now
        )
        "number_words" -> {
            val num = rawValue.toDoubleOrNull() ?: 0.0
            mapOf(
                "word"      to key,
                "value"     to num,
                "updatedAt" to now,
                "createdAt" to now
            )
        }
        "multipliers" -> {
            val num = rawValue.toLongOrNull() ?: 1_000L
            mapOf(
                "word"      to key,
                "value"     to num,
                "updatedAt" to now,
                "createdAt" to now
            )
        }
        else -> mapOf("key" to key, "value" to rawValue, "updatedAt" to now)
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
