package com.example.storechat.ui.detail

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.databinding.DataBindingUtil
import androidx.drawerlayout.widget.DrawerLayout
import com.example.storechat.MainActivity
import com.example.storechat.R
import com.example.storechat.data.AppRepository
import com.example.storechat.databinding.ActivityAppDetailBinding
import com.example.storechat.model.AppInfo
import com.example.storechat.model.DownloadStatus
import com.example.storechat.model.InstallState
import com.example.storechat.ui.download.DownloadQueueActivity
import com.example.storechat.ui.search.SearchActivity
import com.example.storechat.util.LogUtil
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import me.jessyan.autosize.internal.CustomAdapt

class AppDetailActivity : AppCompatActivity(), CustomAdapt {

    private val TAG = "AppDetailActivity"
    private lateinit var binding: ActivityAppDetailBinding
    private val viewModel: AppDetailViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_app_detail)
        binding.viewModel = viewModel
        binding.lifecycleOwner = this

        val appInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(EXTRA_APP_INFO, AppInfo::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra(EXTRA_APP_INFO) as? AppInfo
        }

        if (appInfo != null) {
            // ** THE FIX IS HERE **
            // We should use setAppInfo, which is designed to handle the initial setup.
            // Using setHistoryAppInfo incorrectly triggers the history override logic.
            viewModel.setAppInfo(appInfo)
        } else {
            val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
            if (packageName == null) {
                finish()
                return
            }
            viewModel.loadApp(packageName)
        }

        setupViews()
        setupViewPagerAndTabs()
        observeViewModel()
    }

    fun openDrawer() {
        // 在横屏模式下，binding.drawerLayout
        binding.drawerLayout?.openDrawer(GravityCompat.END)
    }

    private fun setupViews() {
        binding.ivBack.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
        }

        binding.ivSearch.setOnClickListener {
            if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                // 横屏模式下跳转到首页
                val intent = Intent(this, MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                startActivity(intent)
            } else {
                // 竖屏模式下保持原有逻辑
                SearchActivity.start(this)
            }
        }

        binding.ivDownload.setOnClickListener {
            if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                openDrawer()
            } else {
                startActivity(Intent(this, DownloadQueueActivity::class.java))
            }
        }

        val clickListener: (view: View) -> Unit = {
            viewModel.appInfo.value?.let { currentAppInfo ->
                if (currentAppInfo.installState == InstallState.INSTALLED_LATEST) {
                    LogUtil.d(TAG, "Attempting to open app. PackageName: ${currentAppInfo.packageName}")
                    val intent = packageManager.getLaunchIntentForPackage(currentAppInfo.packageName)
                    if (intent != null) {
                        startActivity(intent)
                    } else {
                        Toast.makeText(this, "无法打开应用", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    AppRepository.toggleDownload(currentAppInfo)
                }
            }
        }

        // 移除方向判断，为所有方向设置点击监听器
        binding.btnInstall?.setOnClickListener(clickListener)
        binding.layoutProgress?.setOnClickListener(clickListener)
//
//        if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
//            binding.btnInstall?.setOnClickListener(clickListener)
//            binding.layoutProgress?.setOnClickListener(clickListener)
//        }
    }

    private fun setupViewPagerAndTabs() {
        binding.viewPager.adapter = AppDetailPagerAdapter(this)

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            val customView = android.view.LayoutInflater.from(this)
                .inflate(R.layout.custom_tab, null) as TextView

            customView.text = when (position) {
                0 -> "最新版本"
                1 -> "历史版本"
                else -> ""
            }
            tab.customView = customView
        }.attach()

        // 确保 onTabSelected 只有以下逻辑，不要额外添加重置数据的代码
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                tab?.customView?.isSelected = true
                viewModel.isHistoryTabSelected.value = tab?.position == 1
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
                tab?.customView?.isSelected = false
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        binding.tabLayout.getTabAt(0)?.customView?.isSelected = true

        for (i in 0 until binding.tabLayout.tabCount) {
            val tabView = (binding.tabLayout.getChildAt(0) as ViewGroup).getChildAt(i)
            if (tabView is ViewGroup) {
                for (j in 0 until tabView.childCount) {
                    val child = tabView.getChildAt(j)
                    if (child is TextView) {
                        child.gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    }
                }
            }
        }
    }

    private fun observeViewModel() {
        viewModel.switchToTab.observe(this) { tabIndex ->
            binding.viewPager.setCurrentItem(tabIndex, true)
        }
        // 【新增】监听 AppInfo 变化，处理 100% 进度时的“安装中”状态
        viewModel.appInfo.observe(this) { appInfo ->
            if (appInfo == null) return@observe

            val btn = binding.btnInstall
            // 仅在按钮存在时操作（防止横竖屏布局差异导致为空）
            if (btn != null) {
                if (appInfo.downloadStatus == DownloadStatus.DOWNLOADING) {
                    if (appInfo.progress >= 100) {
                        // 1. 下载完成但尚未安装完成 -> 显示“安装中”
                        btn.text = "安装中"
                        btn.isEnabled = false // 禁止重复点击
                    } else {
                        // 2. 正常下载中 -> 显示进度
                        btn.text = "${appInfo.progress}%"
                        btn.isEnabled = true
                    }
                }
                // 其他状态（如 PAUSED, NONE, INSTALLED）通常由 XML DataBinding 处理
                // 如果发现 DataBinding 覆盖了这里的设置，可以在 else 分支强制刷新状态
            }
        }
    }


    companion object {
        private const val EXTRA_PACKAGE_NAME = "extra_package_name"
        private const val EXTRA_APP_INFO = "extra_app_info"

        fun start(context: Context, app: AppInfo) {
            val intent = Intent(context, AppDetailActivity::class.java)
            intent.putExtra(EXTRA_PACKAGE_NAME, app.packageName)
            context.startActivity(intent)
        }

        fun startWithAppInfo(context: Context, appInfo: AppInfo) {
            val intent = Intent(context, AppDetailActivity::class.java)
            intent.putExtra(EXTRA_APP_INFO, appInfo)
            context.startActivity(intent)
        }
    }

    override fun isBaseOnWidth(): Boolean {
        return resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    }

    override fun getSizeInDp(): Float {
        return if (isBaseOnWidth()) {
            411f
        } else {
            450f
        }
    }
}
