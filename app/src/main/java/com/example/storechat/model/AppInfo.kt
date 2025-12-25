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
    val versionId: Long?, // from server, the ID of the latest version
    val versionCode: Int?, // from server
    val category: AppCategory, // from server
    val createTime: String?, // from server
    val updateTime: String?, // from server
    val remark: String?, // from server
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

    // --- 新增字段：用于实时显示下载大小，默认值为空字符串 ---
    val currentSizeStr: String = "",
    val totalSizeStr: String = ""
) : Serializable {

    init {
        installState = if (!isInstalled) {
            InstallState.NOT_INSTALLED
        } else {
            installState
        }
    }

    val buttonText: String
        get() = when (downloadStatus) {
            DownloadStatus.DOWNLOADING -> "暂停"
            DownloadStatus.PAUSED -> "重试"
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
            DownloadStatus.PAUSED      -> "已暂停"
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