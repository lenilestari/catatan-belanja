package com.lenilestari.aethersea.processor

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lenilestari.aethersea.data.model.KamusItem
import com.lenilestari.aethersea.data.repository.KamusRepository

class LocalDictionary(context: Context) {
    // Local fallback (from assets) — used when Firestore cache not yet populated
    private data class DictItem(val name: String, val aliases: List<String>, val common_units: List<String>)
    private data class DictCategory(val items: List<DictItem>)
    private data class DictRoot(val categories: Map<String, DictCategory>)

    private val localItems: List<DictItem>
    private val repo = KamusRepository()

    init {
        localItems = try {
            val json = context.assets.open("kamus_items.json").bufferedReader().readText()
            val root = Gson().fromJson<DictRoot>(json, object : TypeToken<DictRoot>() {}.type)
            root.categories.values.flatMap { it.items }
        } catch (_: Exception) { emptyList() }
    }

    // Fast path: exact match from Firestore (hits local cache after migration)
    suspend fun findExact(query: String): String? {
        val result = repo.findExact(query)
        if (result != null) return result.name
        return findLocalExact(query)
    }

    // Fuzzy match: load all from Firestore cache, run Levenshtein locally
    suspend fun findClosest(query: String, maxDistance: Int = 2): String? {
        val q = query.trim().lowercase()
        val firestoreItems = repo.getAllCached()

        if (firestoreItems.isNotEmpty()) {
            // Exact match first
            firestoreItems.firstOrNull { it.name == q || it.aliases.contains(q) }
                ?.let { return it.name }
            // Fuzzy
            var best: String? = null
            var bestDist = maxDistance + 1
            firestoreItems.forEach { item ->
                (listOf(item.name) + item.aliases).forEach { candidate ->
                    val d = levenshtein(q, candidate)
                    if (d < bestDist) { bestDist = d; best = item.name }
                }
            }
            if (bestDist <= maxDistance) return best
        }

        // Fallback to local assets
        return findLocalFuzzy(q, maxDistance)
    }

    private fun findLocalExact(query: String): String? {
        val q = query.trim().lowercase()
        return localItems.firstOrNull { it.name == q || it.aliases.contains(q) }?.name
    }

    private fun findLocalFuzzy(q: String, maxDistance: Int): String? {
        var best: String? = null
        var bestDist = maxDistance + 1
        localItems.forEach { item ->
            (listOf(item.name) + item.aliases).forEach { c ->
                val d = levenshtein(q, c)
                if (d < bestDist) { bestDist = d; best = item.name }
            }
        }
        return if (bestDist <= maxDistance) best else null
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

// Extension to convert Firestore KamusItem → can be used by adapters/autocomplete
fun KamusItem.toDisplayName(): String = name
