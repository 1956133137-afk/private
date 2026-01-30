package com.example.storechat.ui.download

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.ViewDataBinding
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.storechat.databinding.ItemRecentAppBinding
import com.example.storechat.databinding.ItemRecentAppLandBinding
import com.example.storechat.model.AppInfo
import com.example.storechat.util.AppUtils


private const val VIEW_TYPE_PORTRAIT = 1

private const val VIEW_TYPE_LANDSCAPE = 2


class DownloadRecentAdapter(private val onItemClick: (AppInfo) -> Unit) :
    ListAdapter<AppInfo, DownloadRecentAdapter.ViewHolder>(AppInfoDiffCallback()) {


    private var recyclerView: RecyclerView? = null


    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        // 保存RecyclerView引用
        this.recyclerView = recyclerView
    }


    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        // 清空RecyclerView引用
        this.recyclerView = null
        super.onDetachedFromRecyclerView(recyclerView)
    }


    override fun getItemViewType(position: Int): Int {

        val layoutManager = recyclerView?.layoutManager

        return if (layoutManager is GridLayoutManager) {
            VIEW_TYPE_PORTRAIT
        } else {
            VIEW_TYPE_LANDSCAPE
        }
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {

        val inflater = LayoutInflater.from(parent.context)

        val binding = if (viewType == VIEW_TYPE_LANDSCAPE) {
            // 横屏模式使用ItemRecentAppLandBinding
            ItemRecentAppLandBinding.inflate(inflater, parent, false)
        } else {
            // 竖屏模式使用ItemRecentAppBinding
            ItemRecentAppBinding.inflate(inflater, parent, false)
        }

        return ViewHolder(binding)
    }


    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        val item = getItem(position)

        holder.bind(item, onItemClick)

        holder.itemView.setOnClickListener {
            val context = it.context
            if (!AppUtils.launchApp(context, item.packageName)) {
                // 如果无法打开应用，执行 onItemClick 回调
                onItemClick(item)
            }
        }
    }

    class ViewHolder(private val binding: ViewDataBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(app: AppInfo, onItemClick: (AppInfo) -> Unit) {
            when (binding) {
                is ItemRecentAppBinding -> {
                    binding.app = app

                    val context = binding.root.context
                    val isInstalled = AppUtils.getInstalledVersionCode(context, app.packageName) != -1L
                    binding.btnInstall.text = if (isInstalled) "打开" else "详情"

                    binding.btnInstall.setOnClickListener {
                        if (!AppUtils.launchApp(context, app.packageName)) {
                            // 如果无法打开应用，执行 onItemClick 回调
                            onItemClick(app)
                        }
                    }

                    binding.ivAppIcon.setOnClickListener {
                        onItemClick(app)
                    }
                }
                is ItemRecentAppLandBinding -> {
                    binding.app = app

                    binding.root.setOnClickListener {
                        val context = binding.root.context
                        if (!AppUtils.launchApp(context, app.packageName)) {
                            // 如果无法打开应用，执行 onItemClick 回调
                            onItemClick(app)
                        }
                    }
                }
            }
            binding.executePendingBindings()
        }
    }


    class AppInfoDiffCallback : DiffUtil.ItemCallback<AppInfo>() {

        override fun areItemsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
    
            return oldItem.packageName == newItem.packageName
        }


        override fun areContentsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
            // 直接比较两个对象是否相等
            return oldItem == newItem
        }
    }
}