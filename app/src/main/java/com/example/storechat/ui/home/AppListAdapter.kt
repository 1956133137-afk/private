package com.example.storechat.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.storechat.databinding.ItemAppBinding
import com.example.storechat.model.AppInfo

class AppListAdapter(
    private val onItemClick: (AppInfo) -> Unit,
    private val onActionClick: (AppInfo) -> Unit
) : ListAdapter<AppInfo, AppListAdapter.AppViewHolder>(DiffCallback) {

    object DiffCallback : DiffUtil.ItemCallback<AppInfo>() {
        override fun areItemsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
            return oldItem.appId == newItem.appId
        }

        override fun areContentsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
            return oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = ItemAppBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AppViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AppViewHolder(
        private val binding: ItemAppBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(app: AppInfo) {
            binding.app = app
            binding.executePendingBindings()

            binding.root.setOnClickListener { onItemClick(app) }
            binding.ivIcon?.setOnClickListener { onItemClick(app) }
            binding.btnAction?.setOnClickListener { onActionClick(app) }
            binding.layoutProgress?.setOnClickListener { onActionClick(app) }
        }
    }
}
