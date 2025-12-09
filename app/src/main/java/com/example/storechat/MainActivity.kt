package com.example.storechat

import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.example.storechat.data.api.SignUtils
import com.example.storechat.ui.home.HomeFragment
import com.example.storechat.xc.XcServiceManager
import me.jessyan.autosize.internal.CustomAdapt

class MainActivity : AppCompatActivity() , CustomAdapt{  //  实现 CustomAdapt

    private var drawerLayout: DrawerLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        val sign = SignUtils.testSign()
        Log.d("SignTest", "result = $sign")

//        testMqttApi

        // Initialize XC Service Manager
        XcServiceManager.init(this)

        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            drawerLayout = findViewById(R.id.drawerLayout)
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.mainContainer, HomeFragment())
                .commit()
        }

    }

    fun openDrawer() {
        // CRASH FIX: Explicitly open the END drawer to match the layout definition.
        drawerLayout?.openDrawer(GravityCompat.END)
    }// 竖屏：按宽度 411 适配；横屏：按高度 731 适配
    override fun isBaseOnWidth(): Boolean {
        return resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    }



    override fun getSizeInDp(): Float {
        return if (isBaseOnWidth()) {
            411f   // 竖屏：设计稿宽度
        } else {
            500f   // 横屏：用竖屏的“高度”当基准，保证纵向比例正常731
        }
    }
}
