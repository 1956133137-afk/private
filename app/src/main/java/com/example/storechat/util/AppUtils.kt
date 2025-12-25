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

    /**
     * 检查应用是否已安装
     */
//    fun isAppInstalled(context: Context, packageName: String): Boolean {
//        return getInstalledVersionCode(context, packageName) != -1L
//    }

    /**
     * 打开已安装的应用
     * @param context 上下文
     * @param packageName 应用包名
     * @return 是否成功打开应用
     */
    fun launchApp(context: Context, packageName: String): Boolean {
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
