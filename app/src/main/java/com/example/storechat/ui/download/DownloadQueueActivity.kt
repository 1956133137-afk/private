package com.example.storechat.ui.download

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import android.content.res.Configuration
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.storechat.databinding.ActivityDownloadQueueBinding
import com.example.storechat.ui.detail.AppDetailActivity
import com.example.storechat.ui.search.SearchActivity

/**
 * 下载队列界面Activity类
 * 支持竖屏和横屏两种布局模式
 */
class DownloadQueueActivity : AppCompatActivity() {

    // 视图绑定对象
    private lateinit var binding: ActivityDownloadQueueBinding

    // ViewModel实例，通过委托属性延迟初始化
    private val viewModel: DownloadQueueViewModel by viewModels()

    // 最近安装应用的适配器
    private lateinit var recentAdapter: DownloadRecentAdapter

    /**
     * Activity创建时调用
     * 初始化界面和各种组件
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 使用ViewBinding inflate布局
        binding = ActivityDownloadQueueBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 绑定 ViewModel，使DataBinding能够访问ViewModel中的数据
        binding.viewModel = viewModel
        // 设置生命周期所有者，确保数据绑定能够正确响应生命周期变化
        binding.lifecycleOwner = this

        // 初始化RecyclerView
        setupRecyclerView()
        // 设置各类点击监听器
        setupClickListeners()
        // 观察ViewModel中的LiveData变化
        observeViewModel()
    }

    /**
     * 设置RecyclerView相关配置
     * 根据屏幕方向选择不同的布局管理器
     */
    private fun setupRecyclerView() {
        // 创建适配器实例，并设置点击事件处理
        recentAdapter = DownloadRecentAdapter { app ->
            AppDetailActivity.start(this, app)
        }

        binding.recyclerRecent.apply {
            adapter = recentAdapter
            // 根据屏幕方向设置不同的布局管理器
            if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                // 横屏时使用垂直列表布局
                layoutManager = LinearLayoutManager(this@DownloadQueueActivity, LinearLayoutManager.VERTICAL, false)
            } else {
                // 竖屏时使用4列的网格布局
                layoutManager = GridLayoutManager(this@DownloadQueueActivity, 4)
            }
        }
    }

    /**
     * 设置各种点击事件监听器
     */
    private fun setupClickListeners() {
        // Toolbar相关按钮
        binding.ivBack?.setOnClickListener { finish() }
        binding.ivSearch?.setOnClickListener { SearchActivity.start(this) }

        // 空页面时的返回首页按钮
        binding.btnBackHome.setOnClickListener { finish() }

        // 下载任务相关按钮
        binding.tvStatus?.setOnClickListener { viewModel.onStatusClick() }
        binding.tvAllResume?.setOnClickListener { viewModel.resumeAllPausedTasks() }
        binding.ivCancel?.setOnClickListener { viewModel.cancelDownload() }
    }

    /**
     * 观察ViewModel中的LiveData变化
     * 并根据变化更新UI
     */
    private fun observeViewModel() {
        // 观察 Toast 消息事件
        viewModel.toastMessage.observe(this) { message ->
            message?.let {
                // 显示Toast消息
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
                // 消费事件，防止重复显示
                viewModel.onToastMessageShown()
            }
        }

        // 观察最近安装列表的变化
        viewModel.recentInstalled.observe(this) { apps ->
            // 更新适配器数据
            recentAdapter.submitList(apps)
        }

        // 根据 activeTask 是否为空，决定整体布局的显示/隐藏
        viewModel.activeTask.observe(this) { task ->
            val hasTask = task != null
            binding.layoutHasTask?.isVisible = hasTask
            binding.layoutEmpty?.isVisible = !hasTask
        }
    }

    /**
     * 启动下载队列界面的静态方法
     */
    companion object {
        fun start(context: Context) {
            val intent = Intent(context, DownloadQueueActivity::class.java)
            context.startActivity(intent)
        }
    }
}