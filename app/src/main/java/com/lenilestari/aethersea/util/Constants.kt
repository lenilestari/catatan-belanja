package com.lenilestari.aethersea.util

object Constants {
    // Semua 3 key dicoba berurutan per model
    val GEMINI_API_KEYS: List<String> get() = listOf(
        com.lenilestari.aethersea.BuildConfig.GEMINI_API_KEY_1,
        com.lenilestari.aethersea.BuildConfig.GEMINI_API_KEY_2,
        com.lenilestari.aethersea.BuildConfig.GEMINI_API_KEY_3
    ).filter { it.isNotEmpty() }
    val CLAUDE_API_KEY: String get() = com.lenilestari.aethersea.BuildConfig.CLAUDE_API_KEY
    const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/"
    const val CLAUDE_BASE_URL = "https://api.anthropic.com/v1/messages"
    const val CLAUDE_API_VERSION = "2023-06-01"
    const val CLAUDE_MODEL = "claude-sonnet-4-6-20250514"
    // Urutan fallback: dicoba dari atas ke bawah saat 429/404/503
    val GEMINI_FALLBACK_MODELS = listOf(
        "gemini-2.5-flash-preview-05-20",
        "gemini-2.0-flash",
        "gemini-2.0-flash-lite",
        "gemini-1.5-flash",
        "gemini-1.5-flash-8b"
    )

    // TODO: ganti dengan Web Client ID dari Firebase Console (Project Settings → Your apps → Web app)
    const val WEB_CLIENT_ID = "841691081121-mjm2b6quo19qfqjjjb8ac1cget4do64o.apps.googleusercontent.com"

    const val CLEANUP_INTERVAL_DAYS = 90L
    const val EXPORT_FOLDER_NAME = "CatatanBelanja"

    // Intent extras
    const val EXTRA_CATEGORY_ID = "extra_category_id"
    const val EXTRA_CATEGORY_NAME = "extra_category_name"
    const val EXTRA_CATEGORY_ICON = "extra_category_icon"
    const val EXTRA_SUB_CATEGORY_ID = "extra_sub_category_id"
    const val EXTRA_SUB_CATEGORY_NAME = "extra_sub_category_name"
    const val EXTRA_SESSION_ID = "extra_session_id"
    const val EXTRA_WISHLIST_ID = "extra_wishlist_id"
    const val EXTRA_SELECTED_DATE = "extra_selected_date"
    const val EXTRA_PARSED_ITEMS_JSON = "extra_parsed_items_json"
    const val EXTRA_GRAND_TOTAL = "extra_grand_total"
    const val EXTRA_HAS_VOICE = "extra_has_voice"
    const val EXTRA_PERIOD = "extra_period"

    // Default category IDs (stable seeded values)
    const val CAT_PRIBADI = "cat_pribadi"
    const val CAT_RUMAH = "cat_rumah"
    const val CAT_BISNIS = "cat_bisnis"
    const val CAT_PENDIDIKAN = "cat_pendidikan"
}
