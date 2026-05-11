package com.lenilestari.aethersea.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lenilestari.aethersea.R
import com.lenilestari.aethersea.data.model.Category
import com.lenilestari.aethersea.databinding.ItemSubCategoryBinding

class SubCategoryAdapter(
    private val onClick: (Category) -> Unit
) : ListAdapter<Category, SubCategoryAdapter.VH>(DiffCb()) {

    private var selectedId: String? = null

    fun setSelected(id: String?) {
        selectedId = id
        notifyDataSetChanged()
    }

    inner class VH(val b: ItemSubCategoryBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(cat: Category) {
            b.tvIcon.text = cat.icon
            b.tvName.text = cat.name
            val selected = cat.id == selectedId
            b.root.setBackgroundResource(
                if (selected) R.drawable.bg_category_selected else R.drawable.bg_card
            )
            b.root.setOnClickListener { setSelected(cat.id); onClick(cat) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemSubCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    class DiffCb : DiffUtil.ItemCallback<Category>() {
        override fun areItemsTheSame(a: Category, b: Category) = a.id == b.id
        override fun areContentsTheSame(a: Category, b: Category) = a == b
    }
}
