package com.lenilestari.aethersea.util

import android.content.Context
import com.google.firebase.firestore.Source
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.lenilestari.aethersea.data.model.KamusItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Memuat seluruh knowledge (kamus, unit_normalization, number_words, multipliers)
 * dari Firestore ke KnowledgeCache.
 *
 * Fallback chain (berurutan):
 *   1. Firestore local cache (instant, no network) — dipakai setelah install pertama
 *   2. Firestore server (max 10s) — jika cache kosong (install pertama + online)
 *   3. JSON assets (kamus_items.json) — jika offline total + cache kosong
 *   4. KnowledgeCache.DEFAULT_* — hardcoded safety net, selalu tersedia
 *
 * Dipanggil dari CatatanBelanjaApp (background coroutine, tidak blocking startup).
 * Aman dipanggil berkali-kali — skip jika cache masih fresh (TTL 24 jam).
 */
class KnowledgeLoader(private val context: Context) {

    private val db = FirestoreInstance.db

    private companion object {
        const val TAG = "KnowledgeLoader"
        const val SERVER_TIMEOUT_MS = 10_000L
        const val MIN_MEANINGFUL_DOCS = 5  // kamus valid = paling tidak 5 item
    }

    suspend fun loadIfNeeded() {
        if (!KnowledgeCache.isStale()) {
            AppLogger.d(TAG, "Cache masih fresh (source=${KnowledgeCache.source}) → skip")
            return
        }
        loadAll()
    }

    suspend fun loadAll() {
        AppLogger.d(TAG, "══════ loadAll() START ══════")
        val startMs = System.currentTimeMillis()

        // Step 1: Firestore local cache (no network, instant)
        if (tryLoadFromFirestore(Source.CACHE)) {
            AppLogger.d(TAG, "✓ Loaded dari Firestore cache dalam ${elapsed(startMs)}ms")
            AppLogger.d(TAG, "  kamus=${KnowledgeCache.kamusItems.size}, units=${KnowledgeCache.unitNormalization.size}")
            AppLogger.d(TAG, "══════ loadAll() DONE ══════")
            return
        }

        // Step 2: Firestore server (butuh network, max 10s)
        AppLogger.d(TAG, "Firestore cache kosong/kecil → coba server (timeout=${SERVER_TIMEOUT_MS}ms)")
        val serverOk = withTimeoutOrNull(SERVER_TIMEOUT_MS) {
            tryLoadFromFirestore(Source.SERVER)
        } ?: false

        if (serverOk) {
            AppLogger.d(TAG, "✓ Loaded dari Firestore server dalam ${elapsed(startMs)}ms")
            AppLogger.d(TAG, "  kamus=${KnowledgeCache.kamusItems.size}, units=${KnowledgeCache.unitNormalization.size}")
            AppLogger.d(TAG, "══════ loadAll() DONE ══════")
            return
        }

        // Step 3: JSON assets fallback
        AppLogger.w(TAG, "Firestore tidak tersedia → fallback ke JSON assets")
        if (loadFromJson()) {
            AppLogger.d(TAG, "✓ Loaded dari JSON dalam ${elapsed(startMs)}ms")
            AppLogger.d(TAG, "  kamus=${KnowledgeCache.kamusItems.size}, units=${KnowledgeCache.unitNormalization.size}")
        } else {
            // Step 4: Hardcoded defaults (sudah di-set saat loadFromJson() gagal)
            AppLogger.w(TAG, "JSON gagal → pakai DEFAULT hardcoded")
        }

        AppLogger.d(TAG, "══════ loadAll() DONE (source=${KnowledgeCache.source}) ══════")
    }

    // ── Firestore loader ──────────────────────────────────────────────────────

