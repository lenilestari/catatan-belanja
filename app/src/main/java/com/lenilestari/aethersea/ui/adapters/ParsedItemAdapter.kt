package com.lenilestari.aethersea.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lenilestari.aethersea.data.model.ShoppingItem
import com.lenilestari.aethersea.databinding.ItemParsedItemBinding
import com.lenilestari.aethersea.util.CurrencyUtils

class ParsedItemAdapter(
    private val onClick: ((ShoppingItem, Int) -> Unit)? = null
) : ListAdapter<ShoppingItem, ParsedItemAdapter.VH>(DiffCb()) {

    fun setItems(items: List<ShoppingItem>) = submitList(items.toList())

    fun getItems(): List<ShoppingItem> = currentList.toList()

    fun updateItem(index: Int, item: ShoppingItem) {
        if (index in currentList.indices) {
            val updated = currentList.toMutableList().also { it[index] = item }
            submitList(updated)
        }
    }

    inner class VH(val b: ItemParsedItemBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: ShoppingItem, pos: Int) {
            b.tvItemName.text = item.item.replaceFirstChar { it.uppercase() }
            val qtyDisplay = if (item.qty % 1 == 0.0) item.qty.toInt().toString() else item.qty.toString()
            b.tvItemDetail.text = "$qtyDisplay ${item.unit} × ${CurrencyUtils.format(item.price)}"
            b.tvItemTotal.text = CurrencyUtils.format(item.total)
            if (item.confidence == "low") {
                b.tvItemName.alpha = 0.6f
            } else {
                b.tvItemName.alpha = 1f
            }
            onClick?.let { cb -> b.root.setOnClickListener { cb(item, pos) } }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemParsedItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position), position)

    class DiffCb : DiffUtil.ItemCallback<ShoppingItem>() {
        override fun areItemsTheSame(a: ShoppingItem, b: ShoppingItem) = a.item == b.item && a.total == b.total
        override fun areContentsTheSame(a: ShoppingItem, b: ShoppingItem) = a == b
    }
}
