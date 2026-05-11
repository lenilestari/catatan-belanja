package com.lenilestari.aethersea.util

import java.text.NumberFormat
import java.util.Locale

object CurrencyUtils {
    private val idLocale = Locale("id", "ID")
    // ThreadLocal karena NumberFormat tidak thread-safe — dipanggil dari banyak coroutine paralel
    private val formatter: ThreadLocal<NumberFormat> = ThreadLocal.withInitial {
        NumberFormat.getNumberInstance(idLocale).apply {
            maximumFractionDigits = 0
            minimumFractionDigits = 0
        }
    }

    fun format(amount: Double): String = "Rp${formatter.get()!!.format(amount)}"
    fun format(amount: Long): String = "Rp${formatter.get()!!.format(amount)}"

    fun formatPlain(amount: Double): String = formatter.get()!!.format(amount)

    fun parse(input: String): Double {
        val cleaned = input.replace("Rp", "").replace(".", "").replace(",", "").trim()
        return cleaned.toDoubleOrNull() ?: 0.0
    }
}
