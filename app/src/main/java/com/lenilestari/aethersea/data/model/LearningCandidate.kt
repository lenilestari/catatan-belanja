package com.lenilestari.aethersea.data.model

import com.google.firebase.Timestamp

data class LearningCandidate(
    val id: String = "",
    val type: String = "",             // "new_item" | "new_alias" | "new_unit"
    val itemName: String = "",
    val alias: String = "",
    val category: String = "",
    val commonUnits: List<String> = emptyList(),
    val originalUnit: String = "",
    val normalizedUnit: String = "",
    val count: Int = 1,
    val confidence: Double = 0.0,
    val lastSeenMs: Long = 0L,
    val createdAt: Timestamp = Timestamp(0, 0),
    val updatedAt: Timestamp = Timestamp(0, 0)
)

// Gemini learning response
data class LearningAnalysis(
    val newItems: List<NewItemSuggestion> = emptyList(),
    val newAliases: List<NewAliasSuggestion> = emptyList(),
    val newUnits: List<NewUnitSuggestion> = emptyList()
)

data class NewItemSuggestion(
    val name: String = "",
    val category: String = "",
    val aliases: List<String> = emptyList(),
    val commonUnits: List<String> = emptyList()
)

data class NewAliasSuggestion(
    val itemName: String = "",
    val alias: String = ""
)

data class NewUnitSuggestion(
    val original: String = "",
    val normalized: String = ""
)
