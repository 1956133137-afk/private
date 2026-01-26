package com.example.storechat

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.example.storechat.data.AppRepository
import com.example.storechat.ui.home.HomeFragment
import com.example.storechat.util.AppPackageNameCache
import com.example.storechat.util.LogUtil
import com.example.storechat.xc.XcServiceManager
import me.jessyan.autosize.internal.CustomAdapt

class MainActivity : AppCompatActivity(), CustomAdapt {  //  实现 CustomAdapt

    private var drawerLayout: DrawerLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        AppPackageNameCache.init(applicationContext)
        LogUtil.init(applicationContext)
        AppRepository.initialize(applicationContext)
        XcServiceManager.init(this)

        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            drawerLayout = findViewById(R.id.drawerLayout)
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.mainContainer, HomeFragment())
                .commit()
        }

        // 检查启动 Intent，看是否需要打开抽屉
        handleIntent(intent)

        // 新增：全局监听服务器错误事件
        observeDownloadErrors()
    }

    private fun observeDownloadErrors() {
        AppRepository.downloadErrorEvent.observe(this) { errorMessage ->
            if (errorMessage.isNotEmpty()) {
                Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // 当 Activity 已在后台时，处理新的 Intent
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.getBooleanExtra("OPEN_DOWNLOAD_DRAWER", false)) {
            if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                drawerLayout?.post { openDrawer() }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    fun openDrawer() {
        drawerLayout?.openDrawer(GravityCompat.END)
    }

    override fun isBaseOnWidth(): Boolean {
        return resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    }

    override fun getSizeInDp(): Float {
        return if (isBaseOnWidth()) {
            411f
        } else {
            550f
        }
    }
}
