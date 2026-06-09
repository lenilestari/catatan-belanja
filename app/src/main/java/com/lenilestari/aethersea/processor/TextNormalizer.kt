package com.lenilestari.aethersea.processor

import com.lenilestari.aethersea.util.AppLogger
import com.lenilestari.aethersea.util.KnowledgeCache

private const val TAG = "TextNorm"

/**
 * Normalisasi teks input voice/manual.
 *
 * Semua knowledge (numberWords, multipliers, unitNormalization) dibaca dari
 * KnowledgeCache — diisi KnowledgeLoader saat startup dari Firestore.
 *
 * Jika KnowledgeLoader belum selesai (race condition awal startup), cache
 * sudah berisi DEFAULT_* hardcoded sebagai safety net — tidak pernah fail.
 * Tidak ada lagi dependency ke assets JSON saat runtime.
 */
object TextNormalizer {
    private val splitRegex = Regex("(?i)\\s+(?:dan|sama|lalu|terus|juga|plus|kemudian)\\s+|,|;")

    fun normalize(input: String): String {
        var text = input.lowercase().trim()
        AppLogger.d(TAG, "normalize IN : \"$text\"")

        val cache       = KnowledgeCache
        val numberWords = cache.numberWords      // Firestore /number_words (atau DEFAULT)
        val multipliers = cache.multipliers      // Firestore /multipliers (atau DEFAULT)
        val unitAliases = cache.unitNormalization // Firestore /unit_normalization (atau DEFAULT)

        // 1. Ganti kata angka — paling panjang dulu agar "dua belas" tidak dipotong jadi "2 belas"
        val before1 = text
        numberWords.entries.sortedByDescending { it.key.length }.forEach { (word, num) ->
            text = text.replace(Regex("\\b${Regex.escape(word)}\\b"), num)
        }
        if (text != before1) AppLogger.d(TAG, "  after numberWords  : \"$text\"")

        // 2. "X ribu/juta" → numeric — paling panjang dulu agar "miliar" tidak overlap "juta"
        val before2 = text
        multipliers.entries.sortedByDescending { it.key.length }.forEach { (suffix, mult) ->
            text = Regex("(\\d+(?:[.,]\\d+)?)\\s*${Regex.escape(suffix)}\\b").replace(text) { match ->
                val num    = match.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
                val result = (num * mult).toLong().toString()
                AppLogger.d(TAG, "  multiplier hit: \"${match.value}\" → $result")
                result
            }
        }
        if (text != before2) AppLogger.d(TAG, "  after multipliers  : \"$text\"")

        // 3. Normalisasi satuan — paling panjang dulu agar "kilogram" tidak jadi "kilo gram"
        val before3 = text
        unitAliases.entries.sortedByDescending { it.key.length }.forEach { (alias, canonical) ->
            text = text.replace(Regex("\\b${Regex.escape(alias)}\\b"), canonical)
        }
        if (text != before3) AppLogger.d(TAG, "  after unitAliases  : \"$text\"")

        text = text.replace(Regex("\\s+"), " ").trim()
        AppLogger.d(TAG, "normalize OUT: \"$text\"")
        return text
    }

    fun splitItems(input: String): List<String> =
        input.split(splitRegex).map { it.trim() }.filter { it.isNotEmpty() }
}