    private suspend fun tryLoadFromFirestore(source: Source): Boolean {
        return try {
            val units   = loadUnitNormalization(source)
            val numbers = loadNumberWords(source)
            val mults   = loadMultipliers(source)
            val kamus   = loadKamus(source)

            // Data bermakna jika kamus punya cukup item
            if (kamus.size < MIN_MEANINGFUL_DOCS && units.isEmpty()) {
                AppLogger.d(TAG, "tryLoadFromFirestore(${source.name}): data tidak cukup (kamus=${kamus.size})")
                return false
            }

            val src = if (source == Source.CACHE) "firestore_cache" else "firestore_server"
            KnowledgeCache.updateAll(units, numbers, mults, kamus, src)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG, "tryLoadFromFirestore(${source.name}) gagal: ${e.message}")
            false
        }
    }

    private suspend fun loadUnitNormalization(source: Source): Map<String, String> {
        val snap = db.collection("unit_normalization").get(source).await()
        return snap.documents.associate { doc ->
            val input      = doc.getString("input") ?: doc.id
            val normalized = doc.getString("normalized") ?: input
            input to normalized
        }.also { AppLogger.d(TAG, "  unit_normalization: ${it.size} docs") }
    }

    private suspend fun loadNumberWords(source: Source): Map<String, String> {
        val snap = db.collection("number_words").get(source).await()
        return snap.documents.associate { doc ->
            val word  = doc.getString("word") ?: doc.id
            val value = doc.getDouble("value") ?: 0.0
            val str   = if (value == value.toLong().toDouble()) value.toLong().toString()
                        else value.toString()
            word to str
        }.also { AppLogger.d(TAG, "  number_words: ${it.size} docs") }
    }

    private suspend fun loadMultipliers(source: Source): Map<String, Long> {
        val snap = db.collection("multipliers").get(source).await()
        return snap.documents.associate { doc ->
            val word  = doc.getString("word") ?: doc.id
            val value = doc.getLong("value") ?: 1_000L
            word to value
        }.also { AppLogger.d(TAG, "  multipliers: ${it.size} docs") }
    }

    private suspend fun loadKamus(source: Source): List<KamusItem> {
        val snap = db.collection("kamus").get(source).await()
        return snap.documents.mapNotNull { doc ->
            doc.toObject(KamusItem::class.java)?.copy(id = doc.id)
        }.also { AppLogger.d(TAG, "  kamus: ${it.size} docs") }
    }

    // ── JSON fallback ─────────────────────────────────────────────────────────

    private fun loadFromJson(): Boolean {
        return try {
            val json = context.assets.open("kamus_items.json").bufferedReader().readText()
            val root = Gson().fromJson(json, JsonObject::class.java)

            val units   = parseJsonUnits(root)
            val numbers = parseJsonNumberWords(root)
            val mults   = parseJsonMultipliers(root)
            val kamus   = parseJsonKamus(root)

            KnowledgeCache.updateAll(
                units   = units.ifEmpty   { KnowledgeCache.DEFAULT_UNITS },
                numbers = numbers.ifEmpty { KnowledgeCache.DEFAULT_NUMBER_WORDS },
                mults   = mults.ifEmpty   { KnowledgeCache.DEFAULT_MULTIPLIERS },
                kamus   = kamus,
                src     = "json_fallback"
            )
            AppLogger.d(TAG, "  JSON: units=${units.size} numbers=${numbers.size} mults=${mults.size} kamus=${kamus.size}")
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "loadFromJson GAGAL — pakai defaults", e)
            KnowledgeCache.updateAll(
                units   = KnowledgeCache.DEFAULT_UNITS,
                numbers = KnowledgeCache.DEFAULT_NUMBER_WORDS,
                mults   = KnowledgeCache.DEFAULT_MULTIPLIERS,
                kamus   = emptyList(),
                src     = "default"
            )
            false
        }
    }

    private fun parseJsonUnits(root: JsonObject): Map<String, String> {
        val result = mutableMapOf<String, String>()
        root.getAsJsonObject("unit_normalization")?.entrySet()?.forEach { (k, v) ->
            result[k.trim().lowercase()] = v.asString.trim()
        }
        return result
    }

    private fun parseJsonNumberWords(root: JsonObject): Map<String, String> {
        val result = mutableMapOf<String, String>()
        root.getAsJsonObject("number_words")?.entrySet()?.forEach { (k, v) ->
            val num = v.asDouble
            result[k.trim().lowercase()] =
                if (num == num.toLong().toDouble()) num.toLong().toString() else num.toString()
        }
        return result
    }

    private fun parseJsonMultipliers(root: JsonObject): Map<String, Long> {
        val result = mutableMapOf<String, Long>()
        root.getAsJsonObject("multipliers")?.entrySet()?.forEach { (k, v) ->
            result[k.trim().lowercase()] = v.asLong
        }
        return result
    }

    private fun parseJsonKamus(root: JsonObject): List<KamusItem> {
        val items = mutableListOf<KamusItem>()
        root.getAsJsonObject("categories")?.entrySet()?.forEach { (categoryKey, catValue) ->
            catValue.asJsonObject.getAsJsonArray("items")?.forEach itemLoop@{ el ->
                val obj  = runCatching { el.asJsonObject }.getOrNull() ?: return@itemLoop
                val name = obj.get("name")?.asString?.trim()?.lowercase()?.ifBlank { null }
                    ?: return@itemLoop
                val aliases = obj.getAsJsonArray("aliases")
                    ?.mapNotNull { a -> a.asString?.trim()?.lowercase()?.ifBlank { null } }
                    ?: emptyList()
                val units2 = obj.getAsJsonArray("common_units")
                    ?.mapNotNull { u -> u.asString?.trim()?.ifBlank { null } }
                    ?: emptyList()
                items.add(KamusItem(
                    id          = name.replace(" ", "_"),
                    name        = name,
                    aliases     = aliases,
                    commonUnits = units2,
                    category    = categoryKey,
                    isActive    = true
                ))
            }
        }
        return items
    }

    private fun elapsed(startMs: Long) = System.currentTimeMillis() - startMs
}
