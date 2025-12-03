package com.example.storechat

import android.content.res.Configuration
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.example.storechat.ui.home.HomeFragment
import com.example.storechat.xc.XcServiceManager

class MainActivity : AppCompatActivity() {

    private var drawerLayout: DrawerLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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
    }
}
