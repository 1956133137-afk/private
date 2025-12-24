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
import com.example.storechat.model.InstallState

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

        viewModel.appInfo.value?.let { app ->
            // 只有在没有缓存数据时才显示加载指示器
            if (viewModel.historyVersions.value == null) {
                // 显示加载指示器
                binding.progressBar?.visibility = View.VISIBLE
            }
            viewModel.loadHistoryFor(requireContext(), app)
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

                if (historyVersion.installState == InstallState.INSTALLED_LATEST) {
                    val intent = requireContext().packageManager.getLaunchIntentForPackage(currentApp.packageName)
                    if (intent != null) {
                        startActivity(intent)
                    } else {
                        Toast.makeText(requireContext(), "无法打开应用", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "开始安装：${historyVersion.versionName}", Toast.LENGTH_SHORT).show()
                    AppRepository.installHistoryVersion(
                        app = currentApp,
                        historyVersion = historyVersion
                    )
                }
            },
            onItemClick = { historyVersion ->
                viewModel.selectHistoryVersion(historyVersion)
            }
        )
        binding.recyclerHistory.adapter = adapter
    }

    private fun observeViewModel() {
        viewModel.historyVersions.observe(viewLifecycleOwner) { versions ->
            adapter.submitList(versions)
            // 数据加载完成，隐藏加载指示器
            binding.progressBar?.visibility = View.GONE
        }
        
        // 观察加载状态
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar?.visibility = if (isLoading == true) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}