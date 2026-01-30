package com.example.storechat

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.example.storechat.data.AppRepository
import com.example.storechat.ui.home.HomeFragment
import com.example.storechat.util.AppPackageNameCache
import com.example.storechat.util.LogUtil
import com.example.storechat.xc.XcServiceManager
import me.jessyan.autosize.internal.CustomAdapt

class MainActivity : AppCompatActivity(), CustomAdapt {

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


        handleIntent(intent)


        observeDownloadErrors()
        observeEventMessages()
    }

    private fun observeEventMessages() {
        AppRepository.eventMessage.observe(this) { message ->
            if (message.isNotEmpty()) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun observeDownloadErrors() {
        AppRepository.downloadErrorEvent.observe(this) { errorMessage ->
            if (errorMessage != null && errorMessage.isNotEmpty()) {
                val dialog = AlertDialog.Builder(this)
                    .setTitle("下载提示")
                    .setMessage(errorMessage)
                    .setPositiveButton("确定") { d, _ ->
                        d.dismiss()
                    }
                    .setOnDismissListener {
                        AppRepository.clearDownloadError()
                    }
                    .setCancelable(false)
                    .show()


                if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                    val displayMetrics = resources.displayMetrics
                    val width = (displayMetrics.widthPixels * 0.85).toInt()
                    dialog.window?.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

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
