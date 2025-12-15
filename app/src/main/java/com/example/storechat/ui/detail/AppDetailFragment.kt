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

        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            val landscapeClickListener: (View) -> Unit = {
                (activity as? AppDetailActivity)?.openDrawer()
            }
            binding.btnInstall?.setOnClickListener(landscapeClickListener)
            binding.layoutProgress?.setOnClickListener(landscapeClickListener)

            binding.tvDescription?.movementMethod = ScrollingMovementMethod.getInstance()
        } else {
            val portraitClickListener: (View) -> Unit = {
                viewModel.appInfo.value?.let(AppRepository::toggleDownload)
            }
            binding.btnInstall?.setOnClickListener(portraitClickListener)
            binding.layoutProgress?.setOnClickListener(portraitClickListener)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
