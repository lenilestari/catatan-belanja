package com.lenilestari.aethersea.export

import java.util.Date

/**
 * Data models untuk Monthly Financial Summary Report.
 * Dihasilkan oleh ReportAggregator dari raw Firestore data.
 */

data class FinancialSummaryReport(
    val userName: String,
    val generatedAt: Date,
    val reportPeriod: String,

    // Core KPIs
    val totalBudget: Long,
    val totalSpending: Long,
    val remainingBudget: Long,
    val avgDailySpending: Double,
    val totalSessions: Int,
    val budgetUsagePercent: Double,

    // Daily extremes
    val highestSpendingDay: DaySpending?,
    val lowestSpendingDay: DaySpending?,

    // Category analysis
    val categoryBreakdown: List<CategorySummary>,

    // Monthly breakdown
    val monthlyBreakdown: List<MonthlyBreakdown>,

    // AI-generated insights
    val insights: List<String>
) {
    val topCategory: String get() = categoryBreakdown.firstOrNull()?.name ?: "—"
    val topCategoryPercent: Double get() = categoryBreakdown.firstOrNull()?.percent ?: 0.0
    val isOverBudget: Boolean get() = totalSpending > totalBudget && totalBudget > 0
}

data class DaySpending(
    val date: String,
    val total: Double,
    val sessionCount: Int
)

data class CategorySummary(
    val name: String,
    val total: Double,
    val sessionCount: Int,
    val percent: Double
)

data class MonthlyBreakdown(
    val period: String,
    val displayPeriod: String,
    val budget: Long,
    val spending: Long,
    val remaining: Long,
    val avgDaily: Double,
    val sessionCount: Int,
    val topCategory: String,
    val usagePercent: Double
) {
    val isOverBudget: Boolean get() = spending > budget && budget > 0
    val statusLabel: String get() = when {
        budget == 0L     -> "—"
        usagePercent >= 100 -> "OVER"
        usagePercent >= 90  -> "KRITIS"
        usagePercent >= 75  -> "NORMAL"
        else                -> "AMAN"
    }
}
