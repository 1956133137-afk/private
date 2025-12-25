package com.example.storechat.ui.download

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

class DownloadQueueAdapter(private val viewModel: DownloadQueueViewModel) :
    ListAdapter<DownloadTask, DownloadQueueAdapter.ViewHolder>(DownloadTaskDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDownloadTaskBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding, viewModel)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val task = getItem(position)
        holder.bind(task)
    }

    class ViewHolder(
        private val binding: ItemDownloadTaskBinding,
        private val viewModel: DownloadQueueViewModel
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(task: DownloadTask) {
            binding.task = task

            val ctx = binding.root.context

            // 进度条填充颜色：下载中=深蓝，暂停=浅蓝，其它状态隐藏填充
            val drawableRes = when (task.status) {
                DownloadStatus.PAUSED -> R.drawable.bg_download_status_progress_paused
                DownloadStatus.DOWNLOADING -> R.drawable.bg_download_status_progress
                else -> R.drawable.bg_download_status_progress
            }

            // 【关键修复】先设置 Drawable，再设置 Progress，解决列表刷新时的显示Bug
            binding.statusProgress.progressDrawable = ContextCompat.getDrawable(ctx, drawableRes)
            binding.statusProgress.progress = task.progress

            // 验证中 / 安装中：不显示内部填充，只保留白底蓝边
            binding.statusProgress.visibility = when (task.status) {
                DownloadStatus.DOWNLOADING,
                DownloadStatus.PAUSED -> View.VISIBLE
                else -> View.INVISIBLE
            }

            // 取消下载
            binding.ivCancel.setOnClickListener {
                viewModel.cancelDownload(task)
            }

            // 按钮控制暂停 / 继续
            binding.tvStatus.setOnClickListener {
                // 【修改】增加防重复点击检查
                if (task.statusButtonEnabled) {
                    viewModel.onStatusClick(task)
                }
            }

            binding.executePendingBindings()
        }
    }
}

class DownloadTaskDiffCallback : DiffUtil.ItemCallback<DownloadTask>() {
    override fun areItemsTheSame(oldItem: DownloadTask, newItem: DownloadTask): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: DownloadTask, newItem: DownloadTask): Boolean {
        return oldItem == newItem
    }
}