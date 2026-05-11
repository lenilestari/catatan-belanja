package com.lenilestari.aethersea.data.model

import com.google.firebase.Timestamp
import java.io.Serializable

data class Wishlist(
    val id: String = "",
    val name: String = "",
    val targetPrice: Double = 0.0,
    val savedAmount: Double = 0.0,
    val monthlyTarget: Double = 0.0,
    val isCompleted: Boolean = false,
    val completedAt: Timestamp? = null,
    val createdAt: Timestamp = Timestamp(0, 0),
    val updatedAt: Timestamp = Timestamp(0, 0)
) : Serializable {
    val remaining: Double get() = (targetPrice - savedAmount).coerceAtLeast(0.0)
    val progressPercent: Int get() = if (targetPrice > 0) ((savedAmount / targetPrice) * 100).toInt().coerceIn(0, 100) else 0
    val monthsRemaining: Int get() = if (monthlyTarget > 0 && remaining > 0) kotlin.math.ceil(remaining / monthlyTarget).toInt() else 0
}
