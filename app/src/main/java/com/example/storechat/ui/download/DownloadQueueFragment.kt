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

        updateCardState()
    }
    override fun onResume() {
        super.onResume()
        viewModel.refreshDataFromDb()
    }

    private fun setupRecyclerView() {

        binding.recyclerRecent?.let { recyclerView ->
            recentAdapter = DownloadRecentAdapter { app ->
                context?.let { AppDetailActivity.start(it, app) }
            }
            recyclerView.adapter = recentAdapter
            recyclerView.layoutManager =
                LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        }


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

        binding.ivClose.setOnClickListener {
            activity?.findViewById<DrawerLayout>(R.id.drawerLayout)
                ?.closeDrawer(GravityCompat.END)
        }


        binding.tvStatus?.setOnClickListener { viewModel.onStatusClick() }
        binding.ivCancelDownload?.setOnClickListener { 
            viewModel.activeTask.value?.let { task ->
                viewModel.cancelDownload(task)
            }
        }


        binding.tvSeeMore?.setOnClickListener {
            isRecentExpanded = true
            updateCardState()
        }
    }

    private fun observeViewModel() {

        viewModel.toastMessage.observe(viewLifecycleOwner) { message ->
            if (message != null) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                viewModel.onToastMessageShown()
            }
        }

        viewModel.eventMessage.observe(viewLifecycleOwner) { message ->
            if (message.isNullOrEmpty()) return@observe

            if (message == "此应用暂无可用版本，无法下载" && resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                return@observe
            }

            if (message == "网络连接中断，下载已暂停") {
                return@observe
            }

            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }


        binding.recyclerRecent?.let {
            viewModel.recentInstalled.observe(viewLifecycleOwner) { list ->
                if (::recentAdapter.isInitialized) {
                    recentAdapter.submitList(list)
                }
                updateCardState()
            }
        }


        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            viewModel.downloadTasks.observe(viewLifecycleOwner) { tasks ->
                downloadAdapter?.submitList(tasks)
        
                if (tasks.isNullOrEmpty()) {
                    isRecentExpanded = false
                }
                updateCardState()
            }
        }
    }


    private fun updateCardState() {
        val downloads = viewModel.downloadTasks.value
        val recent = viewModel.recentInstalled.value

        val hasDownloads = !downloads.isNullOrEmpty()
        val hasRecent = !recent.isNullOrEmpty()


        binding.layoutDownloads?.visibility =
            if (hasDownloads) View.VISIBLE else View.GONE


        binding.tvSeeMore?.visibility = when {

            hasDownloads && hasRecent && !isRecentExpanded -> View.VISIBLE
            else -> View.GONE
        }


        val showRecent =

            (!hasDownloads && hasRecent) ||
        
                    (hasDownloads && hasRecent && isRecentExpanded)

        binding.layoutRecent?.visibility =
            if (showRecent) View.VISIBLE else View.GONE


        val showEmpty = !hasDownloads && !hasRecent
        binding.layoutEmpty?.visibility =
            if (showEmpty) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
