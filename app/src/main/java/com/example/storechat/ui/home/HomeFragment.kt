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
import com.example.storechat.R
import com.example.storechat.databinding.FragmentHomeBinding
import com.example.storechat.model.AppCategory
import com.example.storechat.model.AppInfo
import com.example.storechat.model.UpdateStatus
import com.example.storechat.ui.detail.AppDetailActivity
import com.example.storechat.ui.download.DownloadQueueActivity
import com.example.storechat.ui.search.SearchActivity
import com.example.storechat.util.LogUtil
import com.google.android.material.tabs.TabLayout

class HomeFragment : Fragment() {

    private val TAG = "HomeFragment"
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

        setupRecyclerView()
        setupViews()
        observeViewModel()

        if (savedInstanceState == null) {
            val initialCategory =
                AppCategory.values()[binding.tabLayoutCategories.selectedTabPosition]
            viewModel.selectCategory(requireContext(), initialCategory)
        }

        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            binding.etSearch?.clearFocus()
            binding.etSearch?.isFocusable = false
            binding.etSearch?.isFocusableInTouchMode = false
        }
    }

    override fun onResume() {
        super.onResume()
        if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            refreshCurrentCategory()
        } else {
            binding.etSearch?.clearFocus()
        }
    }

    private fun setupRecyclerView() {
        appListAdapter = AppListAdapter(
            onItemClick = { app -> openDetail(app) },
            onActionClick = { app -> viewModel.handleAppAction(app) }
        )
        binding.recyclerAppList.adapter = appListAdapter
        binding.recyclerAppList.itemAnimator = null
    }

    private fun setupViews() {
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
                LogUtil.d(TAG, "Tab selected: ${category.title}")
                viewModel.selectCategory(requireContext(), category)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {
                refreshCurrentCategory()
            }
        })

        binding.ivSearch?.setOnClickListener { performSearch() }
        binding.etSearch?.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else {
                false
            }
        }

        binding.etSearch?.setOnClickListener {
            if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                binding.etSearch?.isFocusable = true
                binding.etSearch?.isFocusableInTouchMode = true
                binding.etSearch?.requestFocus()
            }
        }

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

        // 暂时禁用版本更新功能
        // binding.tvVersion?.setOnClickListener { 
        //     Toast.makeText(requireContext(), "正在检查更新...", Toast.LENGTH_SHORT).show()
        //     viewModel.checkAppUpdate()
        // }
    }

    private fun performSearch() {
        val keyword = binding.etSearch?.text?.toString().orEmpty()
        LogUtil.d(TAG, "Performing search with keyword: $keyword")

        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            viewModel.inlineSearch(keyword)
            binding.etSearch?.clearFocus()
            binding.etSearch?.isFocusable = false
            binding.etSearch?.isFocusableInTouchMode = false

            // 添加收起键盘的逻辑
            val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(binding.etSearch?.windowToken, 0)
        } else {
            SearchActivity.start(requireContext(), keyword)
        }
    }

    private fun observeViewModel() {
        viewModel.apps.observe(viewLifecycleOwner) { apps ->
            appListAdapter.submitList(apps)
        }

        // 暂时禁用版本更新功能
        // viewModel.checkUpdateResult.observe(viewLifecycleOwner) { status ->
        //     if (status == null) return@observe
        //     when (status) {
        //         is UpdateStatus.LATEST -> {
        //             Toast.makeText(requireContext(), "当前已是最新版本", Toast.LENGTH_SHORT).show()
        //             viewModel.clearUpdateResult()
        //         }

        //         is UpdateStatus.NEW_VERSION -> {
        //             showUpdateDialog(status)
        //             // viewModel.clearUpdateResult() is called in showUpdateDialog's dismiss listener
        //         }
        //     }
        // }

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

        viewModel.totalDownloadProgress.observe(viewLifecycleOwner) { progress ->
            val value = (progress ?: 0).coerceIn(0, 100)
            binding.cpiDownloadProgress?.setProgressCompat(value, true)
        }

        viewModel.downloadFinishedDotVisible.observe(viewLifecycleOwner) { visible ->
            val redDot = binding.viewDownloadDot
            redDot?.visibility = if (visible == true) View.VISIBLE else View.GONE
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar?.visibility = if (isLoading == true) View.VISIBLE else View.GONE
        }

        viewModel.showNetworkError.observe(viewLifecycleOwner) { isError ->
            binding.tvNetworkError?.visibility = if (isError) View.VISIBLE else View.GONE
        }
    }

    private fun refreshCurrentCategory() {
        val selectedTabPosition = binding.tabLayoutCategories.selectedTabPosition
        if (selectedTabPosition != TabLayout.Tab.INVALID_POSITION) {
            val category = AppCategory.values()[selectedTabPosition]
            viewModel.selectCategory(requireContext(), category)
        }
    }

    private fun showUpdateDialog(status: UpdateStatus.NEW_VERSION) {
        val currentVer = viewModel.appVersion.value ?: "V1.0.0"

        AlertDialog.Builder(requireContext())
            .setTitle("发现新版本")
            .setMessage("当前版本：$currentVer\n最新版本：${status.latestVersion}")
            .setNegativeButton("稍后") { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton("去更新") { dialog, _ ->
                dialog.dismiss()
                viewModel.startSelfUpdate(status)
            }
            .setOnDismissListener {
                viewModel.clearUpdateResult()
            }
            .show()
    }

    private fun openDetail(app: AppInfo) {

        AppDetailActivity.startWithAppInfo(requireContext(), app)

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
