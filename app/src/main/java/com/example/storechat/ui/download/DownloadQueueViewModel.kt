package com.example.storechat.ui.download

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import com.example.storechat.data.AppRepository
import com.example.storechat.model.AppInfo
import com.example.storechat.model.DownloadStatus
import com.example.storechat.model.DownloadTask

/**
 * 下载队列界面的ViewModel类
 * 负责管理下载队列页面的数据逻辑
 */
class DownloadQueueViewModel : ViewModel() {

    //================================================================
    //      数据源
    //================================================================

    // 从AppRepository获取下载任务
    val activeTask: LiveData<DownloadTask?> = AppRepository.downloadQueue.map { queue ->
        queue.firstOrNull()?.let { app ->
            DownloadTask(
                app = app,
                progress = app.progress,
                speed = "", // 此处应有真实的速度数据
                downloadedSize = "${(app.size.replace("MB", "").toFloat() * app.progress / 100)}MB",
                totalSize = app.size,
                status = app.downloadStatus
            )
        }
    }

    // 最近安装的应用列表
    val recentInstalled: LiveData<List<AppInfo>> = AppRepository.recentInstalledApps

    // Toast消息事件
    private val _toastMessage = MutableLiveData<String?>()
    val toastMessage: LiveData<String?> = _toastMessage

    //================================================================
    //      UI绑定数据 (用于DataBinding)
    //================================================================

    /**
     * 下载标题文本
     * 显示正在下载的应用数量
     */
    val downloadingTitleText: LiveData<String> = activeTask.map {
        if (it != null) "应用名称(1)" else "应用名称(0)"
    }

    /**
     * 进度文本
     * 显示下载进度百分比
     */
    val progressText: LiveData<String> = activeTask.map {
        it?.let { task -> String.format("%.1f%%", task.progress * 1.0) } ?: "0.0%"
    }

    /**
     * 文件大小信息文本
     * 显示已下载大小和总大小
     */
    val sizeInfoText: LiveData<String> = activeTask.map {
        it?.let { task -> "${task.downloadedSize}/${task.totalSize}" } ?: "0MB/0MB"
    }

    /**
     * 应用名称
     * 显示当前下载的应用名称
     */
    val appName: LiveData<String> = activeTask.map {
        it?.app?.name ?: ""
    }

    /**
     * 下载速度文本
     * 显示当前下载速度
     */
    val speedText: LiveData<String> = activeTask.map {
        it?.speed ?: "0 KB/s"
    }

    /**
     * 进度值
     * 用于ProgressBar控件显示下载进度
     */
    val progressValue: LiveData<Int> = activeTask.map {
        it?.progress ?: 0
    }

    //================================================================
    //      用户操作处理
    //================================================================

    /**
     * 处理状态按钮点击事件
     * 切换下载状态（暂停/继续）
     */
    fun onStatusClick() {
        activeTask.value?.let { AppRepository.toggleDownload(it.app) }
    }

    /**
     * 恢复所有已暂停的任务
     * 在横屏布局中通过"全部继续"按钮触发
     */
    fun resumeAllPausedTasks() {
        _toastMessage.value = "已全部继续"
    }

    /**
     * 取消当前下载任务
     * 通过取消按钮触发
     */
    fun cancelDownload() {
        activeTask.value?.let { AppRepository.cancelDownload(it.app) }
        _toastMessage.value = "下载已取消"
    }

    /**
     * 处理Toast消息已显示事件
     * 清除消息以避免重复显示
     */
    fun onToastMessageShown() {
        _toastMessage.value = null
    }
}
