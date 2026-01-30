package com.example.storechat.model


enum class DownloadStatus {
    NONE,
    DOWNLOADING,
    PAUSED,
    VERIFYING,
    INSTALLING
}

data class DownloadTask(
    val id: Long,               // 新增ID，用于列表更新
    val app: AppInfo,
    val speed: String,          // 当前速度，如 "1.8MB/s"
    val downloadedSize: String, // 已下大小，如 "1.8MB"
    val totalSize: String,      // 总大小，如 "83.00MB"
    val progress: Int,          // 进度百分比 0~100
    val status: DownloadStatus
) {

    val statusButtonText: String
        get() = when (status) {
            DownloadStatus.NONE -> when (app.installState) {
                InstallState.INSTALLED_LATEST -> "打开"
                InstallState.INSTALLED_OLD -> "升级"
                else -> "安装"
            }
            DownloadStatus.DOWNLOADING -> "暂停"
            DownloadStatus.PAUSED -> "继续"
            DownloadStatus.VERIFYING -> "验证中"
            DownloadStatus.INSTALLING -> "安装中"
        }

    val rightText: String
        get() = when (status) {
            DownloadStatus.NONE -> statusButtonText
            DownloadStatus.DOWNLOADING -> {
                if (progress >= 100) "安装中" else progressText
            }
            DownloadStatus.PAUSED -> "继续"
            DownloadStatus.VERIFYING -> "验证中"
            DownloadStatus.INSTALLING -> "安装中"
        }

    val statusButtonEnabled: Boolean
        get() = (status == DownloadStatus.DOWNLOADING && progress < 100) ||
                status == DownloadStatus.PAUSED ||
                status == DownloadStatus.NONE

    val progressText: String
        get() = "$progress%"
}