package com.lenilestari.aethersea.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lenilestari.aethersea.data.model.ShoppingSession
import com.lenilestari.aethersea.databinding.ItemSessionBinding
import com.lenilestari.aethersea.util.CurrencyUtils
import com.lenilestari.aethersea.util.DateUtils

class SessionAdapter(
    private val onClick: (ShoppingSession) -> Unit
) : ListAdapter<ShoppingSession, SessionAdapter.VH>(DiffCb()) {

    inner class VH(val b: ItemSessionBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(session: ShoppingSession) {
            b.tvDate.text = DateUtils.formatDisplay(session.date)
            b.tvSubtitle.text = "${session.displayCategory} · ${session.items.size} item"
            b.tvTotal.text = CurrencyUtils.format(session.grandTotal)
            b.root.setOnClickListener { onClick(session) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemSessionBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    class DiffCb : DiffUtil.ItemCallback<ShoppingSession>() {
        override fun areItemsTheSame(a: ShoppingSession, b: ShoppingSession) = a.id == b.id
        override fun areContentsTheSame(a: ShoppingSession, b: ShoppingSession) = a == b
    }
}
