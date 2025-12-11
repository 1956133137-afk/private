package com.example.storechat.ui.home

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.storechat.MainActivity
import com.example.storechat.databinding.FragmentHomeBinding
import com.example.storechat.model.AppCategory
import com.example.storechat.model.AppInfo
import com.example.storechat.model.UpdateStatus
import com.example.storechat.ui.detail.AppDetailActivity
import com.example.storechat.ui.download.DownloadQueueActivity
import com.example.storechat.ui.search.SearchActivity
import com.google.android.material.tabs.TabLayout

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()
    private lateinit var appListAdapter: AppListAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        binding.viewModel = viewModel
        binding.lifecycleOwner = viewLifecycleOwner
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViews()
        observeViewModel()

        // Manually trigger initial data load
        if (savedInstanceState == null) {
            val initialCategory = AppCategory.values()[binding.tabLayoutCategories.selectedTabPosition]
            viewModel.selectCategory(initialCategory)
        }
    }

    override fun onResume() {
        super.onResume()
        // 竖屏模式下，从其他页面返回时，刷新列表以更新状态
        if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            refreshCurrentCategory()
        }
    }

    private fun setupViews() {
        appListAdapter = AppListAdapter(
            onItemClick = { app -> openDetail(app) },
            onActionClick = { app -> viewModel.handleAppAction(app) }
        )
        binding.recyclerAppList.adapter = appListAdapter

        binding.tabLayoutCategories.apply {
            AppCategory.values().forEach { category ->
                addTab(newTab().setText(category.title))
            }

            tabGravity = TabLayout.GRAVITY_FILL
            tabMode = TabLayout.MODE_SCROLLABLE
        }

        binding.tabLayoutCategories.addOnTabSelectedListener(object :
            TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val category = AppCategory.values()[tab.position]
                viewModel.selectCategory(category)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {
                 refreshCurrentCategory()
            }
        })

        // 搜索按钮 & 键盘搜索
        binding.ivSearch?.setOnClickListener { performSearch() }
        binding.etSearch?.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else {
                false
            }
        }
        // 添加点击输入框时恢复焦点，显示光标
        binding.etSearch?.setOnClickListener {
            binding.etSearch?.requestFocus()
        }

        // 下载按钮点击：清红点 + 跳转下载页 / 抽屉
        val openDownloadPage: () -> Unit = {
            viewModel.onDownloadIconClicked()
            if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                (activity as? MainActivity)?.openDrawer()
            } else {
                startActivity(Intent(requireContext(), DownloadQueueActivity::class.java))
            }
        }
        binding.ivDownloadManager?.setOnClickListener { openDownloadPage() }
        binding.layoutDownloadIcon?.setOnClickListener { openDownloadPage() }

        binding.tvVersion?.setOnClickListener { viewModel.checkAppUpdate() }
    }

    /**
     * 搜索：
     *  - 横屏：在首页内联模糊查询（只刷新下方列表，不跳转）
     *  - 竖屏：保持原行为，跳转 SearchActivity
     */
    private fun performSearch() {
        val keyword = binding.etSearch?.text?.toString().orEmpty()

        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE){
//            横屏：直接在首页内联搜索
            viewModel.inlineSearch(keyword)
            // 清除 EditText 焦点，隐藏光标
            binding.etSearch?.clearFocus()
        }else{
//            竖屏：跳转到 SearchActivity
            SearchActivity.start(requireContext(), keyword)
        }

    }

    private fun observeViewModel() {
        viewModel.apps.observe(viewLifecycleOwner) { apps ->
            appListAdapter.submitList(apps)
        }

        viewModel.checkUpdateResult.observe(viewLifecycleOwner) { status ->
            when (status) {
                is UpdateStatus.LATEST -> Toast.makeText(
                    requireContext(),
                    "当前已是最新版本",
                    Toast.LENGTH_SHORT
                ).show()

                is UpdateStatus.NEW_VERSION -> showUpdateDialog(status.latestVersion)
                null -> {}
            }
            viewModel.clearUpdateResult()
        }

        viewModel.navigationEvent.observe(viewLifecycleOwner) { packageName ->
            if (packageName != null) {
                val intent =
                    requireContext().packageManager.getLaunchIntentForPackage(packageName)
                if (intent != null) {
                    startActivity(intent)
                } else {
                    Toast.makeText(requireContext(), "无法打开应用", Toast.LENGTH_SHORT).show()
                }
                viewModel.onNavigationComplete()
            }
        }

        // ======= 下载图标 / 进度圈 / 红点联动 =======

        // 1）是否有下载任务：只负责控制“进度圈”是否显示，不再动红点
        viewModel.isDownloadInProgress.observe(viewLifecycleOwner) { inProgress ->
            val progressCircle = binding.cpiDownloadProgress
            val downloadIcon = binding.ivDownloadManager

            if (inProgress == true) {
                progressCircle?.visibility = View.VISIBLE
                downloadIcon?.visibility = View.VISIBLE
            } else {
                progressCircle?.visibility = View.GONE
                downloadIcon?.visibility = View.VISIBLE
            }
        }

        // 2）总进度：驱动圆形进度圈，让圆环慢慢包围图标
        viewModel.totalDownloadProgress.observe(viewLifecycleOwner) { progress ->
            val value = (progress ?: 0).coerceIn(0, 100)
            // 这里用的是 Material 的 CircularProgressIndicator
            binding.cpiDownloadProgress?.setProgressCompat(value, true)
        }

        // 3）下载完成红点：只看 downloadFinishedDotVisible
        viewModel.downloadFinishedDotVisible.observe(viewLifecycleOwner) { visible ->
            val redDot = binding.viewDownloadDot
            redDot?.visibility = if (visible == true) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
    }
    
    private fun refreshCurrentCategory() {
        val selectedTabPosition = binding.tabLayoutCategories.selectedTabPosition
        if (selectedTabPosition != TabLayout.Tab.INVALID_POSITION) {
            val category = AppCategory.values()[selectedTabPosition]
            viewModel.selectCategory(category)
        }
    }

    private fun showUpdateDialog(latestVer: String) {
        val currentVer = viewModel.appVersion.value ?: "V1.0.0"

        AlertDialog.Builder(requireContext())
            .setTitle("发现新版本")
            .setMessage("当前版本：$currentVer\n最新版本：$latestVer")
            .setNegativeButton("稍后") { dialog, _ -> dialog.dismiss() }
            .setPositiveButton("去更新") { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(requireContext(), "这里以后接入应用商店自更新逻辑", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun openDetail(app: AppInfo) {
        AppDetailActivity.start(requireContext(), app)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
