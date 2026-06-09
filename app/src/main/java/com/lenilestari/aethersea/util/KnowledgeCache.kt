package com.lenilestari.aethersea.util

import com.lenilestari.aethersea.data.model.KamusItem

/**
 * In-memory knowledge store — single source of truth untuk runtime.
 *
 * Diisi oleh KnowledgeLoader saat app start (background).
 * Dipakai secara synchronous oleh TextNormalizer dan LocalDictionary.
 *
 * Thread-safety: semua field @Volatile agar main thread dan IO thread
 * membaca nilai terbaru tanpa CPU cache staleness. updateAll() @Synchronized
 * mencegah partial update saat ada concurrent write.
 *
 * Fallback chain (otomatis):
 *   Firestore cache → Firestore server → JSON assets → DEFAULT hardcoded
 */
object KnowledgeCache {

    // ── Hardcoded Defaults ────────────────────────────────────────────────────
    // Harus dideklarasi SEBELUM field yang memakainya sebagai nilai awal,
    // karena Kotlin object menginisialisasi properti dari atas ke bawah.
    // Ini adalah safety net — bukan data utama.

    val DEFAULT_UNITS: Map<String, String> = mapOf(
        "kilogram" to "kg",   "kilo" to "kg",      "kg" to "kg",
        "gram" to "gram",     "gr" to "gram",       "grm" to "gram",
        "liter" to "liter",   "litre" to "liter",   "ltr" to "liter",   "lt" to "liter",
        "bungkus" to "bungkus", "bks" to "bungkus",
        "botol" to "botol",   "btl" to "botol",
        "buah" to "buah",     "bh" to "buah",       "pcs" to "buah",
        "ikat" to "ikat",     "ikt" to "ikat",
        "tray" to "tray",     "rak" to "tray",
        "kaleng" to "kaleng", "klg" to "kaleng",
        "lembar" to "lembar", "lbr" to "lembar",
        "meter" to "meter",   "mtr" to "meter",     "m" to "meter",
        "ons" to "ons",       "sachet" to "sachet",
        "kotak" to "kotak",   "pak" to "pak",       "kardus" to "kardus",
        "dus" to "kardus",    "sak" to "sak",       "galon" to "galon",
        "batang" to "batang", "roll" to "roll",     "cup" to "cup",
        "sisir" to "sisir",   "ekor" to "ekor",     "butir" to "butir",
        "potong" to "potong", "papan" to "papan",   "lusin" to "lusin"
    )

    val DEFAULT_NUMBER_WORDS: Map<String, String> = mapOf(
        "nol" to "0",          "satu" to "1",          "dua" to "2",
        "tiga" to "3",         "empat" to "4",          "lima" to "5",
        "enam" to "6",         "tujuh" to "7",          "delapan" to "8",
        "sembilan" to "9",     "sepuluh" to "10",       "sebelas" to "11",
        "dua belas" to "12",   "dua puluh" to "20",     "tiga puluh" to "30",
        "empat puluh" to "40", "lima puluh" to "50",
        "setengah" to "0.5",   "seperempat" to "0.25",  "selusin" to "12"
    )

    val DEFAULT_MULTIPLIERS: Map<String, Long> = mapOf(
        "ribu" to 1_000L,         "rb" to 1_000L,    "rbu" to 1_000L,
        "juta" to 1_000_000L,     "jt" to 1_000_000L,
        "miliar" to 1_000_000_000L
    )

    // ── Knowledge Data ────────────────────────────────────────────────────────

    /** Map input → normalized unit (e.g. "kilo" → "kg", "ltr" → "liter") */
    @Volatile var unitNormalization: Map<String, String> = DEFAULT_UNITS
        private set

    /** Map kata → angka sebagai string (e.g. "satu" → "1", "setengah" → "0.5") */
    @Volatile var numberWords: Map<String, String> = DEFAULT_NUMBER_WORDS
        private set

    /** Map kata → nilai long (e.g. "ribu" → 1000L, "juta" → 1_000_000L) */
    @Volatile var multipliers: Map<String, Long> = DEFAULT_MULTIPLIERS
        private set

    /** Semua item kamus (nama, alias, kategori, satuan) dari Firestore */
    @Volatile var kamusItems: List<KamusItem> = emptyList()
        private set

    // ── State ─────────────────────────────────────────────────────────────────

    @Volatile var isLoaded: Boolean = false
        private set

    @Volatile var loadedAt: Long = 0L
        private set

    /** "firestore_cache" | "firestore_server" | "json_fallback" | "default" */
    @Volatile var source: String = "default"
        private set

    fun isStale(ttlMs: Long = 24 * 60 * 60 * 1000L): Boolean =
        !isLoaded || System.currentTimeMillis() - loadedAt > ttlMs

    // ── Update ────────────────────────────────────────────────────────────────

    @Synchronized
    fun updateAll(
        units: Map<String, String>,
        numbers: Map<String, String>,
        mults: Map<String, Long>,
        kamus: List<KamusItem>,
        src: String
    ) {
        unitNormalization = units.ifEmpty { DEFAULT_UNITS }
        numberWords       = numbers.ifEmpty { DEFAULT_NUMBER_WORDS }
        multipliers       = mults.ifEmpty { DEFAULT_MULTIPLIERS }
        kamusItems        = kamus
        isLoaded          = true
        loadedAt          = System.currentTimeMillis()
        source            = src
    }
}
