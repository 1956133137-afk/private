package com.example.storechat.ui.download

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import com.example.storechat.data.AppRepository
import com.example.storechat.model.AppInfo
import com.example.storechat.model.DownloadTask
import com.example.storechat.model.DownloadStatus

class DownloadQueueViewModel : ViewModel() {

    // region Data for Landscape (Single Task) & backward compatibility
    val activeTask: LiveData<DownloadTask?> = AppRepository.downloadQueue.map { queue ->
        queue.firstOrNull()?.let {
            val sizeInMb = it.size.replace("MB", "").toFloatOrNull() ?: 0f
            DownloadTask(
                id = 0, // Static ID for the single view
                app = it,
                progress = it.progress,
                speed = "", // This should be calculated based on download progress
                downloadedSize = "${(sizeInMb * it.progress / 100)}MB",
                totalSize = it.size,
                status = it.downloadStatus
            )
        }
    }

    fun onStatusClick() {
        activeTask.value?.let { AppRepository.toggleDownload(it.app) }
    }

    fun cancelDownload() {
        activeTask.value?.let {
            AppRepository.cancelDownload(it.app)
            AppRepository.removeDownload(it.app)
        }
        _toastMessage.value = "下载已取消"
    }
    // endregion

    // region Data for Portrait (Multi Task)
    val downloadTasks: LiveData<List<DownloadTask>> = AppRepository.downloadQueue.map { queue ->
        queue.mapIndexed { index, app ->
            val sizeInMb = app.size.replace("MB", "").toFloatOrNull() ?: 0f
            DownloadTask(
                id = index.toLong(),
                app = app,
                progress = app.progress,
                speed = "speed", // This should be calculated based on download progress
                downloadedSize = "${(sizeInMb * app.progress / 100)}MB",
                totalSize = app.size,
                status = app.downloadStatus
            )
        }
    }

    fun onStatusClick(task: DownloadTask) {
        AppRepository.toggleDownload(task.app)
    }

    fun cancelDownload(task: DownloadTask) {
        AppRepository.cancelDownload(task.app)
        AppRepository.removeDownload(task.app)
        _toastMessage.value = "下载已取消"
    }
    // endregion

    // region Common Data & Actions
    val recentInstalled: LiveData<List<AppInfo>> = AppRepository.recentInstalledApps

    private val _toastMessage = MutableLiveData<String?>()
    val toastMessage: LiveData<String?> = _toastMessage

    /** 顶部「全部继续」按钮 */
    fun resumeAllPausedTasks() {
        AppRepository.resumeAllPausedDownloads()
        _toastMessage.value = "已全部继续"
    }

    fun onToastMessageShown() {
        _toastMessage.value = null
    }
    // endregion
}
