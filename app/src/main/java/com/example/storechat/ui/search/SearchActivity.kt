package com.example.storechat.ui.search

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.example.storechat.R
import com.example.storechat.databinding.ActivitySearchBinding
import com.example.storechat.model.AppInfo
import com.example.storechat.model.UpdateStatus
import com.example.storechat.ui.detail.AppDetailActivity
import com.example.storechat.ui.home.AppListAdapter

class SearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchBinding
    private val viewModel: SearchViewModel by viewModels()

    private lateinit var adapter: AppListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.viewModel = viewModel
        binding.lifecycleOwner = this

        setupViews()
        observeViewModel()

        val initialQuery = intent.getStringExtra(EXTRA_QUERY)
        // 修正：增加非空判断逻辑
        if (!initialQuery.isNullOrEmpty() && initialQuery != "null") {
            binding.etQuery.setText(initialQuery)
            // 移动光标到末尾
            binding.etQuery.setSelection(initialQuery.length)
            performSearch()
        }
    }

    private fun setupViews() {
        adapter = AppListAdapter(
            onItemClick = { app -> openDetail(app) },
            onActionClick = { app -> viewModel.handleAppAction(app) }
        )
        binding.recyclerSearchResult.adapter = adapter

        // 为搜索图标添加点击事件
        binding.ivSearch.setOnClickListener { performSearch() }

        binding.etQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else {
                false
            }
        }

        binding.ivBack.setOnClickListener { finish() }

        binding.tvVersion?.setOnClickListener { viewModel.checkAppUpdate() }
    }

    private fun performSearch() {
        val query = binding.etQuery.text.toString().trim()
        if (query.isNotEmpty()) {
            // ★ 优化：搜索时自动收起软键盘，避免遮挡结果
            hideKeyboard()
            viewModel.search(query)
        }
    }

    private fun observeViewModel() {
        viewModel.result.observe(this) { list ->
            adapter.submitList(list)

            // ★ 优化：根据结果控制 空状态 和 列表 的显示/隐藏
            val isEmpty = list.isEmpty()
            // 如果 query 为空（初始状态），也不显示空页面，只有搜了没结果才显示
            val hasQuery = !binding.etQuery.text.isNullOrEmpty()

            binding.layoutEmptyState?.isVisible = isEmpty && hasQuery
            binding.recyclerSearchResult.isVisible = !isEmpty

            // 版本号始终显示，或者根据需求处理
            binding.tvVersion?.isVisible = true
        }

        viewModel.checkUpdateResult.observe(this) { status ->
            when (status) {
                is UpdateStatus.LATEST -> Toast.makeText(this, getString(R.string.latest_version_toast), Toast.LENGTH_SHORT).show()
                is UpdateStatus.NEW_VERSION -> showUpdateDialog(status.latestVersion)
                null -> {}
            }
            viewModel.clearUpdateResult()
        }

        viewModel.navigationEvent.observe(this) { packageName ->
            if (packageName != null) {
                val intent = packageManager.getLaunchIntentForPackage(packageName)
                if (intent != null) {
                    startActivity(intent)
                } else {
                    Toast.makeText(this, getString(R.string.cannot_open_app_toast), Toast.LENGTH_SHORT).show()
                }
                viewModel.onNavigationComplete()
            }
        }
    }

    // ★ 新增辅助方法：收起软键盘
    private fun hideKeyboard() {
        val view = this.currentFocus
        if (view != null) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    private fun showUpdateDialog(latestVer: String) {
        val currentVer = viewModel.appVersion.value ?: "V1.0.0"

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.update_dialog_title))
            .setMessage(getString(R.string.update_dialog_message, currentVer, latestVer))
            .setNegativeButton(getString(R.string.update_dialog_negative_button)) { dialog, _ -> dialog.dismiss() }
            .setPositiveButton(getString(R.string.update_dialog_positive_button)) { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(this, getString(R.string.update_toast_message), Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun openDetail(app: AppInfo) {
        AppDetailActivity.start(this, app)
    }

    companion object {
        private const val EXTRA_QUERY = "extra_query"

        fun start(context: Context, query: String? = null) {
            val intent = Intent(context, SearchActivity::class.java).apply {
                putExtra(EXTRA_QUERY, query)
            }
            context.startActivity(intent)
        }
    }
}