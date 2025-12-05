package com.example.storechat.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.storechat.databinding.ItemDownloadTaskBinding
import com.example.storechat.model.DownloadTask

class DownloadListAdapter(
    private val onStatusClick: (DownloadTask) -> Unit,
    private val onCancelClick: (DownloadTask) -> Unit
) : ListAdapter<DownloadTask, DownloadListAdapter.DownloadViewHolder>(DiffCallback) {

    companion object DiffCallback : DiffUtil.ItemCallback<DownloadTask>() {
        override fun areItemsTheSame(oldItem: DownloadTask, newItem: DownloadTask): Boolean {
            return oldItem.app.packageName == newItem.app.packageName
        }

        override fun areContentsTheSame(oldItem: DownloadTask, newItem: DownloadTask): Boolean {
            return oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DownloadViewHolder {
        val binding = ItemDownloadTaskBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DownloadViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DownloadViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class DownloadViewHolder(
        private val binding: ItemDownloadTaskBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var currentTask: DownloadTask? = null

        init {
            // ✅ 只通过按钮控制，不再点整行
            binding.tvStatus.setOnClickListener {
                currentTask?.let { onStatusClick(it) }
            }
            // ✅ 这里是关闭 / 取消下载的小叉号（item_download_task.xml 里的 ivCancel）
            binding.ivCancel.setOnClickListener {
                currentTask?.let { onCancelClick(it) }
            }
        }

        fun bind(task: DownloadTask) {
            currentTask = task
            binding.task = task
            binding.executePendingBindings()
        }
    }
}
