package com.lenilestari.aethersea.util

import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateUtils {
    // ThreadLocal karena SimpleDateFormat tidak thread-safe — dipanggil dari banyak coroutine paralel
    private val displayFormat = ThreadLocal.withInitial { SimpleDateFormat("d MMMM yyyy", Locale("id", "ID")) }
    private val shortFormat = ThreadLocal.withInitial { SimpleDateFormat("dd MMM yyyy", Locale("id", "ID")) }
    private val fileFormat = ThreadLocal.withInitial { SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault()) }
    private val monthFormat = ThreadLocal.withInitial { SimpleDateFormat("MMMM yyyy", Locale("id", "ID")) }

    fun formatDisplay(timestamp: Timestamp): String = displayFormat.get()!!.format(timestamp.toDate())
    fun formatShort(timestamp: Timestamp): String = shortFormat.get()!!.format(timestamp.toDate())
    fun formatForFile(date: Date = Date()): String = fileFormat.get()!!.format(date)
    fun formatMonth(timestamp: Timestamp): String = monthFormat.get()!!.format(timestamp.toDate())

    fun formatDisplay(date: Long): String = displayFormat.get()!!.format(Date(date))

    fun startOfMonth(): Timestamp {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return Timestamp(cal.time)
    }

    fun daysUntilReset(lastResetAt: Timestamp, intervalDays: Long = 90L): Int {
        val resetMs = lastResetAt.toDate().time
        val nextReset = resetMs + intervalDays * 24 * 60 * 60 * 1000L
        val remaining = nextReset - System.currentTimeMillis()
        return (remaining / (24 * 60 * 60 * 1000L)).toInt().coerceAtLeast(0)
    }

    fun isOlderThan(timestamp: Timestamp, days: Long): Boolean {
        val ageMs = System.currentTimeMillis() - timestamp.toDate().time
        return ageMs > days * 24 * 60 * 60 * 1000L
    }
}
