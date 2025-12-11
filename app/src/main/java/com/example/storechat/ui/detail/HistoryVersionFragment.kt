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

/**
 * 显示“历史版本”的 Fragment
 */
class HistoryVersionFragment : Fragment() {

    private var _binding: FragmentHistoryVersionBinding? = null
    private val binding get() = _binding!!

    // 与 AppDetailActivity 共享同一个 ViewModel 实例
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

        // 触发加载历史版本数据
        viewModel.appInfo.value?.let {
            viewModel.loadHistoryFor(it)
        }
    }

    private fun setupRecyclerView() {
        adapter = HistoryVersionAdapter { historyVersion ->
            val currentApp = viewModel.appInfo.value
            if (currentApp == null) {
                Toast.makeText(requireContext(), "应用信息不存在", Toast.LENGTH_SHORT).show()
                return@HistoryVersionAdapter
            }

            // 点击“安装”按钮，直接调用安装服务
            // 内部会先根据下载链接接口生成有效期 URL，再调用静默安装服务
            Toast.makeText(
                requireContext(),
                "开始安装：${historyVersion.versionName}",
                Toast.LENGTH_SHORT
            ).show()

            AppRepository.installHistoryVersion(
                appId = currentApp.appId, // **FIXED: Pass the appId**
                packageName = currentApp.packageName,
                historyVersion = historyVersion
            )
        }
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
