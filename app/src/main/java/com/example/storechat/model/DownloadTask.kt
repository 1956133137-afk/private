package com.example.storechat.model

// 将所有下载状态统一在此处定义
enum class DownloadStatus {
    NONE,          // 未下载
    DOWNLOADING,   // 下载中
    PAUSED,        // 已暂停
    VERIFYING,     // 验证中
    INSTALLING     // 安装中
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
            // 【修改】同步首页 AppInfo 的逻辑：根据安装状态显示 安装/升级/打开
            DownloadStatus.NONE -> when (app.installState) {
                InstallState.INSTALLED_LATEST -> "打开"
                InstallState.INSTALLED_OLD -> "升级"
                else -> "安装"
            }
            DownloadStatus.DOWNLOADING -> "暂停"
            DownloadStatus.PAUSED -> "继续" // 【统一】建议统一为"继续"，语义更准确
            DownloadStatus.VERIFYING -> "验证中"
            DownloadStatus.INSTALLING -> "安装中"
        }

    // 右侧胶囊里显示的文字（横屏列表使用）
    val rightText: String
        get() = when (status) {
            // 【修改】未下载/已取消时，显示"安装/升级"而不是空白，保持与首页一致的交互提示
            DownloadStatus.NONE -> statusButtonText
            DownloadStatus.DOWNLOADING -> {
                // 【修改】下载完成时显示"安装中"
                if (progress >= 100) "安装中" else progressText
            }
            DownloadStatus.PAUSED -> "继续"               // 暂停：显示"继续"
            DownloadStatus.VERIFYING -> "验证中"          // 验证中
            DownloadStatus.INSTALLING -> "安装中"          // 安装中
        }

    // 按钮是否可点击（防止在验证/安装中重复点击）
    val statusButtonEnabled: Boolean
        get() = (status == DownloadStatus.DOWNLOADING && progress < 100) ||
                status == DownloadStatus.PAUSED ||
                status == DownloadStatus.NONE
//

    val progressText: String
        get() = "$progress%"
}