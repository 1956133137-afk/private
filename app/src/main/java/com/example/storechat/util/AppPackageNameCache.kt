package com.example.storechat.util

import android.content.Context
import android.content.SharedPreferences

object AppPackageNameCache {
    private const val APP_ID_TO_PACKAGE_NAME = "app_id_to_package_name_prefs"
    private const val NAME_TO_PACKAGE_NAME = "name_to_package_name_prefs"

    private lateinit var appIdPrefs: SharedPreferences
    private lateinit var namePrefs: SharedPreferences

    fun init(context: Context) {
        appIdPrefs = context.getSharedPreferences(APP_ID_TO_PACKAGE_NAME, Context.MODE_PRIVATE)
        namePrefs = context.getSharedPreferences(NAME_TO_PACKAGE_NAME, Context.MODE_PRIVATE)
    }

    fun saveMapping(appId: String, productName: String, packageName: String) {
        appIdPrefs.edit().putString(appId, packageName).apply()
        namePrefs.edit().putString(productName, packageName).apply()
    }

    fun getPackageNameByAppId(appId: String): String? {
        return appIdPrefs.getString(appId, null)
    }

    fun getPackageNameByName(productName: String): String? {
        return namePrefs.getString(productName, null)
    }
}
