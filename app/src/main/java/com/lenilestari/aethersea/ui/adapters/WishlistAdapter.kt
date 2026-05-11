package com.lenilestari.aethersea.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lenilestari.aethersea.data.model.Wishlist
import com.lenilestari.aethersea.databinding.ItemWishlistBinding
import com.lenilestari.aethersea.util.CurrencyUtils

class WishlistAdapter(
    private val onClick: (Wishlist) -> Unit
) : ListAdapter<Wishlist, WishlistAdapter.VH>(DiffCb()) {

    inner class VH(val b: ItemWishlistBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(w: Wishlist) {
            b.tvName.text = w.name
            b.tvProgress.text = "${CurrencyUtils.format(w.savedAmount)} / ${CurrencyUtils.format(w.targetPrice)}"
            b.progressBar.progress = w.progressPercent
            b.tvMonthly.text = "Nabung ${CurrencyUtils.format(w.monthlyTarget)}/bulan"
            b.tvMonthsLeft.text = if (w.monthsRemaining > 0) "~${w.monthsRemaining} bln lagi" else "Tercapai! 🎉"
            b.root.setOnClickListener { onClick(w) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemWishlistBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    class DiffCb : DiffUtil.ItemCallback<Wishlist>() {
        override fun areItemsTheSame(a: Wishlist, b: Wishlist) = a.id == b.id
        override fun areContentsTheSame(a: Wishlist, b: Wishlist) = a == b
    }
}
