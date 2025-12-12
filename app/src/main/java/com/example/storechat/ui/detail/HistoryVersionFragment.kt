package com.example.storechat.ui.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.storechat.data.AppRepository
import com.example.storechat.databinding.FragmentHistoryVersionBinding
import com.example.storechat.model.AppInfo

class HistoryVersionFragment : Fragment() {

    private var _binding: FragmentHistoryVersionBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AppDetailViewModel by activityViewModels()

    private lateinit var adapter: HistoryVersionAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryVersionBinding.inflate(inflater, container, false)
        binding.viewModel = viewModel
        binding.lifecycleOwner = viewLifecycleOwner
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        observeViewModel()

        viewModel.appInfo.value?.let {
            viewModel.loadHistoryFor(it)
        }
    }

    private fun setupRecyclerView() {
        adapter = HistoryVersionAdapter(
            onInstallClick = { historyVersion ->
                val currentApp = viewModel.appInfo.value
                if (currentApp == null) {
                    Toast.makeText(requireContext(), "应用信息不存在", Toast.LENGTH_SHORT).show()
                    return@HistoryVersionAdapter
                }
                Toast.makeText(requireContext(), "开始安装：${historyVersion.versionName}", Toast.LENGTH_SHORT).show()
                AppRepository.installHistoryVersion(
                    app = currentApp,
                    historyVersion = historyVersion
                )
            },
            onItemClick = { historyVersion ->
                val currentApp = viewModel.appInfo.value
                if (currentApp == null) {
                    Toast.makeText(requireContext(), "应用信息不存在", Toast.LENGTH_SHORT).show()
                    return@HistoryVersionAdapter
                }
                // Create a temporary AppInfo for the specific historical version
                val historyAppInfo = currentApp.copy(
                    versionId = historyVersion.versionId,
                    versionName = historyVersion.versionName,
                    description = historyVersion.apkPath // Assuming apkPath holds description
                )
                AppDetailActivity.startWithAppInfo(requireContext(), historyAppInfo)
            }
        )
        binding.recyclerHistory.adapter = adapter
    }

    private fun observeViewModel() {
        viewModel.historyVersions.observe(viewLifecycleOwner) { versions ->
            adapter.submitList(versions)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
