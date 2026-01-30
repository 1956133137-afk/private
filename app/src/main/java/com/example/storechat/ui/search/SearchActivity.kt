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

        if (!initialQuery.isNullOrEmpty() && initialQuery != "null") {
            binding.etQuery.setText(initialQuery)

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
            hideKeyboard()
            viewModel.search(query)
        }
    }

    private fun observeViewModel() {
        viewModel.result.observe(this) { list ->
            adapter.submitList(list)

            val isEmpty = list.isEmpty()
            val hasQuery = !binding.etQuery.text.isNullOrEmpty()

            binding.layoutEmptyState?.isVisible = isEmpty && hasQuery
            binding.recyclerSearchResult.isVisible = !isEmpty

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