package com.lenilestari.aethersea.data.model

import com.google.firebase.Timestamp
import java.io.Serializable

data class MonthlyBudget(
    val period: String = "",
    val startDate: Timestamp? = null,
    val endDate: Timestamp? = null,
    val totalSourcesAmount: Long = 0L,
    val carryOverFromPrevious: Long = 0L,
    val totalBudget: Long = 0L,
    val totalSpending: Long = 0L,
    val leftAmount: Long = 0L,
    val isClosed: Boolean = false,
    val carriedToNext: Long = 0L,
    val closedAt: Timestamp? = null
) : Serializable

data class MonthSummary(
    val period: String = "",
    val totalBudget: Long = 0L,
    val totalSpending: Long = 0L,
    val leftAmount: Long = 0L,
    val carryOverFromPrevious: Long = 0L,
    val sourcesCount: Int = 0
)
