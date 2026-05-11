package com.lenilestari.aethersea.processor

import android.util.Log

private const val TAG = "TextNorm"

object TextNormalizer {
    private val splitRegex = Regex("(?i)\\s+(?:dan|sama|lalu|terus|juga|plus|kemudian)\\s+|,|;")

    private val numberWords = mapOf(
        "nol" to "0", "satu" to "1", "dua" to "2", "tiga" to "3", "empat" to "4",
        "lima" to "5", "enam" to "6", "tujuh" to "7", "delapan" to "8",
        "sembilan" to "9", "sepuluh" to "10", "sebelas" to "11",
        "dua belas" to "12", "dua puluh" to "20", "tiga puluh" to "30",
        "empat puluh" to "40", "lima puluh" to "50",
        "setengah" to "0.5", "seperempat" to "0.25", "selusin" to "12"
    )

    private val multipliers = mapOf(
        "ribu" to 1000L, "rb" to 1000L, "rbu" to 1000L,
        "juta" to 1000000L, "jt" to 1000000L,
        "miliar" to 1000000000L
    )

    private val unitAliases = listOf(
        "kilogram" to "kg", "kilo" to "kg",
        "liter" to "liter", "litre" to "liter", "ltr" to "liter", "lt" to "liter",
        "gram" to "gram", "gr" to "gram", "grm" to "gram",
        "bungkus" to "bungkus", "bks" to "bungkus",
        "botol" to "botol", "btl" to "botol",
        "buah" to "buah", "bh" to "buah", "pcs" to "buah",
        "ikat" to "ikat", "ikt" to "ikat",
        "tray" to "tray", "rak" to "tray",
        "kaleng" to "kaleng", "klg" to "kaleng",
        "lembar" to "lembar", "lbr" to "lembar",
        "meter" to "meter", "mtr" to "meter", "m" to "meter"
    )

    fun normalize(input: String): String {
        var text = input.lowercase().trim()
        Log.d(TAG, "normalize IN : \"$text\"")

        // Replace number words (multi-word first)
        val before1 = text
        numberWords.entries.sortedByDescending { it.key.length }.forEach { (w, n) ->
            text = text.replace(Regex("\\b${Regex.escape(w)}\\b"), n)
        }
        if (text != before1) Log.d(TAG, "  after numberWords  : \"$text\"")

        // "X ribu/juta" → numeric
        val before2 = text
        multipliers.forEach { (suffix, mult) ->
            text = Regex("(\\d+(?:[.,]\\d+)?)\\s*$suffix\\b").replace(text) { match ->
                val num = match.groupValues[1].replace(",", ".").toDouble()
                val result = (num * mult).toLong().toString()
                Log.d(TAG, "  multiplier hit: \"${match.value}\" → $result")
                result
            }
        }
        if (text != before2) Log.d(TAG, "  after multipliers  : \"$text\"")

        // Unit aliases
        val before3 = text
        unitAliases.forEach { (alias, canonical) ->
            text = text.replace(Regex("\\b${Regex.escape(alias)}\\b"), canonical)
        }
        if (text != before3) Log.d(TAG, "  after unitAliases  : \"$text\"")

        text = text.replace(Regex("\\s+"), " ").trim()
        Log.d(TAG, "normalize OUT: \"$text\"")
        return text
    }

    fun splitItems(input: String): List<String> {
        return input.split(splitRegex)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }
}
