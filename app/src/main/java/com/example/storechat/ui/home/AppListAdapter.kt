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
        val app = getItem(position)
        holder.bind(app)
        holder.itemView.setOnClickListener { onItemClick(app) }
        holder.binding.btnAction.setOnClickListener { onActionClick(app) }
        holder.binding.layoutProgress.setOnClickListener { onActionClick(app) }
    }

    inner class AppViewHolder(
       internal val binding: ItemAppBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(app: AppInfo) {
            binding.app = app
            binding.executePendingBindings()
        }
    }
}