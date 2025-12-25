package com.example.storechat.ui.download

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import com.example.storechat.data.AppRepository
import com.example.storechat.model.AppInfo
import com.example.storechat.model.DownloadTask

class DownloadQueueViewModel : ViewModel() {

    // 监听 Repository 的全局事件消息
    val eventMessage: LiveData<String> = AppRepository.eventMessage

    // region Data for Landscape (Single Task) - DEPRECATED but kept for compatibility
    val activeTask: LiveData<DownloadTask?> = AppRepository.downloadQueue.map { queue ->
        queue.firstOrNull()?.let { app ->
            DownloadTask(
                id = 0,
                app = app,
                progress = app.progress,
                speed = "",
                downloadedSize = app.currentSizeStr, // 修改
                totalSize = if (app.totalSizeStr.isNotEmpty()) app.totalSizeStr else app.size, // 修改：优先用实时总大小
                status = app.downloadStatus
            )
        }
    }

    fun onStatusClick() {
        activeTask.value?.let { AppRepository.toggleDownload(it.app) }
    }

    @Deprecated("Use cancelDownload(task) instead")
    fun cancelDownload() {
        activeTask.value?.let {
            AppRepository.removeDownload(it.app)
        }
        _toastMessage.value = "下载已取消，文件已删除"
    }
    // endregion

    // region Data for Portrait (Multi Task)
    val downloadTasks: LiveData<List<DownloadTask>> = AppRepository.downloadQueue.map { queue ->
        queue.mapIndexed { index, app ->
            DownloadTask(
                id = index.toLong(),
                app = app,
                progress = app.progress,
                speed = "",
                downloadedSize = app.currentSizeStr, // 修改：绑定实时大小
                totalSize = if (app.totalSizeStr.isNotEmpty()) app.totalSizeStr else app.size, // 修改：绑定总大小
                status = app.downloadStatus
            )
        }
    }

    fun onStatusClick(task: DownloadTask) {
        AppRepository.toggleDownload(task.app)
    }

    fun cancelDownload(task: DownloadTask) {
        AppRepository.removeDownload(task.app)
        _toastMessage.value = "下载已取消，文件已删除"
    }
    // endregion

    // region Common Data & Actions
    val recentInstalled: LiveData<List<AppInfo>> = AppRepository.recentInstalledApps

    private val _toastMessage = MutableLiveData<String?>()
    val toastMessage: LiveData<String?> = _toastMessage

    fun resumeAllPausedTasks() {
        AppRepository.resumeAllPausedDownloads()
        _toastMessage.value = "已恢复所有任务"
    }

    fun onToastMessageShown() {
        _toastMessage.value = null
    }
    fun refreshDataFromDb() {
        AppRepository.reloadTasksFromDb()
    }
    // endregion
}