package com.lenilestari.aethersea.export

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import com.lenilestari.aethersea.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.dhatim.fastexcel.Workbook
import org.dhatim.fastexcel.Worksheet
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Premium financial summary report exporter.
 * Dark fintech aesthetic — navy + teal + green/red KPI colors.
 *
 * Sheets:
 *   1. Ringkasan Finansial — KPIs + trend + insights
 *   2. Per Bulan           — monthly breakdown table
 *   3. Per Kategori        — category analysis table
 *   4. Hari Tertinggi      — top 10 spending days
 */
object SummaryReportExporter {

    // ── Color Palette ──────────────────────────────────────────────────────────
    private const val DARK_NAVY  = "1A1A2E"   // primary header bg
    private const val NAVY       = "16213E"   // section header bg
    private const val MID_NAVY   = "0F3460"   // table header bg
    private const val TEAL       = "00B4D8"   // accent / highlight values
    private const val GREEN      = "27AE60"   // positive
    private const val RED        = "E74C3C"   // negative / over-budget
    private const val ORANGE     = "E67E22"   // warning (75-90%)
    private const val WHITE      = "FFFFFF"
    private const val LIGHT      = "F0F4F8"   // alternating row bg
    private const val TEXT_MAIN  = "212529"   // dark text on white bg
    private const val TEXT_SEC   = "6C757D"   // secondary text
    private const val GAP_COLOR  = "E9ECEF"   // separator columns

