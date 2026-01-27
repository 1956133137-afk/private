package com.example.storechat.ui.detail

import android.content.res.Configuration
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
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
                viewModel.appInfo.value?.let { appInfo ->
                    if (appInfo.installState == com.example.storechat.model.InstallState.INSTALLED_LATEST) {
                        val launchIntent = requireActivity().packageManager.getLaunchIntentForPackage(appInfo.packageName)
                        if (launchIntent != null) {
                            launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(launchIntent)
                        }
                    } else {
                        com.example.storechat.data.AppRepository.toggleDownload(appInfo)
                    }
                }
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
        
        // 监听服务器错误事件
        observeDownloadErrors()
    }

    private fun observeDownloadErrors() {
        AppRepository.downloadErrorEvent.observe(viewLifecycleOwner) { errorMessage ->
            if (errorMessage != null && errorMessage.isNotEmpty()) {
                val dialog = AlertDialog.Builder(requireContext())
                    .setTitle("安装提示")
                    .setMessage(errorMessage)
                    .setPositiveButton("确定") { d, _ ->
                        d.dismiss()
                    }
                    .setOnDismissListener {
                        AppRepository.clearDownloadError()
                    }
                    .setCancelable(false)
                    .show()

                // 核心修改：动态设置对话框宽度
                if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                    val displayMetrics = resources.displayMetrics
                    val width = (displayMetrics.widthPixels * 0.85).toInt()
                    dialog.window?.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.appInfo.value?.packageName?.let { pkg ->
            viewModel.loadApp(pkg)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
