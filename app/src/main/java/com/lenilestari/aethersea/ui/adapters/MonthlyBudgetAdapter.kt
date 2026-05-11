package com.lenilestari.aethersea.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lenilestari.aethersea.R
import com.lenilestari.aethersea.data.model.MonthlyBudget
import com.lenilestari.aethersea.databinding.ItemMonthlyBudgetBinding
import com.lenilestari.aethersea.util.CurrencyUtils

class MonthlyBudgetAdapter(
    private val onClick: (MonthlyBudget) -> Unit
) : ListAdapter<MonthlyBudget, MonthlyBudgetAdapter.VH>(DiffCb()) {

    inner class VH(val b: ItemMonthlyBudgetBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(mb: MonthlyBudget) {
            val (year, month) = mb.period.split("-").map { it.toInt() }
            val monthNames = arrayOf("Januari","Februari","Maret","April","Mei","Juni",
                "Juli","Agustus","September","Oktober","November","Desember")
            b.tvPeriod.text = "${monthNames[month - 1]} $year"

            b.tvSummary.text = "Budget ${CurrencyUtils.format(mb.totalBudget)} · " +
                "Spending ${CurrencyUtils.format(mb.totalSpending)} · " +
                "Sisa ${if (mb.leftAmount >= 0) "+" else ""}${CurrencyUtils.format(mb.leftAmount)}"

            if (mb.carriedToNext != 0L) {
                val nextMonth = if (month == 12) 1 else month + 1
                val nextYear = if (month == 12) year + 1 else year
                val sign = if (mb.carriedToNext >= 0) "+" else ""
                b.tvCarryOver.text = "${sign}${CurrencyUtils.format(mb.carriedToNext)} → ${monthNames[nextMonth - 1]} $nextYear"
                b.tvCarryOver.visibility = android.view.View.VISIBLE
            } else {
                b.tvCarryOver.visibility = android.view.View.GONE
            }

            if (mb.isClosed) {
                b.tvStatus.text = "Selesai"
                b.tvStatus.setTextColor(ContextCompat.getColor(b.root.context, R.color.text_caption))
                b.tvStatus.setBackgroundResource(R.drawable.bg_badge_blue)
            } else {
                b.tvStatus.text = "Aktif"
                b.tvStatus.setTextColor(ContextCompat.getColor(b.root.context, R.color.success_text))
                b.tvStatus.setBackgroundResource(R.drawable.bg_badge_green)
            }

            val leftColor = if (mb.leftAmount >= 0) R.color.success_text else R.color.danger_text
            b.tvSummary.setTextColor(ContextCompat.getColor(b.root.context, leftColor))

            b.root.setOnClickListener { onClick(mb) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemMonthlyBudgetBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    class DiffCb : DiffUtil.ItemCallback<MonthlyBudget>() {
        override fun areItemsTheSame(a: MonthlyBudget, b: MonthlyBudget) = a.period == b.period
        override fun areContentsTheSame(a: MonthlyBudget, b: MonthlyBudget) = a == b
    }
}
