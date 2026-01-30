package com.example.storechat.model

import java.io.Serializable

enum class InstallState {
    NOT_INSTALLED,
    INSTALLED_OLD,
    INSTALLED_LATEST
}

data class AppInfo(
    val name: String,
    val appId: String,
    val versionId: Long?,
    val versionCode: Int?,
    val category: AppCategory,
    val createTime: String?,
    val updateTime: String?,
    val remark: String?,
    val description: String?,
    val size: String,
    val downloadCount: Int,
    val packageName: String,
    val apkPath: String,
    var installState: InstallState,
    val versionName: String,
    val releaseDate: String,
    val downloadStatus: DownloadStatus = DownloadStatus.NONE,
    val progress: Int = 0,
    var isInstalled: Boolean = false,
    val isHistory: Boolean = false,

    val currentSizeStr: String = "",
    val totalSizeStr: String = "",



    val installedVersionCode: Long = 0
) : Serializable {

    init {
        installState = if (!isInstalled) {
            InstallState.NOT_INSTALLED
        } else {
            installState
        }
    }

    val fixedDownloadCount: String = "99+"

    val buttonText: String
        get() = when (downloadStatus) {
            DownloadStatus.DOWNLOADING -> "暂停"
            DownloadStatus.PAUSED -> "继续"
            DownloadStatus.VERIFYING -> "验证中"
            DownloadStatus.INSTALLING -> "安装中"
            DownloadStatus.NONE -> when (installState) {
                InstallState.NOT_INSTALLED -> "安装"
                InstallState.INSTALLED_OLD -> "升级" // 或者显示“更新”
                InstallState.INSTALLED_LATEST -> "打开"
            }
        }

    val buttonEnabled: Boolean
        get() = when (downloadStatus) {
            DownloadStatus.VERIFYING, DownloadStatus.INSTALLING -> false
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

    val formattedReleaseDate: String
        get() = if (releaseDate.contains(' ')) {
            releaseDate.substring(0, releaseDate.indexOf(' '))
        } else {
            releaseDate
        }
}