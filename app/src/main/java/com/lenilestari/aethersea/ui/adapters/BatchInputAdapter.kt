package com.lenilestari.aethersea.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lenilestari.aethersea.R
import com.lenilestari.aethersea.data.model.BatchInput
import com.lenilestari.aethersea.databinding.ItemBatchInputBinding

class BatchInputAdapter(
    private val onDelete: (BatchInput) -> Unit
) : ListAdapter<BatchInput, BatchInputAdapter.VH>(DiffCb()) {

    inner class VH(val b: ItemBatchInputBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: BatchInput) {
            b.tvRawText.text = item.rawText
            if (item.source == "voice") {
                b.tvBadge.text = "VOICE"
                b.tvBadge.setBackgroundResource(R.drawable.bg_badge_red)
                b.tvBadge.setTextColor(b.root.context.getColor(R.color.recording_icon))
            } else {
                b.tvBadge.text = "MANUAL"
                b.tvBadge.setBackgroundResource(R.drawable.bg_badge_blue)
                b.tvBadge.setTextColor(b.root.context.getColor(R.color.accent_blue_text))
            }
            b.btnDelete.setOnClickListener { onDelete(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemBatchInputBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    class DiffCb : DiffUtil.ItemCallback<BatchInput>() {
        override fun areItemsTheSame(a: BatchInput, b: BatchInput) = a.id == b.id
        override fun areContentsTheSame(a: BatchInput, b: BatchInput) = a == b
    }
}
