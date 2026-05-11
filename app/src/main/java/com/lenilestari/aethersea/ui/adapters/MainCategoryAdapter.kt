package com.lenilestari.aethersea.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lenilestari.aethersea.data.model.Category
import com.lenilestari.aethersea.databinding.ItemMainCategoryBinding

class MainCategoryAdapter(
    private val onClick: (Category) -> Unit
) : ListAdapter<Category, MainCategoryAdapter.VH>(DiffCb()) {

    inner class VH(val b: ItemMainCategoryBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(cat: Category) {
            b.tvIcon.text = cat.icon
            b.tvName.text = cat.name
            b.root.setOnClickListener { onClick(cat) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemMainCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    class DiffCb : DiffUtil.ItemCallback<Category>() {
        override fun areItemsTheSame(a: Category, b: Category) = a.id == b.id
        override fun areContentsTheSame(a: Category, b: Category) = a == b
    }
}
