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
            binding.tvStatus.setOnClickListener {
                if (currentTask?.statusButtonEnabled == true) {
                    currentTask?.let(onStatusClick)
                }
            }

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



            binding.statusProgress.progressDrawable = ContextCompat.getDrawable(ctx, drawableRes)
            binding.statusProgress.progress = task.progress



            binding.statusProgress.visibility = when (task.status) {
                DownloadStatus.DOWNLOADING,
                DownloadStatus.PAUSED -> View.VISIBLE
                else -> View.INVISIBLE
            }
            binding.executePendingBindings()

            if (task.status == DownloadStatus.DOWNLOADING) {
                if (task.progress >= 100) {
                    binding.tvStatus.text = "安装中"
                    binding.tvStatus.isEnabled = false
                } else {
                    binding.tvStatus.isEnabled = true
                }
            }


        }
    }
}