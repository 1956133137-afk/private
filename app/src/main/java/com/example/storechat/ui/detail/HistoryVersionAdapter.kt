package com.example.storechat.ui.detail

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.storechat.databinding.ItemVersionBinding
import com.example.storechat.model.HistoryVersion

class HistoryVersionAdapter(
    private val onInstallClick: (HistoryVersion) -> Unit,
    private val onItemClick: (HistoryVersion) -> Unit
) : ListAdapter<HistoryVersion, HistoryVersionAdapter.VersionViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VersionViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemVersionBinding.inflate(inflater, parent, false)
        return VersionViewHolder(binding, onInstallClick, onItemClick)
    }

    override fun onBindViewHolder(holder: VersionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class VersionViewHolder(
        private val binding: ItemVersionBinding,
        private val onInstallClick: (HistoryVersion) -> Unit,
        private val onItemClick: (HistoryVersion) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: HistoryVersion) {
            binding.version = item
            binding.btnInstall.setOnClickListener { onInstallClick(item) }
            binding.root.setOnClickListener { onItemClick(item) } // Add this line
            binding.executePendingBindings()
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<HistoryVersion>() {
        override fun areItemsTheSame(oldItem: HistoryVersion, newItem: HistoryVersion): Boolean =
            oldItem.versionId == newItem.versionId

        override fun areContentsTheSame(oldItem: HistoryVersion, newItem: HistoryVersion): Boolean =
            oldItem == newItem
    }
}
