package com.example.storechat.model

import java.io.Serializable

/**
 * Represents a historical version of an app.
 *
 * @param versionId The unique ID of this version from the server.
 * @param versionName The display name of the version (e.g., "1.0.2").
 * @param apkPath Placeholder for the APK file path.
 * @param installState The installation state of this specific version on the device.
 * @param appName The name of the application.
 */
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
