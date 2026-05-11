package com.lenilestari.aethersea.budget

import com.google.firebase.Timestamp
import com.lenilestari.aethersea.data.model.MonthlyBudget
import com.lenilestari.aethersea.data.model.MonthSummary
import com.lenilestari.aethersea.data.repository.BudgetSourceRepository
import com.lenilestari.aethersea.data.repository.MonthlyBudgetRepository
import com.lenilestari.aethersea.data.repository.SessionRepository
import java.util.Calendar
import kotlin.math.roundToLong

class BudgetCalculator(
    private val budgetSourceRepo: BudgetSourceRepository,
    private val sessionRepo: SessionRepository,
    private val monthlyBudgetRepo: MonthlyBudgetRepository
) {
    suspend fun rolloverPreviousMonths() {
        val currentPeriod = getCurrentPeriod()
        android.util.Log.d("DBG_AETHER", "   rollover: currentPeriod=$currentPeriod → getOpenMonths...")
        val openMonths = monthlyBudgetRepo.getOpenMonths()
        android.util.Log.d("DBG_AETHER", "   rollover: openMonths=${openMonths.size}")

        for (month in openMonths.sortedBy { it.period }) {
            if (month.period >= currentPeriod) continue

            val (start, end) = getMonthBoundaries(month.period)
            val spending = sessionRepo.getSessionsInRange(start, end)
                .sumOf { it.grandTotal }.roundToLong()
            val sources = budgetSourceRepo.getByPeriod(month.period).sumOf { it.amount }
            val carryOver = month.carryOverFromPrevious
            val totalBudget = sources + carryOver
            val left = totalBudget - spending

            // Jika close gagal (timeout), jangan propagate carry-over — hindari double carry-over
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
                android.util.Log.w("DBG_AETHER", "   rollover: gagal close ${month.period}, skip carry-over")
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
        android.util.Log.d("DBG_AETHER", "   ensureCurrent: get($period)...")
        val existing = monthlyBudgetRepo.get(period)
        android.util.Log.d("DBG_AETHER", "   ensureCurrent: existing=${existing?.period ?: "NULL"}")
        if (existing == null) {
            android.util.Log.d("DBG_AETHER", "   ensureCurrent: create new month...")
            val (start, end) = getMonthBoundaries(period)
            val result = monthlyBudgetRepo.create(MonthlyBudget(
                period = period,
                startDate = start,
                endDate = end,
                isClosed = false
            ))
            android.util.Log.d("DBG_AETHER", "   ensureCurrent: create=${if (result.isSuccess) "OK" else "FAIL: ${result.exceptionOrNull()?.message}"}")
        }
    }

    suspend fun recalculateCurrentMonth() {
        val period = getCurrentPeriod()
        android.util.Log.d("DBG_AETHER", "   recalculate: period=$period")
        val (start, end) = getMonthBoundaries(period)
        android.util.Log.d("DBG_AETHER", "   recalculate: getByPeriod start")
        val sources = budgetSourceRepo.getByPeriod(period).sumOf { it.amount }
        android.util.Log.d("DBG_AETHER", "   recalculate: sources=$sources → getSessionsInRange start")
        val spending = sessionRepo.getSessionsInRange(start, end)
            .sumOf { it.grandTotal }.roundToLong()
        android.util.Log.d("DBG_AETHER", "   recalculate: spending=$spending → getOrCreate start")
        val month = monthlyBudgetRepo.getOrCreate(period)
        android.util.Log.d("DBG_AETHER", "   recalculate: getOrCreate done → update start")
        val carryOver = month.carryOverFromPrevious
        val totalBudget = sources + carryOver
        val left = totalBudget - spending

        val updateResult = monthlyBudgetRepo.update(month.copy(
            totalSourcesAmount = sources,
            totalSpending = spending,
            totalBudget = totalBudget,
            leftAmount = left,
            startDate = month.startDate ?: start,
            endDate = month.endDate ?: end
        ))
        android.util.Log.d("DBG_AETHER", "   recalculate: update result=${if (updateResult.isSuccess) "OK" else "FAIL: ${updateResult.exceptionOrNull()?.message}"}")
    }

    suspend fun recalculateForPeriod(period: String) {
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
        // READ ONLY — tidak boleh write saat UI load
        // recalculateCurrentMonth() dipanggil hanya setelah save/delete session
        val period = getCurrentPeriod()
        android.util.Log.d("DBG_AETHER", "   getCurrentMonthSummary: read only, period=$period")
        val month = monthlyBudgetRepo.get(period) ?: return MonthSummary(period = period)
        val sourcesCount = budgetSourceRepo.getByPeriod(period).size
        android.util.Log.d("DBG_AETHER", "   getCurrentMonthSummary: done budget=${month.totalBudget}")
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
        val cal = Calendar.getInstance().apply { set(year, month - 1, 1) }
        val monthNames = arrayOf("Januari", "Februari", "Maret", "April", "Mei", "Juni",
            "Juli", "Agustus", "September", "Oktober", "November", "Desember")
        return "${monthNames[month - 1]} $year"
    }

    fun periodFromTimestamp(ts: Timestamp): String {
        val cal = Calendar.getInstance().apply { time = ts.toDate() }
        return String.format("%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
    }
}
