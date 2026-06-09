package com.lenilestari.aethersea.export

import com.lenilestari.aethersea.data.model.MonthlyBudget
import com.lenilestari.aethersea.data.model.ShoppingSession
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Mengagregasi data mentah (sessions + monthlyBudgets) menjadi FinancialSummaryReport.
 * Rule-based AI insights tanpa network call.
 */
object ReportAggregator {

    private val dayFmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    fun aggregate(
        sessions: List<ShoppingSession>,
        monthlyBudgets: List<MonthlyBudget>,
        userName: String
    ): FinancialSummaryReport {
        val now = Date()

        // Period display
        val sorted = monthlyBudgets.sortedBy { it.period }
        val periodStr = when (sorted.size) {
            0    -> "—"
            1    -> formatPeriodDisplay(sorted.first().period)
            else -> "${formatPeriodDisplay(sorted.first().period)} – ${formatPeriodDisplay(sorted.last().period)}"
        }

        // Totals dari monthlyBudgets (authoritative, sudah dihitung server)
        val totalBudget   = monthlyBudgets.sumOf { it.totalBudget }
        val totalSpending = monthlyBudgets.sumOf { it.totalSpending }
        val remaining     = totalBudget - totalSpending
        val usagePct      = if (totalBudget > 0) totalSpending.toDouble() / totalBudget * 100 else 0.0

        // Daily analysis dari sessions
        val dailyMap = mutableMapOf<String, MutableList<ShoppingSession>>()
        sessions.forEach { s ->
            val key = dayFmt.format(s.date.toDate())
            dailyMap.getOrPut(key) { mutableListOf() }.add(s)
        }
        val dailyTotals = dailyMap.mapValues { (_, v) -> v.sumOf { it.grandTotal } }
        val highestDay = dailyTotals.maxByOrNull { it.value }
            ?.let { DaySpending(it.key, it.value, dailyMap[it.key]?.size ?: 0) }
        val lowestDay = dailyTotals.filter { it.value > 0 }.minByOrNull { it.value }
            ?.let { DaySpending(it.key, it.value, dailyMap[it.key]?.size ?: 0) }

        // Avg daily dari hari aktif belanja
        val activeDays = dailyTotals.size.coerceAtLeast(1)
        val avgDaily   = totalSpending.toDouble() / activeDays

        // Category breakdown dari sessions
        val catGroups = sessions.groupBy { it.displayCategory.ifBlank { "Lainnya" } }
        val catTotal  = sessions.sumOf { it.grandTotal }.coerceAtLeast(1.0)
        val categoryBreakdown = catGroups.map { (cat, sess) ->
            val t = sess.sumOf { it.grandTotal }
            CategorySummary(
                name         = cat,
                total        = t,
                sessionCount = sess.size,
                percent      = t / catTotal * 100
            )
        }.sortedByDescending { it.total }

        // Monthly breakdown — per period, join sessions + budget
        val sessionsByPeriod = sessions.groupBy { it.period }
        val monthlyBreakdown = monthlyBudgets.sortedByDescending { it.period }.map { mb ->
            val periodSessions = sessionsByPeriod[mb.period] ?: emptyList()
            val periodTopCat   = periodSessions
                .groupBy { it.displayCategory.ifBlank { "Lainnya" } }
                .maxByOrNull { (_, v) -> v.sumOf { it.grandTotal } }?.key ?: "—"
            val daysInMonth  = daysInPeriod(mb.period)
            val periodAvg    = if (daysInMonth > 0) mb.totalSpending.toDouble() / daysInMonth else 0.0
            val periodUsage  = if (mb.totalBudget > 0) mb.totalSpending.toDouble() / mb.totalBudget * 100 else 0.0
            MonthlyBreakdown(
                period        = mb.period,
                displayPeriod = formatPeriodDisplay(mb.period),
                budget        = mb.totalBudget,
                spending      = mb.totalSpending,
                remaining     = mb.leftAmount,
                avgDaily      = periodAvg,
                sessionCount  = periodSessions.size,
                topCategory   = periodTopCat,
                usagePercent  = periodUsage
            )
        }

        val insights = generateInsights(
            totalBudget, totalSpending, remaining, usagePct,
            avgDaily, monthlyBreakdown, categoryBreakdown, sessions.size
        )

        return FinancialSummaryReport(
            userName            = userName.ifBlank { "Pengguna" },
            generatedAt         = now,
            reportPeriod        = periodStr,
            totalBudget         = totalBudget,
            totalSpending       = totalSpending,
            remainingBudget     = remaining,
            avgDailySpending    = avgDaily,
            totalSessions       = sessions.size,
            budgetUsagePercent  = usagePct,
            highestSpendingDay  = highestDay,
            lowestSpendingDay   = lowestDay,
            categoryBreakdown   = categoryBreakdown,
            monthlyBreakdown    = monthlyBreakdown,
            insights            = insights
        )
    }

    // ── Insight generation ──────────────────────────────────────────────────────

