package com.example.storechat

import android.app.Application
import me.jessyan.autosize.AutoSizeConfig

class StoreChatApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AutoSizeConfig.getInstance().setLog(false)
    }
}