package com.example.storechat.ui.detail

import android.content.res.Configuration
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.Log
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
            // 找到这一段代码进行修改
            val landscapeClickListener: (View) -> Unit = {
                viewModel.appInfo.value?.let { appInfo ->
                    // 根据安装状态决定是打开应用还是下载/安装
                    if (appInfo.installState == com.example.storechat.model.InstallState.INSTALLED_LATEST) {
                        // 如果是最新版本已安装，尝试打开应用
                        val launchIntent = requireActivity().packageManager.getLaunchIntentForPackage(appInfo.packageName)
                        if (launchIntent != null) {
                            launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(launchIntent)
                        }
//                        else {
////                            android.widget.Toast.makeText(context, "无法打开应用", android.widget.Toast.LENGTH_SHORT).show()
//                        }
                    } else {
                        // 否则执行下载/安装逻辑
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
    }
    override fun onResume() {
        super.onResume()
        // 每次界面显示时，重新加载应用信息，确保“安装”按钮变“打开”
        viewModel.appInfo.value?.packageName?.let { pkg ->
            viewModel.loadApp(pkg)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
