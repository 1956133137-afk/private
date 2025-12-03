package com.example.storechat.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.storechat.databinding.ItemAppBinding
import com.example.storechat.model.AppInfo

class AppListAdapter(
    private val onItemClick: (AppInfo) -> Unit,      // 点整行 / 图标 -> 进详情
    private val onActionClick: (AppInfo) -> Unit     // 点右侧按钮 -> 下载/升级/打开
) : ListAdapter<AppInfo, AppListAdapter.AppViewHolder>(DiffCallback) {

    object DiffCallback : DiffUtil.ItemCallback<AppInfo>() {
        override fun areItemsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
            return oldItem.packageName == newItem.packageName
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

            // 整行点击：进入详情页
            binding.root.setOnClickListener { onItemClick(app) }
            // 图标点击：也进入详情
            binding.ivIcon.setOnClickListener { onItemClick(app) }

            // 右侧按钮点击：执行下载/升级/打开等动作
            binding.btnAction.setOnClickListener { onActionClick(app) }
            // 下载进度条点击：执行暂停/继续等动作
            binding.layoutProgress.setOnClickListener { onActionClick(app) }
        }
    }
}
