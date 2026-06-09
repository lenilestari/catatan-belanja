package com.lenilestari.aethersea.export

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import com.lenilestari.aethersea.data.model.BudgetSource
import com.lenilestari.aethersea.data.model.MonthlyBudget
import com.lenilestari.aethersea.data.model.ShoppingSession
import com.lenilestari.aethersea.data.model.Wishlist
import com.lenilestari.aethersea.util.Constants
import com.lenilestari.aethersea.util.CurrencyUtils
import com.lenilestari.aethersea.util.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.dhatim.fastexcel.Workbook
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExcelExporter {
    private val monthFmt = SimpleDateFormat("MMMM yyyy", Locale("id", "ID"))
    private val dateFmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    suspend fun export(
        context: Context,
        sessions: List<ShoppingSession>,
        wishlists: List<Wishlist>,
        budgetSources: List<BudgetSource>,
        monthlyBudgets: List<MonthlyBudget>
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val filename = "Catatan_Belanja_${DateUtils.formatForFile()}.xlsx"
            val cv = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, filename)
                put(MediaStore.Downloads.MIME_TYPE,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                put(MediaStore.Downloads.RELATIVE_PATH,
                    "${Environment.DIRECTORY_DOWNLOADS}/${Constants.EXPORT_FOLDER_NAME}")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv) ?: return@withContext false

            resolver.openOutputStream(uri)?.buffered()?.use { stream ->
                val wb = Workbook(stream, "Catatan Belanja", "1.0")
                // Detail Belanja dibuat pertama agar menjadi sheet default saat Excel dibuka
                writeDetailBelanja(wb, sessions)
                writeRingkasan(wb, sessions, monthlyBudgets)
                writeSumberBudget(wb, budgetSources)
                writeWishlist(wb, wishlists)
                wb.finish()
            }

            cv.clear(); cv.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, cv, null, null)
            true
        } catch (_: Exception) { false }
    }

    private fun writeRingkasan(wb: Workbook, sessions: List<ShoppingSession>, monthlyBudgets: List<MonthlyBudget>) {
        val ws = wb.newWorksheet("Ringkasan")
        ws.value(0, 0, "Catatan Belanja")
        ws.style(0, 0).bold().set()
        ws.value(1, 0, "Diekspor: ${dateFmt.format(Date())}")
        ws.value(3, 0, "Ringkasan Budget Per Bulan")
        ws.style(3, 0).bold().set()

        val headers = arrayOf("Bulan", "Total Sources", "Carry Over", "Total Budget", "Spending", "Sisa")
        headers.forEachIndexed { i, h -> ws.value(4, i, h); ws.style(4, i).bold().set() }

        var row = 5
        for (mb in monthlyBudgets.sortedBy { it.period }) {
            ws.value(row, 0, mb.period)
            ws.value(row, 1, mb.totalSourcesAmount.toDouble())
            ws.value(row, 2, mb.carryOverFromPrevious.toDouble())
            ws.value(row, 3, mb.totalBudget.toDouble())
            ws.value(row, 4, mb.totalSpending.toDouble())
            ws.value(row, 5, mb.leftAmount.toDouble())
            row++
        }

        row += 2
        ws.value(row, 0, "Ringkasan Per Kategori")
        ws.style(row, 0).bold().set()
        row++
        ws.value(row, 0, "Kategori"); ws.value(row, 1, "Total"); ws.style(row, 0).bold().set(); ws.style(row, 1).bold().set()
        row++
        val byCategory = sessions.groupBy { it.displayCategory }
        byCategory.forEach { (cat, s) ->
            ws.value(row, 0, cat)
            ws.value(row, 1, s.sumOf { it.grandTotal })
            row++
        }
    }

    private fun writeDetailBelanja(wb: Workbook, sessions: List<ShoppingSession>) {
        val ws = wb.newWorksheet("Detail Belanja")
        ws.value(0, 0, "Diekspor: ${dateFmt.format(Date())}")
        val headers = arrayOf("Tanggal", "Kategori Utama", "Sub Kategori", "Nama Barang", "Qty", "Satuan", "Harga Satuan", "Total Item", "Total Sesi", "Source")
        headers.forEachIndexed { i, h -> ws.value(1, i, h); ws.style(1, i).bold().set() }

        var row = 2
        val sorted = sessions.sortedByDescending { it.date.seconds }
        for (s in sorted) {
            val tanggal = dateFmt.format(s.date.toDate())
            if (s.items.isEmpty()) {
                // Sesi tanpa item detail — tetap tampilkan baris ringkasan
                ws.value(row, 0, tanggal)
                ws.value(row, 1, s.mainCategoryName)
                ws.value(row, 2, s.subCategoryName)
                ws.value(row, 3, "(tidak ada detail item)")
                ws.value(row, 8, s.grandTotal)
                ws.style(row, 8).bold().set()
                row++
            } else {
                for ((idx, item) in s.items.withIndex()) {
                    ws.value(row, 0, tanggal)
                    ws.value(row, 1, s.mainCategoryName)
                    ws.value(row, 2, s.subCategoryName)
                    ws.value(row, 3, item.item)
                    ws.value(row, 4, item.qty)
                    ws.value(row, 5, item.unit)
                    ws.value(row, 6, item.price)
                    ws.value(row, 7, item.total)
                    // Total sesi hanya ditulis di baris pertama sesi itu
                    if (idx == 0) {
                        ws.value(row, 8, s.grandTotal)
                        ws.style(row, 8).bold().set()
                    }
                    ws.value(row, 9, item.source)
                    row++
                }
            }
        }

        // Baris grand total di bawah
        if (sorted.isNotEmpty()) {
            row++
            ws.value(row, 7, "TOTAL KESELURUHAN")
            ws.value(row, 8, sorted.sumOf { it.grandTotal })
            ws.style(row, 7).bold().set()
            ws.style(row, 8).bold().set()
        }
    }

    private fun writeSumberBudget(wb: Workbook, sources: List<BudgetSource>) {
        val ws = wb.newWorksheet("Sumber Budget")
        val headers = arrayOf("Bulan", "Dari Siapa", "Tanggal Terima", "Catatan", "Jumlah")
        headers.forEachIndexed { i, h -> ws.value(0, i, h); ws.style(0, i).bold().set() }

        var row = 1
        for (s in sources.sortedByDescending { it.receivedDate }) {
            ws.value(row, 0, s.period)
            ws.value(row, 1, s.name)
            ws.value(row, 2, dateFmt.format(s.receivedDate.toDate()))
            ws.value(row, 3, s.note)
            ws.value(row, 4, s.amount.toDouble())
            row++
        }
    }

    private fun writeWishlist(wb: Workbook, wishlists: List<Wishlist>) {
        val ws = wb.newWorksheet("Wishlist")
        val headers = arrayOf("Nama", "Harga Target", "Sudah Ditabung", "Target Per Bulan", "Estimasi Selesai", "Progress %")
        headers.forEachIndexed { i, h -> ws.value(0, i, h); ws.style(0, i).bold().set() }

        var row = 1
        for (w in wishlists) {
            ws.value(row, 0, w.name)
            ws.value(row, 1, w.targetPrice)
            ws.value(row, 2, w.savedAmount)
            ws.value(row, 3, w.monthlyTarget)
            ws.value(row, 4, if (w.monthsRemaining > 0) "~${w.monthsRemaining} bulan lagi" else "Sudah tercapai!")
            ws.value(row, 5, w.progressPercent.toDouble())
            row++
        }
    }
}
