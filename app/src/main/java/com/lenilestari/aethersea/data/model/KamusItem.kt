package com.lenilestari.aethersea.data.model

import com.google.firebase.Timestamp

data class KamusItem(
    val id: String = "",
    val name: String = "",
    val aliases: List<String> = emptyList(),
    val commonUnits: List<String> = emptyList(),
    val category: String = "",
    val searchKeywords: List<String> = emptyList(),
    val isActive: Boolean = true,
    val version: String = "",
    val createdAt: Timestamp = Timestamp(0, 0),
    val updatedAt: Timestamp = Timestamp(0, 0)
)
