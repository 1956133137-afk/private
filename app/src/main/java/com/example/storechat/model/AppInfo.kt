package com.example.storechat.model

import java.io.Serializable

/**
 * 表示应用程序的安装状态。
 */
enum class InstallState {
    /** 应用程序未安装。用户可以选择安装它。 */
    NOT_INSTALLED,     // 未安装 -> 安装
    /** 已安装旧版本的应用程序。用户可以选择升级。 */
    INSTALLED_OLD,     // 已安装有更新 -> 升级
    /** 已安装最新版本的应用程序。用户可以打开它。 */
    INSTALLED_LATEST   // 已最新 -> 打开
}

// DownloadStatus 现在是单一数据源，定义在 DownloadTask.kt 中

/**
 * 表示一个应用程序的信息。
 *
 * @property name 应用程序的名称。
 * @property description 应用程序的描述。
 * @property size 应用程序的大小。
 * @property downloadCount 应用程序的下载次数。
 * @property packageName 应用程序的包名。
 * @property apkPath 应用程序的 APK 文件路径。
 * @property installState 应用程序的安装状态。
 * @property versionName 应用程序的版本名称。
 * @property releaseDate 应用程序的发布日期。
 * @property downloadStatus 应用程序的下载状态。
 * @property progress 应用程序的下载进度。
 */
data class AppInfo(
    val name: String,
    val description: String?,
    val size: String,
    val downloadCount: Int,
    val packageName: String,
    val apkPath: String,
    val installState: InstallState,
    val versionName: String,      // 新增：版本名
    val releaseDate: String,      // 新增：发布日期
    val downloadStatus: DownloadStatus = DownloadStatus.NONE,
    val progress: Int = 0
) : Serializable {

    /**
     * 操作按钮上显示的文本。
     */
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

    /**
     * 操作按钮是否启用。
     */
    val buttonEnabled: Boolean
        get() = when (downloadStatus) {
            DownloadStatus.VERIFYING, DownloadStatus.INSTALLING -> false
            DownloadStatus.NONE -> installState != InstallState.INSTALLED_LATEST
            else -> true // DOWNLOADING, PAUSED
        }

    /**
     * 是否显示进度条。
     */
    val showProgress: Boolean
        get() = when(downloadStatus) {
            DownloadStatus.DOWNLOADING, DownloadStatus.PAUSED, DownloadStatus.VERIFYING, DownloadStatus.INSTALLING -> true
            DownloadStatus.NONE -> false
        }

    /**
     * 是否显示操作按钮。
     */
    val showButton: Boolean
        get() = !showProgress

    /**
     * 用于显示进度的文本。
     */
    val progressText: String
        get() = when (downloadStatus) {
            DownloadStatus.DOWNLOADING -> "$progress%"
            DownloadStatus.PAUSED -> "已暂停"
            DownloadStatus.VERIFYING -> "验证中..."
            DownloadStatus.INSTALLING -> "安装中..."
            DownloadStatus.NONE -> ""
        }
}
