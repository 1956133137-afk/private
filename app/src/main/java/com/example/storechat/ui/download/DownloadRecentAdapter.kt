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

// 定义视图类型常量：竖屏模式
private const val VIEW_TYPE_PORTRAIT = 1
// 定义视图类型常量：横屏模式
private const val VIEW_TYPE_LANDSCAPE = 2

/**
 * 下载页面最近安装应用列表的适配器类
 * 支持竖屏和横屏两种不同的布局显示
 * @param onItemClick 点击项时的回调函数
 */
class DownloadRecentAdapter(private val onItemClick: (AppInfo) -> Unit) :
    ListAdapter<AppInfo, DownloadRecentAdapter.ViewHolder>(AppInfoDiffCallback()) {

    // 保存关联的RecyclerView引用，用于判断布局管理器类型
    private var recyclerView: RecyclerView? = null

    /**
     * 当Adapter被附加到RecyclerView时调用
     * @param recyclerView 关联的RecyclerView
     */
    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        // 保存RecyclerView引用
        this.recyclerView = recyclerView
    }

    /**
     * 当Adapter从RecyclerView分离时调用
     * @param recyclerView 分离的RecyclerView
     */
    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        // 清空RecyclerView引用
        this.recyclerView = null
        super.onDetachedFromRecyclerView(recyclerView)
    }

    /**
     * 根据位置确定要创建的视图类型
     * 通过检查布局管理器类型来决定显示哪种布局
     * @param position 项的位置
     * @return 视图类型（竖屏或横屏）
     */
    override fun getItemViewType(position: Int): Int {
        // 获取关联的RecyclerView的布局管理器
        val layoutManager = recyclerView?.layoutManager
        // 如果是GridLayoutManager（竖屏模式）则返回竖屏视图类型，否则返回横屏视图类型
        return if (layoutManager is GridLayoutManager) {
            VIEW_TYPE_PORTRAIT
        } else {
            VIEW_TYPE_LANDSCAPE
        }
    }

    /**
     * 创建ViewHolder实例
     * 根据视图类型创建不同的布局
     * @param parent 父容器
     * @param viewType 视图类型
     * @return ViewHolder实例
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // 获取LayoutInflater实例
        val inflater = LayoutInflater.from(parent.context)
        // 根据视图类型选择合适的布局文件进行inflate
        val binding = if (viewType == VIEW_TYPE_LANDSCAPE) {
            // 横屏模式使用ItemRecentAppLandBinding
            ItemRecentAppLandBinding.inflate(inflater, parent, false)
        } else {
            // 竖屏模式使用ItemRecentAppBinding
            ItemRecentAppBinding.inflate(inflater, parent, false)
        }
        // 返回包含绑定对象的ViewHolder
        return ViewHolder(binding)
    }

    /**
     * 绑定数据到ViewHolder
     * @param holder ViewHolder实例
     * @param position 数据项位置
     */
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        // 获取当前位置的数据项
        val item = getItem(position)
        // 调用ViewHolder的bind方法绑定数据
        holder.bind(item)
        // 设置项点击监听器
        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    /**
     * ViewHolder内部类，用于持有列表项视图
     * @param binding ViewDataBinding实例，可能是ItemRecentAppBinding或ItemRecentAppLandBinding
     */
    class ViewHolder(private val binding: ViewDataBinding) : RecyclerView.ViewHolder(binding.root) {
        /**
         * 绑定应用数据到视图
         * @param app AppInfo数据对象
         */
        fun bind(app: AppInfo) {
            // 根据绑定对象的具体类型进行相应的数据绑定
            if (binding is ItemRecentAppBinding) {
                // 竖屏模式下的数据绑定
                binding.app = app
            } else if (binding is ItemRecentAppLandBinding) {
                // 横屏模式下的数据绑定
                binding.app = app
            }
            // 执行待处理的数据绑定操作
            binding.executePendingBindings()
        }
    }

    /**
     * AppInfo数据项差异比较回调类
     * 用于DiffUtil算法优化列表更新性能
     */
    class AppInfoDiffCallback : DiffUtil.ItemCallback<AppInfo>() {
        /**
         * 判断两个数据项是否代表同一个对象
         * @param oldItem 旧数据项
         * @param newItem 新数据项
         * @return 如果代表同一个对象返回true，否则返回false
         */
        override fun areItemsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
            // 通过包名判断是否为同一个应用
            return oldItem.packageName == newItem.packageName
        }

        /**
         * 判断两个数据项内容是否相同
         * @param oldItem 旧数据项
         * @param newItem 新数据项
         * @return 如果内容相同返回true，否则返回false
         */
        override fun areContentsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
            // 直接比较两个对象是否相等
            return oldItem == newItem
        }
    }
}