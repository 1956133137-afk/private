package com.example.storechat.ui.detail

import android.content.res.Configuration
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.storechat.data.AppRepository
import com.example.storechat.databinding.FragmentAppDetailBinding

/**
 * 显示“最新版本”的应用详情信息
 */
class AppDetailFragment : Fragment() {

    private var _binding: FragmentAppDetailBinding? = null
    private val binding get() = _binding!!

    // 与 AppDetailActivity 共享同一个 ViewModel 实例
    private val viewModel: AppDetailViewModel by activityViewModels()

    // 为安装/进度按钮设置点击事件
    private val installClickListener: (View) -> Unit = {
        viewModel.appInfo.value?.let(AppRepository::toggleDownload)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAppDetailBinding.inflate(inflater, container, false)
        binding.viewModel = viewModel
        binding.lifecycleOwner = viewLifecycleOwner
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 横屏模式下，Fragment 包含安装按钮和可滚动的描述文本, 需要在这里设置监听器和滚动方法
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            binding.btnInstall?.setOnClickListener(installClickListener)
            binding.layoutProgress?.setOnClickListener(installClickListener)

            // 2. 使应用简介的 TextView 可以滚动
            binding.tvDescription?.movementMethod = ScrollingMovementMethod.getInstance()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
