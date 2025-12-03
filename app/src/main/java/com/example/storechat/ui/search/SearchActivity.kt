package com.example.storechat.ui.search

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
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
        if (!initialQuery.isNullOrEmpty() && initialQuery != "null") {
            binding.etQuery.setText(initialQuery)
            viewModel.search(initialQuery)
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
        viewModel.search(binding.etQuery.text.toString())
    }

    private fun observeViewModel() {
        viewModel.result.observe(this) { list ->
            adapter.submitList(list)
            // 可以添加一个空状态的显示逻辑
            binding.tvVersion?.isVisible = list.isEmpty()
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
