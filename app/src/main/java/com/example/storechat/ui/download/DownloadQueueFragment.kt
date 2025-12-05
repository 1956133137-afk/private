package com.example.storechat.ui.download

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.storechat.R
import com.example.storechat.databinding.FragmentDownloadQueueBinding
import com.example.storechat.ui.detail.AppDetailActivity
import com.example.storechat.ui.home.DownloadListAdapter

class DownloadQueueFragment : Fragment() {

    private var _binding: FragmentDownloadQueueBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DownloadQueueViewModel by viewModels()

    private lateinit var recentAdapter: DownloadRecentAdapter
    private var downloadAdapter: DownloadListAdapter? = null

    // 是否已经点击「查看更多」
    private var isRecentExpanded = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDownloadQueueBinding.inflate(inflater, container, false)
        binding.viewModel = viewModel
        binding.lifecycleOwner = viewLifecycleOwner
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
        // 初始化时根据当前数据刷新一次卡片显示
        updateCardState()
    }

    private fun setupRecyclerView() {
        // 最近安装列表（竖屏/横屏都可能存在，所以用安全调用）
        binding.recyclerRecent?.let { recyclerView ->
            recentAdapter = DownloadRecentAdapter { app ->
                context?.let { AppDetailActivity.start(it, app) }
            }
            recyclerView.adapter = recentAdapter
            recyclerView.layoutManager =
                LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        }

        // 横屏小卡片的多任务下载列表
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            downloadAdapter = DownloadListAdapter (
                onStatusClick = { task -> viewModel.onStatusClick(task) }, onCancelClick = {
                    task -> viewModel.cancelDownload(task)
                }
            )
            binding.downloadAdapter = downloadAdapter
            binding.recyclerDownloads?.layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun setupClickListeners() {
        // 关闭右侧 Drawer
        binding.ivClose.setOnClickListener {
            activity?.findViewById<DrawerLayout>(R.id.drawerLayout)
                ?.closeDrawer(GravityCompat.END)
        }

        // 竖屏单任务卡片上的暂停/继续 与 取消
        binding.tvStatus?.setOnClickListener { viewModel.onStatusClick() }
        binding.ivCancelDownload?.setOnClickListener { viewModel.cancelDownload() }

        // 横屏小卡片：点击「查看更多」展开“最近安装完成”
        binding.tvSeeMore?.setOnClickListener {
            isRecentExpanded = true
            updateCardState()
        }
    }

    private fun observeViewModel() {
        // Toast 提示
        viewModel.toastMessage.observe(viewLifecycleOwner) { message ->
            if (message != null) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                viewModel.onToastMessageShown()
            }
        }

        // 最近安装列表
        binding.recyclerRecent?.let {
            viewModel.recentInstalled.observe(viewLifecycleOwner) { list ->
                if (::recentAdapter.isInitialized) {
                    recentAdapter.submitList(list)
                }
                updateCardState()
            }
        }

        // 横屏模式下监听下载任务列表（多任务）
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            viewModel.downloadTasks.observe(viewLifecycleOwner) { tasks ->
                downloadAdapter?.submitList(tasks)
                // 如果下载任务变为空，重置展开状态
                if (tasks.isNullOrEmpty()) {
                    isRecentExpanded = false
                }
                updateCardState()
            }
        }
    }

    /**
     * 根据当前任务 / 最近安装的情况，统一控制小卡片内部各区域：
     *  - 有下载任务：
     *      - 显示 layoutDownloads；
     *      - 若也有最近安装：显示「查看更多」，点了后展开 layoutRecent。
     *  - 无下载任务但有最近安装：
     *      - 隐藏 layoutDownloads 和「查看更多」，直接显示 layoutRecent（卡片高度合适）。
     *  - 下载任务和最近安装都没有：
     *      - 显示 layoutEmpty，让卡片看起来稍大一点。
     */
    private fun updateCardState() {
        val downloads = viewModel.downloadTasks.value
        val recent = viewModel.recentInstalled.value

        val hasDownloads = !downloads.isNullOrEmpty()
        val hasRecent = !recent.isNullOrEmpty()

        // 1. 下载列表区域（只有横屏小卡片有这个布局，所以用安全调用）
        binding.layoutDownloads?.visibility =
            if (hasDownloads) View.VISIBLE else View.GONE

        // 2. 查看更多（左对齐的那一行）
        binding.tvSeeMore?.visibility = when {
            // 有下载又有最近安装，并且尚未展开 -> 显示“查看更多”
            hasDownloads && hasRecent && !isRecentExpanded -> View.VISIBLE
            else -> View.GONE
        }

        // 3. 最近安装完成区域
        val showRecent =
            // 没有下载任务，但有最近安装：直接显示
            (!hasDownloads && hasRecent) ||
                    // 有下载任务，用户点击过查看更多：显示
                    (hasDownloads && hasRecent && isRecentExpanded)

        binding.layoutRecent?.visibility =
            if (showRecent) View.VISIBLE else View.GONE

        // 4. 空状态：下载任务 & 最近安装都为空
        val showEmpty = !hasDownloads && !hasRecent
        binding.layoutEmpty?.visibility =
            if (showEmpty) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
