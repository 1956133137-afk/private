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

            // 强制标签左对齐
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
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        // 为横屏的真实搜索框和按钮设置点击事件
        binding.ivSearch?.setOnClickListener { performSearch() }
        binding.etSearch?.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else {
                false
            }
        }

        binding.ivDownloadManager?.setOnClickListener {
            if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                (activity as? MainActivity)?.openDrawer()
            } else {
                startActivity(Intent(requireContext(), DownloadQueueActivity::class.java))
            }
        }
        binding.tvVersion?.setOnClickListener { viewModel.checkAppUpdate() }
    }

    private fun performSearch() {
        val keyword = binding.etSearch?.text.toString() ?: ""
        SearchActivity.start(requireContext(), keyword)
    }

    private fun observeViewModel() {
        viewModel.apps.observe(viewLifecycleOwner) { apps ->
            appListAdapter.submitList(apps)
        }

        viewModel.checkUpdateResult.observe(viewLifecycleOwner) { status ->
            when (status) {
                is UpdateStatus.LATEST -> Toast.makeText(requireContext(), "当前已是最新版本", Toast.LENGTH_SHORT).show()
                is UpdateStatus.NEW_VERSION -> showUpdateDialog(status.latestVersion)
                null -> {}
            }
            viewModel.clearUpdateResult()
        }

        viewModel.navigationEvent.observe(viewLifecycleOwner) { packageName ->
            if (packageName != null) {
                val intent = requireContext().packageManager.getLaunchIntentForPackage(packageName)
                if (intent != null) {
                    startActivity(intent)
                } else {
                    Toast.makeText(requireContext(), "无法打开应用", Toast.LENGTH_SHORT).show()
                }
                viewModel.onNavigationComplete()
            }
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
