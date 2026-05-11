package com.lenilestari.aethersea.budget

import com.google.firebase.Timestamp
import com.lenilestari.aethersea.data.model.MonthlyBudget
import com.lenilestari.aethersea.data.model.MonthSummary
import com.lenilestari.aethersea.data.repository.BudgetSourceRepository
import com.lenilestari.aethersea.data.repository.MonthlyBudgetRepository
import com.lenilestari.aethersea.data.repository.SessionRepository
import com.lenilestari.aethersea.util.AppLogger
import java.util.Calendar
import kotlin.math.roundToLong

class BudgetCalculator(
    private val budgetSourceRepo: BudgetSourceRepository,
    private val sessionRepo: SessionRepository,
    private val monthlyBudgetRepo: MonthlyBudgetRepository
) {
    private companion object { const val TAG = "BudgetCalculator" }

    suspend fun rolloverPreviousMonths() {
        val currentPeriod = getCurrentPeriod()
        AppLogger.d(TAG, "rollover: currentPeriod=$currentPeriod → getOpenMonths")
        val openMonths = monthlyBudgetRepo.getOpenMonths()
        AppLogger.d(TAG, "rollover: openMonths=${openMonths.size}")

        for (month in openMonths.sortedBy { it.period }) {
            if (month.period >= currentPeriod) continue

            val (start, end) = getMonthBoundaries(month.period)
            val spending = sessionRepo.getSessionsInRange(start, end)
                .sumOf { it.grandTotal }.roundToLong()
            val sources = budgetSourceRepo.getByPeriod(month.period).sumOf { it.amount }
            val carryOver = month.carryOverFromPrevious
            val totalBudget = sources + carryOver
            val left = totalBudget - spending

            val closeResult = monthlyBudgetRepo.update(month.copy(
                totalSourcesAmount = sources,
                totalSpending = spending,
                totalBudget = totalBudget,
                leftAmount = left,
                isClosed = true,
                carriedToNext = left,
                closedAt = Timestamp.now()
            ))
            if (closeResult.isFailure) {
                AppLogger.w(TAG, "rollover: gagal close ${month.period}, skip carry-over")
                continue
            }

            val nextPeriod = getNextPeriod(month.period)
            val nextMonth = monthlyBudgetRepo.getOrCreate(nextPeriod)
            monthlyBudgetRepo.update(nextMonth.copy(
                carryOverFromPrevious = nextMonth.carryOverFromPrevious + left
            ))
        }

        ensureCurrentMonthExists()
    }

    suspend fun ensureCurrentMonthExists() {
        val period = getCurrentPeriod()
        AppLogger.d(TAG, "ensureCurrent: get($period)")
        val existing = monthlyBudgetRepo.get(period)
        if (existing == null) {
            AppLogger.d(TAG, "ensureCurrent: not found → creating")
            val (start, end) = getMonthBoundaries(period)
            val result = monthlyBudgetRepo.create(MonthlyBudget(
                period = period,
                startDate = start,
                endDate = end,
                isClosed = false
            ))
            AppLogger.d(TAG, "ensureCurrent: create=${if (result.isSuccess) "OK" else "FAIL: ${result.exceptionOrNull()?.message}"}")
        } else {
            AppLogger.d(TAG, "ensureCurrent: already exists period=${existing.period}")
        }
    }

    suspend fun recalculateCurrentMonth() {
        val period = getCurrentPeriod()
        AppLogger.d(TAG, "recalculate: period=$period")
        val (start, end) = getMonthBoundaries(period)
        val sources = budgetSourceRepo.getByPeriod(period).sumOf { it.amount }
        val spending = sessionRepo.getSessionsInRange(start, end)
            .sumOf { it.grandTotal }.roundToLong()
        AppLogger.d(TAG, "recalculate: sources=$sources spending=$spending")
        val month = monthlyBudgetRepo.getOrCreate(period)
        val carryOver = month.carryOverFromPrevious
        val totalBudget = sources + carryOver
        val left = totalBudget - spending

        val result = monthlyBudgetRepo.update(month.copy(
            totalSourcesAmount = sources,
            totalSpending = spending,
            totalBudget = totalBudget,
            leftAmount = left,
            startDate = month.startDate ?: start,
            endDate = month.endDate ?: end
        ))
        AppLogger.d(TAG, "recalculate: update=${if (result.isSuccess) "OK" else "FAIL: ${result.exceptionOrNull()?.message}"}")
    }

    suspend fun recalculateForPeriod(period: String) {
        AppLogger.d(TAG, "recalculateForPeriod: period=$period")
        val (start, end) = getMonthBoundaries(period)
        val sources = budgetSourceRepo.getByPeriod(period).sumOf { it.amount }
        val spending = sessionRepo.getSessionsInRange(start, end)
            .sumOf { it.grandTotal }.roundToLong()
        val month = monthlyBudgetRepo.getOrCreate(period)
        val totalBudget = sources + month.carryOverFromPrevious
        monthlyBudgetRepo.update(month.copy(
            totalSourcesAmount = sources,
            totalSpending = spending,
            totalBudget = totalBudget,
            leftAmount = totalBudget - spending,
            startDate = month.startDate ?: start,
            endDate = month.endDate ?: end
        ))
    }

    suspend fun getCurrentMonthSummary(): MonthSummary {
        val period = getCurrentPeriod()
        AppLogger.d(TAG, "getCurrentMonthSummary: period=$period")
        val month = monthlyBudgetRepo.get(period) ?: return MonthSummary(period = period)
        val sourcesCount = budgetSourceRepo.getByPeriod(period).size
        AppLogger.d(TAG, "getCurrentMonthSummary: budget=${month.totalBudget} spending=${month.totalSpending}")
        return MonthSummary(
            period = period,
            totalBudget = month.totalBudget,
            totalSpending = month.totalSpending,
            leftAmount = month.leftAmount,
            carryOverFromPrevious = month.carryOverFromPrevious,
            sourcesCount = sourcesCount
        )
    }

    suspend fun getSummaryForPeriod(period: String): MonthSummary {
        val month = monthlyBudgetRepo.get(period) ?: return MonthSummary(period = period)
        val sourcesCount = budgetSourceRepo.getByPeriod(period).size
        return MonthSummary(
            period = period,
            totalBudget = month.totalBudget,
            totalSpending = month.totalSpending,
            leftAmount = month.leftAmount,
            carryOverFromPrevious = month.carryOverFromPrevious,
            sourcesCount = sourcesCount
        )
    }

    fun getCurrentPeriod(): String {
        val cal = Calendar.getInstance()
        return String.format("%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
    }

    fun getNextPeriod(period: String): String {
        val (year, month) = period.split("-").map { it.toInt() }
        return if (month == 12) String.format("%04d-01", year + 1)
        else String.format("%04d-%02d", year, month + 1)
    }

    fun getPreviousPeriod(period: String): String {
        val (year, month) = period.split("-").map { it.toInt() }
        return if (month == 1) String.format("%04d-12", year - 1)
        else String.format("%04d-%02d", year, month - 1)
    }

    fun getMonthBoundaries(period: String): Pair<Timestamp, Timestamp> {
        val (year, month) = period.split("-").map { it.toInt() }
        val cal = Calendar.getInstance().apply {
            set(year, month - 1, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = Timestamp(cal.time)
        cal.apply {
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
        }
        return Pair(start, Timestamp(cal.time))
    }

    fun formatPeriodDisplay(period: String): String {
        val (year, month) = period.split("-").map { it.toInt() }
        val monthNames = arrayOf("Januari", "Februari", "Maret", "April", "Mei", "Juni",
            "Juli", "Agustus", "September", "Oktober", "November", "Desember")
        return "${monthNames[month - 1]} $year"
    }

    fun periodFromTimestamp(ts: Timestamp): String {
        val cal = Calendar.getInstance().apply { time = ts.toDate() }
        return String.format("%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
    }
}
