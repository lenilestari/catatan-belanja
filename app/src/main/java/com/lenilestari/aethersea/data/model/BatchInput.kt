package com.lenilestari.aethersea.data.model

import java.io.Serializable

data class BatchInput(
    val id: String = System.currentTimeMillis().toString(),
    val rawText: String = "",
    val source: String = "voice",
    val parsedItem: ShoppingItem? = null
) : Serializable
