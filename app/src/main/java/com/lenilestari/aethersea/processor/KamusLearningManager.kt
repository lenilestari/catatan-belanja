package com.lenilestari.aethersea.processor

import android.content.Context
import android.util.Log
import com.lenilestari.aethersea.data.model.KamusItem
import com.lenilestari.aethersea.data.model.ShoppingItem
import com.lenilestari.aethersea.data.repository.KamusRepository
import com.lenilestari.aethersea.data.repository.LearningRepository
import com.lenilestari.aethersea.remote.GeminiClient

private const val TAG = "KamusLearn"

object KamusLearningManager {

    private const val PROMOTE_THRESHOLD = 3
    private const val ALIAS_SIMILARITY_THRESHOLD = 0.80

    suspend fun analyze(context: Context, parsedItems: List<ShoppingItem>) {
        Log.d(TAG, "════ analyze() START ════")
        Log.d(TAG, "  parsedItems (${parsedItems.size}): ${parsedItems.map { it.item }}")
        try {
            val kamusItems = KamusRepository().getAllCached()
            Log.d(TAG, "  kamus size: ${kamusItems.size}")

            // 1. Filter item yang sudah dikenal
            val allNames = parsedItems.map { it.item.lowercase().trim() }
                .filter { it.isNotBlank() && it.length > 1 && it != "item" }
            Log.d(TAG, "  [1] kandidat nama    : $allNames")

            val unknownNames = allNames.filter { name -> !isKnown(name, kamusItems) }.distinct()
            val knownNames   = allNames.filter { name ->  isKnown(name, kamusItems) }.distinct()
            Log.d(TAG, "  [1] sudah dikenal    : $knownNames")
            Log.d(TAG, "  [1] BELUM dikenal    : $unknownNames")

            if (unknownNames.isEmpty()) {
                Log.d(TAG, "  Semua item sudah dikenal → skip AI learning")
                Log.d(TAG, "════ analyze() END ════")
                return
            }

            // 2. Levenshtein alias hints
            val aliasHints = findAliasHints(unknownNames, kamusItems)
            if (aliasHints.isNotEmpty()) {
                Log.d(TAG, "  [2] alias hints (≥80%): ${aliasHints.map { (a, t) -> "'$a'→'$t'" }}")
            } else {
                Log.d(TAG, "  [2] alias hints: tidak ada yang mirip (semua < 80%)")
            }

            // 3. AI analisis
            Log.d(TAG, "  [3] kirim ke Gemini: $unknownNames")
            val analysisResult = GeminiClient.analyzeLearning(
                unknownNames = unknownNames,
                kamusItems   = kamusItems,
                aliasHints   = aliasHints
            )
            if (analysisResult.isFailure) {
                Log.e(TAG, "  [3] Gemini GAGAL: ${analysisResult.exceptionOrNull()?.message}")
                Log.d(TAG, "════ analyze() END ════")
                return
            }
            val analysis = analysisResult.getOrNull()!!
            Log.d(TAG, "  [3] Gemini response:")
            Log.d(TAG, "       new_items   (${analysis.newItems.size}): ${analysis.newItems.map { "${it.name}[${it.category}]" }}")
            Log.d(TAG, "       new_aliases (${analysis.newAliases.size}): ${analysis.newAliases.map { "${it.alias}→${it.itemName}" }}")
            Log.d(TAG, "       new_units   (${analysis.newUnits.size}): ${analysis.newUnits.map { "${it.original}→${it.normalized}" }}")

            // 4. Simpan ke Firestore
            val repo = LearningRepository()
            analysis.newItems.forEach   { repo.upsertNewItem(it) }
            analysis.newAliases.forEach { repo.upsertNewAlias(it) }
            analysis.newUnits.forEach   { repo.upsertNewUnit(it) }
            Log.d(TAG, "  [4] upsert selesai")

            // 5. Promosikan ke kamus
            repo.promoteAboveThreshold(PROMOTE_THRESHOLD)
            Log.d(TAG, "  [5] promote (threshold=$PROMOTE_THRESHOLD) selesai")

        } catch (e: Exception) {
            Log.e(TAG, "  EXCEPTION: ${e.message}", e)
        }
        Log.d(TAG, "════ analyze() END ════")
    }

    // --- Kotlin pre-classification helpers ---

    private fun isKnown(name: String, kamusItems: List<KamusItem>): Boolean =
        kamusItems.any { it.name == name || it.aliases.contains(name) }

    private fun findAliasHints(
        unknownNames: List<String>,
        kamusItems: List<KamusItem>
    ): List<Pair<String, String>> =
        unknownNames.mapNotNull { name ->
            val (best, sim) = bestMatch(name, kamusItems)
            val simPct = "%.0f%%".format(sim * 100)
            if (sim >= ALIAS_SIMILARITY_THRESHOLD && best != null) {
                Log.d(TAG, "    levenshtein '$name' ↔ '$best' = $simPct ✓ alias")
                name to best
            } else {
                Log.d(TAG, "    levenshtein '$name' ↔ '${best ?: "-"}' = $simPct ✗ bukan alias")
                null
            }
        }

    private fun bestMatch(name: String, kamusItems: List<KamusItem>): Pair<String?, Double> {
        var bestName: String? = null
        var bestSim = 0.0
        kamusItems.forEach { item ->
            (listOf(item.name) + item.aliases).forEach { candidate ->
                val s = similarity(name, candidate)
                if (s > bestSim) { bestSim = s; bestName = item.name }
            }
        }
        return bestName to bestSim
    }

    private fun similarity(a: String, b: String): Double {
        val maxLen = maxOf(a.length, b.length)
        if (maxLen == 0) return 1.0
        return 1.0 - levenshtein(a, b).toDouble() / maxLen
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            dp[i][j] = if (a[i - 1] == b[j - 1]) dp[i - 1][j - 1]
                       else 1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
        }
        return dp[a.length][b.length]
    }
}
