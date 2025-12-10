package com.example.storechat.model

import java.io.Serializable

enum class InstallState {
    NOT_INSTALLED,
    INSTALLED_OLD,
    INSTALLED_LATEST
}

data class AppInfo(
    val name: String,
    val appId: String, // from server
    val category: AppCategory, // from server
    val createTime: String, // from server
    val updateTime: String?, // from server
    val remark: String?, // from server
    val description: String?,
    val size: String,
    val downloadCount: Int,
    val packageName: String,
    val apkPath: String,
    val installState: InstallState,
    val versionName: String,
    val releaseDate: String,
    val downloadStatus: DownloadStatus = DownloadStatus.NONE,
    val progress: Int = 0
) : Serializable {

    val buttonText: String
        get() = when (downloadStatus) {
            DownloadStatus.DOWNLOADING -> "暂停"
            DownloadStatus.PAUSED -> "继续"
            DownloadStatus.VERIFYING -> "验证中"
            DownloadStatus.INSTALLING -> "安装中"
            DownloadStatus.NONE -> when (installState) {
                InstallState.NOT_INSTALLED -> "安装"
                InstallState.INSTALLED_OLD -> "升级"
                InstallState.INSTALLED_LATEST -> "打开"
            }
        }

    val buttonEnabled: Boolean
        get() = when (downloadStatus) {
            DownloadStatus.VERIFYING, DownloadStatus.INSTALLING -> false
            DownloadStatus.NONE -> installState != InstallState.INSTALLED_LATEST
            else -> true
        }

    val showProgress: Boolean
        get() = when(downloadStatus) {
            DownloadStatus.DOWNLOADING,
            DownloadStatus.PAUSED,
            DownloadStatus.VERIFYING,
            DownloadStatus.INSTALLING -> true
            DownloadStatus.NONE -> false
        }

    val showButton: Boolean
        get() = !showProgress

    val progressText: String
        get() = when (downloadStatus) {
            DownloadStatus.DOWNLOADING -> "$progress%"
            DownloadStatus.PAUSED      -> "继续"
            DownloadStatus.VERIFYING   -> "验证中"
            DownloadStatus.INSTALLING  -> "安装中"
            DownloadStatus.NONE        -> ""
        }
}
