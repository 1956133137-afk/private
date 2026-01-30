package com.example.storechat.model

import java.io.Serializable


data class HistoryVersion(
    val versionId: Long,
    val versionName: String,
    val versionCode: Int?,
    val apkPath: String,
    var installState: InstallState = InstallState.NOT_INSTALLED,
    val appName: String = ""
) : Serializable {

    val buttonText: String
        get() = when (installState) {
            InstallState.INSTALLED_LATEST, InstallState.INSTALLED_OLD -> "打开"
            else -> "安装"
        }

    val buttonEnabled: Boolean
        get() = true // Always clickable in history list
}
