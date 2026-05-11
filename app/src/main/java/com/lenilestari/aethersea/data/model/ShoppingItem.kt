package com.lenilestari.aethersea.data.model

import java.io.Serializable

data class ShoppingItem(
    val item: String = "",
    val qty: Double = 1.0,
    val unit: String = "buah",
    val price: Double = 0.0,
    val total: Double = 0.0,
    val source: String = "manual",
    val confidence: String = "high"
) : Serializable
