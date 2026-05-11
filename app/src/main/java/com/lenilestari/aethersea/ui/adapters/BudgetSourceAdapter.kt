package com.lenilestari.aethersea.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lenilestari.aethersea.data.model.BudgetSource
import com.lenilestari.aethersea.databinding.ItemBudgetSourceBinding
import com.lenilestari.aethersea.util.CurrencyUtils
import com.lenilestari.aethersea.util.DateUtils

class BudgetSourceAdapter(
    private val onEdit: (BudgetSource) -> Unit,
    private val onDelete: (BudgetSource) -> Unit
) : ListAdapter<BudgetSource, BudgetSourceAdapter.VH>(DiffCb()) {

    inner class VH(val b: ItemBudgetSourceBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(s: BudgetSource) {
            b.tvName.text = s.name
            b.tvDate.text = DateUtils.formatDisplay(s.receivedDate)
            b.tvNote.text = s.note.ifEmpty { "-" }
            b.tvAmount.text = CurrencyUtils.format(s.amount)
            b.btnEdit.setOnClickListener { onEdit(s) }
            b.btnDelete.setOnClickListener { onDelete(s) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemBudgetSourceBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    class DiffCb : DiffUtil.ItemCallback<BudgetSource>() {
        override fun areItemsTheSame(a: BudgetSource, b: BudgetSource) = a.id == b.id
        override fun areContentsTheSame(a: BudgetSource, b: BudgetSource) = a == b
    }
}
