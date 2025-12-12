package com.example.storechat.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

object AppUtils {
    /**
     * 获取已安装应用的版本号
     * 返回 -1 表示未安装
     */
    fun getInstalledVersionCode(context: Context, packageName: String): Long {
        if (packageName.isBlank()) return -1L
        return try {
            val pInfo = context.packageManager.getPackageInfo(packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
        } catch (e: PackageManager.NameNotFoundException) {
            -1L
        }
    }
}