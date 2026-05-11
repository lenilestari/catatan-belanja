package com.lenilestari.aethersea.data.model

data class KamusItem(
    val id: String = "",
    val name: String = "",
    val aliases: List<String> = emptyList(),
    val commonUnits: List<String> = emptyList(),
    val category: String = ""
)