    private fun generateInsights(
        totalBudget: Long,
        totalSpending: Long,
        remaining: Long,
        usagePct: Double,
        avgDaily: Double,
        monthly: List<MonthlyBreakdown>,
        categories: List<CategorySummary>,
        sessionCount: Int
    ): List<String> {
        val out = mutableListOf<String>()

        // 1. Budget usage overall
        when {
            totalBudget == 0L -> out.add("ℹ️  Belum ada data budget yang tercatat untuk periode ini.")
            usagePct >= 110   -> out.add("🔴 PERINGATAN: Pengeluaran melebihi budget ${String.format("%.1f", usagePct - 100)}%! Perlu evaluasi segera.")
            usagePct >= 100   -> out.add("⚠️  Budget tepat habis — pengeluaran menyentuh 100% dari yang direncanakan.")
            usagePct >= 90    -> out.add("⚠️  Budget hampir habis — terpakai ${String.format("%.1f", usagePct)}%. Batasi pengeluaran non-esensial.")
            usagePct >= 75    -> out.add("💡 Budget terpakai ${String.format("%.1f", usagePct)}% — pengeluaran dalam batas wajar, tetap perhatikan sisa bulan.")
            usagePct >= 50    -> out.add("✅ Budget terpakai ${String.format("%.1f", usagePct)}% — pengeluaran terkendali dengan baik.")
            usagePct > 0      -> out.add("✅ Pengeluaran sangat efisien — budget baru terpakai ${String.format("%.1f", usagePct)}%.")
        }

        // 2. Top category
        if (categories.isNotEmpty()) {
            val top = categories.first()
            out.add("🛒 Pengeluaran terbesar ada pada kategori \"${top.name}\" — ${String.format("%.1f", top.percent)}% dari total belanja (${formatRp(top.total.toLong())}).")
        }

        // 3. Month-over-month trend (pakai 2 bulan terakhir)
        if (monthly.size >= 2) {
            val latest = monthly.first()
            val prev   = monthly[1]
            if (prev.spending > 0 && latest.spending > 0) {
                val changePct = (latest.spending - prev.spending).toDouble() / prev.spending * 100
                when {
                    changePct > 25  -> out.add("📈 Pengeluaran ${latest.displayPeriod} naik tajam ${String.format("%.1f", changePct)}% vs ${prev.displayPeriod}. Perlu diwaspadai.")
                    changePct > 10  -> out.add("📊 Pengeluaran ${latest.displayPeriod} meningkat ${String.format("%.1f", changePct)}% dibanding ${prev.displayPeriod}.")
                    changePct < -15 -> out.add("📉 Efisiensi meningkat — pengeluaran ${latest.displayPeriod} turun ${String.format("%.1f", -changePct)}% vs ${prev.displayPeriod}.")
                    changePct < -5  -> out.add("📉 Pengeluaran ${latest.displayPeriod} sedikit menurun ${String.format("%.1f", -changePct)}% dibanding ${prev.displayPeriod}.")
                    else            -> out.add("📊 Pengeluaran relatif stabil — perubahan ${String.format("%.1f", changePct)}% vs bulan sebelumnya.")
                }
            }
        }

        // 4. Shopping frequency
        if (sessionCount > 0) {
            val activeMonths = monthly.count { it.sessionCount > 0 }.coerceAtLeast(1)
            val avgPerMonth  = sessionCount.toDouble() / activeMonths
            out.add("🗓️  Rata-rata ${String.format("%.1f", avgPerMonth)} sesi belanja per bulan dari total $sessionCount sesi tercatat.")
        }

        // 5. Remaining budget
        when {
            remaining > 0  -> out.add("💰 Sisa budget: ${formatRp(remaining)} — bisa dioptimalkan atau dialokasikan ke bulan berikutnya.")
            remaining < 0  -> out.add("🔴 Defisit anggaran: ${formatRp(-remaining)} — pengeluaran melebihi semua sumber dana yang masuk.")
            remaining == 0L -> out.add("⚖️  Budget habis tepat — tidak ada sisa maupun kekurangan.")
        }

        // 6. Overspent months
        val overspent = monthly.filter { it.isOverBudget }
        if (overspent.isNotEmpty()) {
            val names = overspent.take(3).joinToString(", ") { it.displayPeriod }
            out.add("⚠️  Bulan yang melebihi budget: $names.")
        }

        // 7. Category diversity
        if (categories.size >= 3) {
            out.add("📂 Pengeluaran tersebar di ${categories.size} kategori — menunjukkan keberagaman kebutuhan.")
        } else if (categories.size == 1) {
            out.add("📂 Seluruh pengeluaran hanya ada di 1 kategori — konsumsi sangat terfokus.")
        }

        return out
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    fun formatRp(amount: Long): String {
        if (amount == 0L) return "Rp 0"
        val isNeg = amount < 0
        val abs   = if (isNeg) -amount else amount
        val s     = String.format("%,d", abs).replace(",", ".")
        return "${if (isNeg) "-" else ""}Rp $s"
    }

    fun formatPeriodDisplay(period: String): String = try {
        val (year, month) = period.split("-").map { it.toInt() }
        val months = arrayOf("Januari","Februari","Maret","April","Mei","Juni",
            "Juli","Agustus","September","Oktober","November","Desember")
        "${months[month - 1]} $year"
    } catch (_: Exception) { period }

    private fun daysInPeriod(period: String): Int = try {
        val (year, month) = period.split("-").map { it.toInt() }
        Calendar.getInstance().apply { set(year, month - 1, 1) }
            .getActualMaximum(Calendar.DAY_OF_MONTH)
    } catch (_: Exception) { 30 }
}
