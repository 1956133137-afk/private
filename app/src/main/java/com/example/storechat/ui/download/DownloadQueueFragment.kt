package com.example.storechat.ui.download

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
import com.example.storechat.model.AppInfo
import com.example.storechat.ui.detail.AppDetailActivity

/**
 * 下载队列界面Fragment类
 * 用于在其他界面中嵌入下载队列功能
 */
class DownloadQueueFragment : Fragment() {

    // 视图绑定对象，使用可空类型并在onDestroyView中置空以避免内存泄漏
    private var _binding: FragmentDownloadQueueBinding? = null
    private val binding get() = _binding!!

    // ViewModel实例，通过委托属性延迟初始化
    private val viewModel: DownloadQueueViewModel by viewModels()

    private lateinit var recentAdapter: DownloadRecentAdapter

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
    }

    /**
     * 设置RecyclerView相关配置
     */
    private fun setupRecyclerView() {
        recentAdapter = DownloadRecentAdapter { app ->
            context?.let { AppDetailActivity.start(it, app) }
        }

        binding.recyclerRecent.apply {
            adapter = recentAdapter
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        }
    }

    /**
     * 设置各种点击事件监听器
     */
    private fun setupClickListeners() {
        binding.ivClose.setOnClickListener {
            // 安全地关闭抽屉，避免当DrawerLayout不存在时发生崩溃
            activity?.findViewById<DrawerLayout>(R.id.drawerLayout)?.closeDrawer(GravityCompat.END)
        }
        binding.tvStatus.setOnClickListener { viewModel.onStatusClick() }
    }

    /**
     * 观察ViewModel中的LiveData变化
     */
    private fun observeViewModel() {
        viewModel.recentInstalled.observe(viewLifecycleOwner) { list ->
            recentAdapter.submitList(list)
        }

        viewModel.toastMessage.observe(viewLifecycleOwner) { message ->
            if (message != null) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                viewModel.onToastMessageShown()
            }
        }
    }

    /**
     * 清理资源以避免内存泄漏
     */
    override fun onDestroyView() {
        super.onDestroyView()
        // 清空binding引用以避免内存泄漏
        _binding = null
    }
}