    private val fileFmt = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault())
    private val genFmt  = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))
    private val dayFmt  = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    // ── Public entry point ─────────────────────────────────────────────────────

    suspend fun export(context: Context, report: FinancialSummaryReport): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val filename = "Aethersea_Report_${fileFmt.format(report.generatedAt)}.xlsx"
                val cv = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, filename)
                    put(MediaStore.Downloads.MIME_TYPE,
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    put(MediaStore.Downloads.RELATIVE_PATH,
                        "${Environment.DIRECTORY_DOWNLOADS}/${Constants.EXPORT_FOLDER_NAME}")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
                    ?: return@withContext false

                resolver.openOutputStream(uri)?.buffered()?.use { stream ->
                    val wb = Workbook(stream, "Aethersea Financial Report", "1.0")
                    writeRingkasan(wb, report)
                    writePerBulan(wb, report)
                    writePerKategori(wb, report)
                    writeHariTertinggi(wb, report)
                    wb.finish()
                }

                cv.clear(); cv.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, cv, null, null)
                true
            } catch (_: Exception) { false }
        }

    // ── Sheet 1: Ringkasan Finansial ───────────────────────────────────────────

    private fun writeRingkasan(wb: Workbook, r: FinancialSummaryReport) {
        val ws = wb.newWorksheet("Ringkasan Finansial")

        // Column widths — layout: [A label | B value | C gap | D label | E value | F margin]
        ws.width(0, 30.0)   // A — label
        ws.width(1, 22.0)   // B — value
        ws.width(2,  3.0)   // C — gap
        ws.width(3, 30.0)   // D — label
        ws.width(4, 22.0)   // E — value
        ws.width(5,  3.0)   // F — right margin

        var row = 0

        // ── App header ─────────────────────────────────────────────────────────
        row = writeAppHeader(ws, row, r)
        row++  // spacer

        // ── KPI Section ────────────────────────────────────────────────────────
        row = writeSectionHeader(ws, row, "  RINGKASAN KEUANGAN", cols = 0..5)

        val kpis = buildKpiRows(r)
        kpis.forEachIndexed { i, kpi ->
            val bg = if (i % 2 == 0) LIGHT else WHITE
            // Left KPI
            ws.value(row, 0, kpi.labelL)
            ws.style(row, 0).fontSize(10).fontColor(TEXT_MAIN).fillColor(bg).set()
            ws.value(row, 1, kpi.valueL)
            ws.style(row, 1).bold().fontSize(11).fontColor(kpi.colorL).fillColor(bg)
                .horizontalAlignment("right").set()
            // Gap
            cell(ws, row, 2, "", GAP_COLOR)
            // Right KPI
            ws.value(row, 3, kpi.labelR)
            ws.style(row, 3).fontSize(10).fontColor(TEXT_MAIN).fillColor(bg).set()
            ws.value(row, 4, kpi.valueR)
            ws.style(row, 4).bold().fontSize(11).fontColor(kpi.colorR).fillColor(bg)
                .horizontalAlignment("right").set()
            cell(ws, row, 5, "", bg)
            row++
        }

        row++  // spacer

        // ── Spending Trend ─────────────────────────────────────────────────────
        if (r.monthlyBreakdown.isNotEmpty()) {
            row = writeSectionHeader(ws, row, "  TREND PENGELUARAN BULANAN", cols = 0..5)

            // Sub-header
            ws.value(row, 0, "Bulan"); ws.style(row, 0).bold().fontSize(9).fontColor(TEXT_SEC).fillColor(WHITE).set()
            ws.value(row, 1, "Grafik (vs Maks)"); ws.style(row, 1).bold().fontSize(9).fontColor(TEXT_SEC).fillColor(WHITE).set()
            cell(ws, row, 2, "", WHITE)
            ws.value(row, 3, "Pengeluaran"); ws.style(row, 3).bold().fontSize(9).fontColor(TEXT_SEC).fillColor(WHITE).set()
            ws.value(row, 4, "Status"); ws.style(row, 4).bold().fontSize(9).fontColor(TEXT_SEC).fillColor(WHITE).horizontalAlignment("center").set()
            cell(ws, row, 5, "", WHITE)
            row++

            val maxSpend = r.monthlyBreakdown.maxOfOrNull { it.spending }?.coerceAtLeast(1L) ?: 1L
            val BAR_MAX  = 18

            r.monthlyBreakdown.sortedBy { it.period }.forEachIndexed { i, mb ->
                val bg      = if (i % 2 == 0) WHITE else LIGHT
                val barLen  = ((mb.spending.toDouble() / maxSpend) * BAR_MAX).toInt().coerceIn(0, BAR_MAX)
                val bar     = "█".repeat(barLen) + "░".repeat(BAR_MAX - barLen)
                val barCol  = kpiColor(mb.usagePercent)
                val statusCol = kpiColor(mb.usagePercent)

                ws.value(row, 0, mb.displayPeriod)
                ws.style(row, 0).fontSize(10).fontColor(TEXT_MAIN).fillColor(bg).set()
                ws.value(row, 1, bar)
                ws.style(row, 1).fontSize(9).fontColor(barCol).fillColor(bg).set()
                cell(ws, row, 2, "", GAP_COLOR)
                ws.value(row, 3, ReportAggregator.formatRp(mb.spending))
                ws.style(row, 3).fontSize(10).fontColor(TEXT_MAIN).fillColor(bg).horizontalAlignment("right").set()
                ws.value(row, 4, "${String.format("%.1f", mb.usagePercent)}%  ${mb.statusLabel}")
                ws.style(row, 4).bold().fontSize(10).fontColor(statusCol).fillColor(bg).horizontalAlignment("center").set()
                cell(ws, row, 5, "", bg)
                row++
            }

            row++  // spacer
        }

        // ── AI Insights ────────────────────────────────────────────────────────
        if (r.insights.isNotEmpty()) {
            row = writeSectionHeader(ws, row, "  AI INSIGHTS", cols = 0..5)

            r.insights.forEachIndexed { i, insight ->
                val bg = if (i % 2 == 0) WHITE else LIGHT
                ws.value(row, 0, insight)
                ws.style(row, 0).fontSize(10).fontColor(TEXT_MAIN).fillColor(bg).wrapText(true).set()
                for (c in 1..5) cell(ws, row, c, "", bg)
                row++
            }
        }
    }

    // ── Sheet 2: Per Bulan ─────────────────────────────────────────────────────

    private fun writePerBulan(wb: Workbook, r: FinancialSummaryReport) {
        if (r.monthlyBreakdown.isEmpty()) return
        val ws = wb.newWorksheet("Per Bulan")

        // Column widths
        ws.width(0, 20.0)   // Bulan
        ws.width(1, 18.0)   // Budget
        ws.width(2, 18.0)   // Pengeluaran
        ws.width(3, 18.0)   // Sisa
        ws.width(4, 16.0)   // Avg Harian
        ws.width(5,  9.0)   // Sesi
        ws.width(6, 22.0)   // Kategori Utama
        ws.width(7, 12.0)   // % Budget
        ws.width(8, 10.0)   // Status

        var row = 0
        row = writeSheetTitle(ws, row, "BREAKDOWN PER BULAN", r, cols = 8)
        row++

        // Table header
        val headers = arrayOf("Bulan", "Budget", "Pengeluaran", "Sisa", "Avg Harian", "Sesi", "Kategori Utama", "% Budget", "Status")
        headers.forEachIndexed { c, h ->
            ws.value(row, c, h)
            ws.style(row, c).bold().fontSize(10).fontColor(WHITE).fillColor(MID_NAVY)
                .horizontalAlignment(if (c == 0 || c == 6) "left" else "center").set()
        }
        row++

        // Data rows
        r.monthlyBreakdown.sortedByDescending { it.period }.forEachIndexed { i, mb ->
            val bg = if (i % 2 == 0) WHITE else LIGHT
            ws.value(row, 0, mb.displayPeriod)
            ws.style(row, 0).fontSize(10).fontColor(TEXT_MAIN).fillColor(bg).set()

            ws.value(row, 1, mb.budget.toDouble())
            ws.style(row, 1).fontSize(10).fontColor(TEXT_MAIN).fillColor(bg)
                .format("#,##0").horizontalAlignment("right").set()

            ws.value(row, 2, mb.spending.toDouble())
            ws.style(row, 2).fontSize(10).fontColor(if (mb.isOverBudget) RED else TEXT_MAIN).fillColor(bg)
                .format("#,##0").horizontalAlignment("right").set()

            ws.value(row, 3, mb.remaining.toDouble())
            ws.style(row, 3).bold().fontSize(10).fontColor(if (mb.remaining < 0) RED else GREEN).fillColor(bg)
                .format("#,##0").horizontalAlignment("right").set()

            ws.value(row, 4, mb.avgDaily)
            ws.style(row, 4).fontSize(10).fontColor(TEXT_MAIN).fillColor(bg)
                .format("#,##0").horizontalAlignment("right").set()

            ws.value(row, 5, mb.sessionCount.toDouble())
            ws.style(row, 5).fontSize(10).fontColor(TEXT_MAIN).fillColor(bg)
                .horizontalAlignment("center").set()

            ws.value(row, 6, mb.topCategory)
            ws.style(row, 6).fontSize(10).fontColor(TEXT_MAIN).fillColor(bg).set()

            ws.value(row, 7, "${String.format("%.1f", mb.usagePercent)}%")
            ws.style(row, 7).bold().fontSize(10).fontColor(kpiColor(mb.usagePercent)).fillColor(bg)
                .horizontalAlignment("center").set()

            ws.value(row, 8, mb.statusLabel)
            ws.style(row, 8).bold().fontSize(9).fontColor(kpiColor(mb.usagePercent)).fillColor(bg)
                .horizontalAlignment("center").set()
            row++
        }

        // Total row
        row++
        val totalSpend = r.monthlyBreakdown.sumOf { it.spending }
        val totalBudg  = r.monthlyBreakdown.sumOf { it.budget }
        val totalRem   = r.monthlyBreakdown.sumOf { it.remaining }
        val totalSess  = r.monthlyBreakdown.sumOf { it.sessionCount }
        val totalPct   = if (totalBudg > 0) totalSpend.toDouble() / totalBudg * 100 else 0.0

        ws.value(row, 0, "TOTAL")
        ws.style(row, 0).bold().fontSize(10).fontColor(WHITE).fillColor(DARK_NAVY).set()
        ws.value(row, 1, totalBudg.toDouble())
        ws.style(row, 1).bold().fontSize(10).fontColor(WHITE).fillColor(DARK_NAVY)
            .format("#,##0").horizontalAlignment("right").set()
        ws.value(row, 2, totalSpend.toDouble())
        ws.style(row, 2).bold().fontSize(10).fontColor(WHITE).fillColor(DARK_NAVY)
            .format("#,##0").horizontalAlignment("right").set()
        ws.value(row, 3, totalRem.toDouble())
        ws.style(row, 3).bold().fontSize(10).fontColor(if (totalRem < 0) RED else TEAL).fillColor(DARK_NAVY)
            .format("#,##0").horizontalAlignment("right").set()
        ws.value(row, 4, "")
        ws.style(row, 4).fillColor(DARK_NAVY).set()
        ws.value(row, 5, totalSess.toDouble())
        ws.style(row, 5).bold().fontSize(10).fontColor(WHITE).fillColor(DARK_NAVY)
            .horizontalAlignment("center").set()
        ws.value(row, 6, "")
        ws.style(row, 6).fillColor(DARK_NAVY).set()
        ws.value(row, 7, "${String.format("%.1f", totalPct)}%")
        ws.style(row, 7).bold().fontSize(10).fontColor(kpiColor(totalPct)).fillColor(DARK_NAVY)
            .horizontalAlignment("center").set()
        ws.value(row, 8, "")
        ws.style(row, 8).fillColor(DARK_NAVY).set()
    }

    // ── Sheet 3: Per Kategori ──────────────────────────────────────────────────

    private fun writePerKategori(wb: Workbook, r: FinancialSummaryReport) {
        if (r.categoryBreakdown.isEmpty()) return
        val ws = wb.newWorksheet("Per Kategori")

        ws.width(0, 28.0)   // Kategori
        ws.width(1, 20.0)   // Total Pengeluaran
        ws.width(2, 12.0)   // Sesi
        ws.width(3, 14.0)   // % dari Total
        ws.width(4, 20.0)   // Bar visual

        var row = 0
        row = writeSheetTitle(ws, row, "BREAKDOWN PER KATEGORI", r, cols = 4)
        row++

        val headers = arrayOf("Kategori", "Total Pengeluaran", "Sesi", "% Total", "Proporsi")
        headers.forEachIndexed { c, h ->
            ws.value(row, c, h)
            ws.style(row, c).bold().fontSize(10).fontColor(WHITE).fillColor(MID_NAVY)
                .horizontalAlignment(if (c == 0 || c == 4) "left" else "center").set()
        }
        row++

        val BAR_MAX = 20
        r.categoryBreakdown.forEachIndexed { i, cat ->
            val bg     = if (i % 2 == 0) WHITE else LIGHT
            val barLen = ((cat.percent / 100) * BAR_MAX).toInt().coerceIn(0, BAR_MAX)
            val bar    = "▓".repeat(barLen) + "░".repeat(BAR_MAX - barLen)
            val col    = when {
                i == 0   -> TEAL   // top category highlighted
                cat.percent >= 30 -> RED    // dominant but not #1
                cat.percent >= 15 -> ORANGE
                else     -> GREEN
            }

            ws.value(row, 0, cat.name)
            ws.style(row, 0).fontSize(10).fontColor(TEXT_MAIN).fillColor(bg).set()

            ws.value(row, 1, cat.total)
            ws.style(row, 1).fontSize(10).fontColor(TEXT_MAIN).fillColor(bg)
                .format("#,##0").horizontalAlignment("right").set()

            ws.value(row, 2, cat.sessionCount.toDouble())
            ws.style(row, 2).fontSize(10).fontColor(TEXT_MAIN).fillColor(bg)
                .horizontalAlignment("center").set()

            ws.value(row, 3, "${String.format("%.1f", cat.percent)}%")
            ws.style(row, 3).bold().fontSize(10).fontColor(col).fillColor(bg)
                .horizontalAlignment("center").set()

            ws.value(row, 4, bar)
            ws.style(row, 4).fontSize(9).fontColor(col).fillColor(bg).set()
            row++
        }

        // Grand total row
        row++
        val grandTotal = r.categoryBreakdown.sumOf { it.total }
        val grandSess  = r.categoryBreakdown.sumOf { it.sessionCount }

        ws.value(row, 0, "TOTAL")
        ws.style(row, 0).bold().fontSize(10).fontColor(WHITE).fillColor(DARK_NAVY).set()
        ws.value(row, 1, grandTotal)
        ws.style(row, 1).bold().fontSize(10).fontColor(WHITE).fillColor(DARK_NAVY)
            .format("#,##0").horizontalAlignment("right").set()
        ws.value(row, 2, grandSess.toDouble())
        ws.style(row, 2).bold().fontSize(10).fontColor(WHITE).fillColor(DARK_NAVY)
            .horizontalAlignment("center").set()
        ws.value(row, 3, "100%")
        ws.style(row, 3).bold().fontSize(10).fontColor(WHITE).fillColor(DARK_NAVY)
            .horizontalAlignment("center").set()
        ws.value(row, 4, "")
        ws.style(row, 4).fillColor(DARK_NAVY).set()
    }

    // ── Sheet 4: Hari Tertinggi ────────────────────────────────────────────────

    private fun writeHariTertinggi(wb: Workbook, r: FinancialSummaryReport) {
        val ws = wb.newWorksheet("Hari Tertinggi")

        ws.width(0, 16.0)
        ws.width(1, 22.0)
        ws.width(2, 10.0)
        ws.width(3, 20.0)

        var row = 0
        row = writeSheetTitle(ws, row, "HARI PENGELUARAN TERTINGGI", r, cols = 3)
        row++

        // Highest spending day highlight
        if (r.highestSpendingDay != null) {
            ws.value(row, 0, "Pengeluaran Tertinggi")
            ws.style(row, 0).bold().fontSize(10).fontColor(WHITE).fillColor(RED).set()
            ws.value(row, 1, r.highestSpendingDay.date)
            ws.style(row, 1).bold().fontSize(10).fontColor(WHITE).fillColor(RED)
                .horizontalAlignment("center").set()
            ws.value(row, 2, "${r.highestSpendingDay.sessionCount} sesi")
            ws.style(row, 2).bold().fontSize(10).fontColor(WHITE).fillColor(RED)
                .horizontalAlignment("center").set()
            ws.value(row, 3, ReportAggregator.formatRp(r.highestSpendingDay.total.toLong()))
            ws.style(row, 3).bold().fontSize(11).fontColor(WHITE).fillColor(RED)
                .horizontalAlignment("right").set()
            row++
        }

        // Lowest spending day highlight
        if (r.lowestSpendingDay != null) {
            ws.value(row, 0, "Pengeluaran Terendah")
            ws.style(row, 0).bold().fontSize(10).fontColor(WHITE).fillColor(GREEN).set()
            ws.value(row, 1, r.lowestSpendingDay.date)
            ws.style(row, 1).bold().fontSize(10).fontColor(WHITE).fillColor(GREEN)
                .horizontalAlignment("center").set()
            ws.value(row, 2, "${r.lowestSpendingDay.sessionCount} sesi")
            ws.style(row, 2).bold().fontSize(10).fontColor(WHITE).fillColor(GREEN)
                .horizontalAlignment("center").set()
            ws.value(row, 3, ReportAggregator.formatRp(r.lowestSpendingDay.total.toLong()))
            ws.style(row, 3).bold().fontSize(11).fontColor(WHITE).fillColor(GREEN)
                .horizontalAlignment("right").set()
            row++
        }

        row++

        // Overview stats
        ws.value(row, 0, "Total Hari Aktif Belanja")
        ws.style(row, 0).fontSize(10).fontColor(TEXT_MAIN).fillColor(LIGHT).set()
        ws.value(row, 3, "${r.monthlyBreakdown.sumOf { it.sessionCount }} hari")
        ws.style(row, 3).bold().fontSize(10).fontColor(TEAL).fillColor(LIGHT)
            .horizontalAlignment("right").set()
        for (c in 1..2) cell(ws, row, c, "", LIGHT)
        row++

        ws.value(row, 0, "Total Sesi Belanja")
        ws.style(row, 0).fontSize(10).fontColor(TEXT_MAIN).fillColor(WHITE).set()
        ws.value(row, 3, "${r.totalSessions} sesi")
        ws.style(row, 3).bold().fontSize(10).fontColor(TEAL).fillColor(WHITE)
            .horizontalAlignment("right").set()
        for (c in 1..2) cell(ws, row, c, "", WHITE)
        row++

        ws.value(row, 0, "Rata-rata per Hari Aktif")
        ws.style(row, 0).fontSize(10).fontColor(TEXT_MAIN).fillColor(LIGHT).set()
        ws.value(row, 3, ReportAggregator.formatRp(r.avgDailySpending.toLong()))
        ws.style(row, 3).bold().fontSize(10).fontColor(TEXT_MAIN).fillColor(LIGHT)
            .horizontalAlignment("right").set()
        for (c in 1..2) cell(ws, row, c, "", LIGHT)
    }

    // ── Shared layout helpers ──────────────────────────────────────────────────

    private fun writeAppHeader(ws: Worksheet, startRow: Int, r: FinancialSummaryReport): Int {
        var row = startRow

        // Row 0: App name bar
        ws.value(row, 0, "  ◈  AETHERSEA  —  Monthly Financial Summary Report")
        ws.style(row, 0).bold().fontSize(14).fontColor(WHITE).fillColor(DARK_NAVY).set()
        for (c in 1..5) cell(ws, row, c, "", DARK_NAVY)
        row++

        // Row 1: Subtitle
        ws.value(row, 0, "  Smart Finance Tracker")
        ws.style(row, 0).fontSize(10).fontColor(TEAL).fillColor(DARK_NAVY).set()
        ws.value(row, 3, "Generated: ${genFmt.format(r.generatedAt)}")
        ws.style(row, 3).fontSize(9).fontColor(TEXT_SEC).fillColor(DARK_NAVY)
            .horizontalAlignment("right").set()
        for (c in listOf(1, 2, 4, 5)) cell(ws, row, c, "", DARK_NAVY)
        row++

        // Row 2: Period / User
        ws.value(row, 0, "  Periode: ${r.reportPeriod}")
        ws.style(row, 0).fontSize(10).fontColor(TEXT_SEC).fillColor(NAVY).set()
        ws.value(row, 3, "Pengguna: ${r.userName}")
        ws.style(row, 3).fontSize(10).fontColor(TEXT_SEC).fillColor(NAVY)
            .horizontalAlignment("right").set()
        for (c in listOf(1, 2, 4, 5)) cell(ws, row, c, "", NAVY)
        row++

        return row
    }

    private fun writeSectionHeader(ws: Worksheet, row: Int, title: String, cols: IntRange): Int {
        ws.value(row, 0, title)
        ws.style(row, 0).bold().fontSize(11).fontColor(WHITE).fillColor(NAVY).set()
        for (c in cols.drop(1)) cell(ws, row, c, "", NAVY)
        return row + 1
    }

    private fun writeSheetTitle(ws: Worksheet, startRow: Int, title: String, r: FinancialSummaryReport, cols: Int): Int {
        var row = startRow
        ws.value(row, 0, "  ◈  AETHERSEA  —  $title")
        ws.style(row, 0).bold().fontSize(13).fontColor(WHITE).fillColor(DARK_NAVY).set()
        for (c in 1..cols) cell(ws, row, c, "", DARK_NAVY)
        row++
        ws.value(row, 0, "  Periode: ${r.reportPeriod}   |   Pengguna: ${r.userName}   |   ${genFmt.format(r.generatedAt)}")
        ws.style(row, 0).fontSize(9).fontColor(TEXT_SEC).fillColor(NAVY).set()
        for (c in 1..cols) cell(ws, row, c, "", NAVY)
        return row + 1
    }

    private fun cell(ws: Worksheet, row: Int, col: Int, value: String, bg: String) {
        ws.value(row, col, value)
        ws.style(row, col).fillColor(bg).set()
    }

    // ── KPI row builder ────────────────────────────────────────────────────────

    private data class KpiRow(
        val labelL: String, val valueL: String, val colorL: String,
        val labelR: String, val valueR: String, val colorR: String
    )

    private fun buildKpiRows(r: FinancialSummaryReport): List<KpiRow> {
        val usage    = r.budgetUsagePercent
        val usageStr = "${String.format("%.1f", usage)}%"

        return listOf(
            KpiRow(
                "Total Budget",           ReportAggregator.formatRp(r.totalBudget),    DARK_NAVY,
                "Total Pengeluaran",      ReportAggregator.formatRp(r.totalSpending),  if (r.isOverBudget) RED else TEXT_MAIN
            ),
            KpiRow(
                "Sisa Budget",            ReportAggregator.formatRp(r.remainingBudget), if (r.remainingBudget >= 0) GREEN else RED,
                "Rata-rata per Hari",     ReportAggregator.formatRp(r.avgDailySpending.toLong()), TEXT_MAIN
            ),
            KpiRow(
                "Total Sesi Belanja",     "${r.totalSessions} sesi",                   TEAL,
                "% Budget Terpakai",      usageStr,                                    kpiColor(usage)
            ),
            KpiRow(
                "Hari Pengeluaran Tinggi",
                r.highestSpendingDay?.let { "${ReportAggregator.formatRp(it.total.toLong())} (${it.date})" } ?: "—",
                RED,
                "Hari Pengeluaran Rendah",
                r.lowestSpendingDay?.let { "${ReportAggregator.formatRp(it.total.toLong())} (${it.date})" } ?: "—",
                GREEN
            ),
            KpiRow(
                "Kategori Utama",         r.topCategory,                               TEAL,
                "% Kategori Utama",       "${String.format("%.1f", r.topCategoryPercent)}%", TEAL
            )
        )
    }

    // ── Utils ──────────────────────────────────────────────────────────────────

    private fun kpiColor(usagePct: Double): String = when {
        usagePct >= 100 -> RED
        usagePct >= 90  -> ORANGE
        usagePct >= 75  -> TEXT_MAIN
        else            -> GREEN
    }
}
