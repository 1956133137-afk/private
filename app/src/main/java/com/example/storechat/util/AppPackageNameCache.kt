package com.example.storechat.util

import android.content.Context
import android.content.SharedPreferences

object AppPackageNameCache {
    private const val PREFS_NAME = "app_package_name_cache"
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun savePackageName(appId: String, packageName: String) {
        prefs.edit().putString(appId, packageName).apply()
    }

    fun getPackageName(appId: String): String? {
        return prefs.getString(appId, null)
    }
}
