package com.lenilestari.aethersea.processor

import com.lenilestari.aethersea.data.model.KamusItem
import com.lenilestari.aethersea.data.repository.KamusRepository
import com.lenilestari.aethersea.util.KnowledgeCache

/**
 * Dictionary lookup untuk voice input parsing.
 *
 * Sumber data (priority order):
 *   1. Firestore local cache via KamusRepository (akurat, sync otomatis dari server)
 *   2. KnowledgeCache.kamusItems (in-memory, diisi KnowledgeLoader dari Firestore/JSON)
 *
 * Tidak ada lagi init{} yang membaca assets JSON secara langsung.
 * JSON hanya dibaca oleh KnowledgeLoader sebagai offline fallback, bukan runtime dependency.
 */
class LocalDictionary {
    private val repo = KamusRepository()

    /** Exact match — coba Firestore cache via repo, lalu in-memory cache */
    suspend fun findExact(query: String): String? {
        val result = repo.findExact(query)
        if (result != null) return result.name

        val q = query.trim().lowercase()
        return KnowledgeCache.kamusItems
            .firstOrNull { it.name == q || it.aliases.contains(q) }
            ?.name
    }

    /**
     * Fuzzy match Levenshtein distance.
     * Prioritas: Firestore cache → KnowledgeCache (in-memory fallback).
     */
    suspend fun findClosest(query: String, maxDistance: Int = 2): String? {
        val q             = query.trim().lowercase()
        val firestoreItems = repo.getAllCached()
        val items          = firestoreItems.ifEmpty { KnowledgeCache.kamusItems }

        if (items.isEmpty()) return null

        // Exact/alias match dulu (O(n) lebih cepat dari Levenshtein loop full)
        items.firstOrNull { it.name == q || it.aliases.contains(q) }
            ?.let { return it.name }

        // Fuzzy Levenshtein
        var best: String? = null
        var bestDist      = maxDistance + 1
        items.forEach { item ->
            (listOf(item.name) + item.aliases).forEach { candidate ->
                val d = levenshtein(q, candidate)
                if (d < bestDist) { bestDist = d; best = item.name }
            }
        }
        return if (bestDist <= maxDistance) best else null
    }

    /** Semua item dari cache — dipakai KamusLearningManager untuk Levenshtein bulk */
    suspend fun getAllItems(): List<KamusItem> {
        val fromFirestore = repo.getAllCached()
        return fromFirestore.ifEmpty { KnowledgeCache.kamusItems }
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

fun KamusItem.toDisplayName(): String = name
