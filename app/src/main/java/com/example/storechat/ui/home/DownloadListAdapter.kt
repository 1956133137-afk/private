package com.example.storechat.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.storechat.R
import com.example.storechat.databinding.ItemDownloadTaskBinding
import com.example.storechat.model.DownloadStatus
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
            // 点击按钮 暂停/继续
            binding.tvStatus.setOnClickListener {
                // 【修改】增加状态检查，避免在验证/安装中点击，防止重复操作
                if (currentTask?.statusButtonEnabled == true) {
                    currentTask?.let(onStatusClick)
                }
            }
            // 取消下载
            binding.ivCancel.setOnClickListener {
                currentTask?.let(onCancelClick)
            }
        }

        fun bind(task: DownloadTask) {
            currentTask = task
            binding.task = task

            val ctx = binding.root.context
            val drawableRes = when (task.status) {
                DownloadStatus.PAUSED -> R.drawable.bg_download_status_progress_paused
                DownloadStatus.DOWNLOADING -> R.drawable.bg_download_status_progress
                else -> R.drawable.bg_download_status_progress
            }

            // 【关键修复】必须先设置 progressDrawable，再设置 progress
            // 否则在部分系统/列表复用时，进度条颜色更新会有延迟或错乱
            binding.statusProgress.progressDrawable = ContextCompat.getDrawable(ctx, drawableRes)
            binding.statusProgress.progress = task.progress

            // 只有下载中/暂停才显示进度条颜色填充，NONE(安装)/VERIFYING(验证)只显示边框
            binding.statusProgress.visibility = when (task.status) {
                DownloadStatus.DOWNLOADING,
                DownloadStatus.PAUSED -> View.VISIBLE
                else -> View.INVISIBLE
            }

            binding.executePendingBindings()
        }
    }
}