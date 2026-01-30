package com.example.storechat.util

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager

object AppPackageNameCache {
    private const val APP_ID_TO_PACKAGE_NAME = "app_id_to_package_name_prefs"
    private const val NAME_TO_PACKAGE_NAME = "name_to_package_name_prefs"

    private lateinit var appIdPrefs: SharedPreferences
    private lateinit var namePrefs: SharedPreferences
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        appIdPrefs = context.getSharedPreferences(APP_ID_TO_PACKAGE_NAME, Context.MODE_PRIVATE)
        namePrefs = context.getSharedPreferences(NAME_TO_PACKAGE_NAME, Context.MODE_PRIVATE)
        isInitialized = true
    }

    fun saveMapping(appId: String, productName: String, packageName: String) {
        if (!isInitialized) return
        if (appId.isNotBlank()) {
            appIdPrefs.edit().putString(appId, packageName).apply()
        }
        if (productName.isNotBlank()) {
            namePrefs.edit().putString(productName, packageName).apply()
        }
    }

    fun getPackageNameByAppId(appId: String): String? {
        if (!isInitialized) return null
        return appIdPrefs.getString(appId, null)
    }

    fun getPackageNameByName(productName: String): String? {
        if (!isInitialized) return null
        return namePrefs.getString(productName, null)
    }

    fun scanInstalledPackages(context: Context) {
        if (!isInitialized) init(context)
        try {
            val pm = context.packageManager

            val packages = pm.getInstalledPackages(0)
            val nameEditor = namePrefs.edit()

            for (info in packages) {
    
                val appInfo = info.applicationInfo ?: continue
                val label = appInfo.loadLabel(pm).toString()

                if (label.isNotBlank()) {
        
                    if (!namePrefs.contains(label)) {
                        nameEditor.putString(label, info.packageName)
                    }
                }
            }
            nameEditor.apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}