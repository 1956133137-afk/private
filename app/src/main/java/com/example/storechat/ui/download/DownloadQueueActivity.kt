package com.example.storechat.ui.download

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.storechat.databinding.ActivityDownloadQueueBinding
import com.example.storechat.model.DownloadStatus
import com.example.storechat.ui.detail.AppDetailActivity
import com.example.storechat.ui.search.SearchActivity

class DownloadQueueActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDownloadQueueBinding
    private val viewModel: DownloadQueueViewModel by viewModels()

    private lateinit var recentAdapter: DownloadRecentAdapter
    private lateinit var downloadAdapter: DownloadQueueAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDownloadQueueBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.viewModel = viewModel
        binding.lifecycleOwner = this

        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        // 确保每次恢复时都正确更新UI状态
        updateEmptyViewVisibility()
    }

    private fun setupRecyclerView() {
        // 下载列表
        downloadAdapter = DownloadQueueAdapter(viewModel)
        binding.adapter = downloadAdapter

        // 最近安装
        recentAdapter = DownloadRecentAdapter { app ->
            AppDetailActivity.start(this, app)
        }

        binding.recyclerDownloads?.layoutManager =
            LinearLayoutManager(this@DownloadQueueActivity)

        binding.recyclerRecent?.apply {
            adapter = recentAdapter
            layoutManager = GridLayoutManager(this@DownloadQueueActivity, 4)
        }
    }

    private fun setupClickListeners() {
        binding.ivBack?.setOnClickListener { finish() }
        binding.ivSearch?.setOnClickListener { SearchActivity.start(this) }
        binding.btnBackHome?.setOnClickListener { finish() }

        // 顶部「全部继续」
        binding.tvResumeAll?.setOnClickListener {
            viewModel.resumeAllPausedTasks()
        }
    }

    private fun observeViewModel() {
        viewModel.toastMessage.observe(this) { message ->
            message?.let {
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
                viewModel.onToastMessageShown()
            }
        }

        viewModel.recentInstalled.observe(this) { apps ->
            recentAdapter.submitList(apps)
            updateEmptyViewVisibility()
        }

        viewModel.downloadTasks.observe(this) { tasks ->
            downloadAdapter.submitList(tasks)
            updateEmptyViewVisibility()

            // 只要还有"已暂停"的任务，就显示「全部继续」
            binding.tvResumeAll?.isVisible =
                tasks.any { it.status == DownloadStatus.PAUSED }
        }
    }

    private fun updateEmptyViewVisibility() {
        val isDownloadListEmpty = viewModel.downloadTasks.value.isNullOrEmpty()
        val isRecentListEmpty = viewModel.recentInstalled.value.isNullOrEmpty()
        binding.layoutEmpty?.isVisible = isDownloadListEmpty && isRecentListEmpty
//        android.util.Log.d("DownloadQueue", "isDownloadListEmpty=$isDownloadListEmpty, isRecentListEmpty=$isRecentListEmpty, layoutEmpty visibility=${binding.layoutEmpty?.isVisible}")
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, DownloadQueueActivity::class.java)
            context.startActivity(intent)
        }
    }
}