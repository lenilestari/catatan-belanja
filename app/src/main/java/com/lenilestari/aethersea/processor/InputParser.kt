package com.lenilestari.aethersea.processor

import android.util.Log

private const val TAG = "InputParser"

object InputParser {

    // Kata-kata filler yang diabaikan
    private val noiseWords = setOf(
        "eh", "yang", "itu", "ya", "dong", "beli", "mau", "tolong",
        "seharga", "dengan", "harga", "bayar", "rupiah", "rp",
        "nih", "deh", "si", "tuh", "juga", "lagi", "tadi", "ini", "aja",
        "minta", "kasih", "ambil", "cari"
    )

    // Angka digit dengan opsional titik/koma (format Indonesia)
    private val numberPattern = Regex("""\d[\d.,]*""")

    // "N ratus" → N×100 (tidak ditangani TextNormalizer)
    private val ratusPattern = Regex("""(\d+)\s*ratus\b""")

    data class ParsedInput(val nama: String, val qty: Int, val harga: Long)

    fun parse(input: String): ParsedInput {
        Log.d(TAG, "━━━━━━ parse() START ━━━━━━")
        Log.d(TAG, "  [0] raw input       : \"$input\"")

        // 1. Normalisasi dasar
        var text = TextNormalizer.normalize(input)
        Log.d(TAG, "  [1] after normalize  : \"$text\"")

        // 2. Tangani "ratus"
        text = expandRatus(text)
        Log.d(TAG, "  [2] after expandRatus: \"$text\"")

        // 3. Hapus noise/filler
        text = stripNoise(text)
        Log.d(TAG, "  [3] after stripNoise : \"$text\"")

        // 4. Gabungkan digit tunggal
        text = mergeDigitSequences(text)
        Log.d(TAG, "  [4] after mergeDigits: \"$text\"")

        // 5. Temukan token angka
        val matches = numberPattern.findAll(text).toList()
        Log.d(TAG, "  [5] numbers found    : ${matches.map { it.value }}")

        // 6. Nama
        val nama = extractName(text, matches)
        Log.d(TAG, "  [6] nama extracted   : \"$nama\"")

        // 7. Petakan ke qty + harga
        val numbers = matches.map { toLong(it.value) }
        Log.d(TAG, "  [7] numbers (long)   : $numbers")

        val result = when (numbers.size) {
            0    -> ParsedInput(nama, 1, 0L)
            1    -> ParsedInput(nama, 1, numbers[0])
            else -> ParsedInput(
                nama  = nama,
                qty   = maxOf(1, numbers[numbers.size - 2].toInt()),
                harga = maxOf(0L, numbers.last())
            )
        }
        Log.d(TAG, "  [8] RESULT           : nama=\"${result.nama}\" qty=${result.qty} harga=${result.harga}")
        Log.d(TAG, "━━━━━━ parse() END ━━━━━━")
        return result
    }

    // --- private helpers ---

    private fun expandRatus(text: String): String =
        ratusPattern.replace(text) { m ->
            (m.groupValues[1].toLong() * 100L).toString()
        }

    private fun stripNoise(text: String): String {
        var result = text
        noiseWords.sortedByDescending { it.length }.forEach { word ->
            result = result.replace(Regex("\\b${Regex.escape(word)}\\b"), " ")
        }
        return result.replace(Regex("\\s+"), " ").trim()
    }

    // Gabungkan token digit-tunggal berurutan: "5 0 0 0" → "5000"
    private fun mergeDigitSequences(text: String): String {
        val tokens = text.split(" ")
        val result = mutableListOf<String>()
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            if (t.length == 1 && t[0].isDigit()) {
                val seq = StringBuilder(t)
                var j = i + 1
                while (j < tokens.size && tokens[j].length == 1 && tokens[j][0].isDigit()) {
                    seq.append(tokens[j])
                    j++
                }
                result.add(seq.toString())
                i = j
            } else {
                result.add(t)
                i++
            }
        }
        return result.joinToString(" ")
    }

    private fun extractName(text: String, matches: List<MatchResult>): String {
        val raw = if (matches.isEmpty()) text
                  else text.substring(0, matches.first().range.first)
        return raw.trim().ifEmpty { "item" }
    }

    // Titik = ribuan (50.000 → 50000), koma dihapus → Long (hindari Int overflow pada miliar)
    private fun toLong(s: String): Long =
        s.replace(",", "").replace(".", "").toLongOrNull() ?: 0L
}
