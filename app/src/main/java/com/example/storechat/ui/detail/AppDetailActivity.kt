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
import com.example.storechat.model.InstallState
import com.example.storechat.ui.download.DownloadQueueActivity
import com.example.storechat.ui.search.SearchActivity
import com.example.storechat.util.LogUtil
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
            viewModel.setHistoryAppInfo(appInfo)
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
    }

    fun openDrawer() {
        // 在横屏模式下，binding.drawerLayout 才不为 null
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

        if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            binding.btnInstall?.setOnClickListener(clickListener)
            binding.layoutProgress?.setOnClickListener(clickListener)
        }
    }

    private fun setupViewPagerAndTabs() {
        binding.viewPager.adapter = AppDetailPagerAdapter(this)

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "最新版本"
                1 -> "历史版本"
                else -> ""
            }
        }.attach()

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