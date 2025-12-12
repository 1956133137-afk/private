package com.example.storechat.data

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import com.example.storechat.data.api.ApiClient
import com.example.storechat.data.api.AppInfoResponse
import com.example.storechat.data.api.AppListRequestBody
import com.example.storechat.data.api.AppVersionHistoryRequest
import com.example.storechat.data.api.CheckUpdateRequest
import com.example.storechat.data.api.DownloadLinkRequest
import com.example.storechat.model.AppCategory
import com.example.storechat.model.AppInfo
import com.example.storechat.model.DownloadStatus
import com.example.storechat.model.HistoryVersion
import com.example.storechat.model.InstallState
import com.example.storechat.model.UpdateStatus
import com.example.storechat.util.AppUtils
import com.example.storechat.xc.XcServiceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

object AppRepository {

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val downloadJobs = ConcurrentHashMap<String, Job>()
    private val cancellationsForDeletion = ConcurrentHashMap.newKeySet<String>()

    private val apiService = ApiClient.appApi

    private val stateLock = Any()

    private var localAllApps: List<AppInfo> = emptyList()
    private var localDownloadQueue: List<AppInfo> = emptyList()
    private var localRecentApps: List<AppInfo> = emptyList()

    private val _allApps = MutableLiveData<List<AppInfo>>()
    val allApps: LiveData<List<AppInfo>> = _allApps

    private val _downloadQueue = MutableLiveData<List<AppInfo>>()
    val downloadQueue: LiveData<List<AppInfo>> = _downloadQueue

    private val _recentInstalledApps = MutableLiveData<List<AppInfo>>()
    val recentInstalledApps: LiveData<List<AppInfo>> = _recentInstalledApps

    private val _appVersion = MutableLiveData("V1.0.0")
    val appVersion: LiveData<String> = _appVersion

    private val _checkUpdateResult = MutableLiveData<UpdateStatus?>()
    val checkUpdateResult: LiveData<UpdateStatus?> = _checkUpdateResult

    private val _selectedCategory = MutableLiveData(AppCategory.YANNUO)

    val categorizedApps: LiveData<List<AppInfo>> = MediatorLiveData<List<AppInfo>>().apply {
        addSource(allApps) { apps ->
            value = apps.filter { it.category == _selectedCategory.value }
        }
        addSource(_selectedCategory) { category ->
            value = allApps.value?.filter { it.category == category }
        }
    }

    fun initialize(context: Context) {
        coroutineScope.launch {
            refreshAppsFromServer(context, AppCategory.YANNUO)
        }
    }

    private fun updateAppStatus(packageName: String, transform: (AppInfo) -> AppInfo) {
        synchronized(stateLock) {
            var appToTransform: AppInfo? = null
            var appExistsInAllApps = false

            // First, try to find the app in the main list
            val initialApp = localAllApps.find { it.packageName == packageName }
            if (initialApp != null) {
                appToTransform = initialApp
                appExistsInAllApps = true
            } else {
                // If not in main list, it might be a temporary app (e.g., history download)
                // Look for it in the download queue
                appToTransform = localDownloadQueue.find { it.packageName == packageName }
            }

            if (appToTransform == null) return

            val newApp = transform(appToTransform)

            // Update localAllApps if the app was found there
            if (appExistsInAllApps) {
                localAllApps = localAllApps.map { if (it.packageName == packageName) newApp else it }
            }

            // Always update the download queue
            if (localDownloadQueue.any { it.packageName == packageName }) {
                localDownloadQueue = localDownloadQueue.map { if (it.packageName == packageName) newApp else it }
                _downloadQueue.postValue(localDownloadQueue)
            }

            // Post the updated main list
            if (appExistsInAllApps) {
                _allApps.postValue(localAllApps)
            }
        }
    }

    private fun addToDownloadQueue(app: AppInfo) {
        synchronized(stateLock) {
            if (localDownloadQueue.none { it.packageName == app.packageName }) {
                localDownloadQueue = localDownloadQueue + app
                _downloadQueue.postValue(localDownloadQueue)
            }
        }
    }

    private fun removeFromDownloadQueue(packageName: String) {
        synchronized(stateLock) {
            localDownloadQueue = localDownloadQueue.filterNot { it.packageName == packageName }
            _downloadQueue.postValue(localDownloadQueue)
        }
    }

    private fun addToRecentInstalled(app: AppInfo) {
        synchronized(stateLock) {
            if (localRecentApps.none { it.packageName == app.packageName }) {
                localRecentApps = listOf(app) + localRecentApps
                _recentInstalledApps.postValue(localRecentApps)
            }
        }
    }

    fun selectCategory(context: Context, category: AppCategory) {
        _selectedCategory.postValue(category)
        coroutineScope.launch {
            refreshAppsFromServer(context, category)
        }
    }

    private suspend fun refreshAppsFromServer(context: Context, category: AppCategory?) {
        try {
            val response = apiService.getAppList(
                AppListRequestBody(appCategory = category?.id)
            )

            if (response.code == 200 && response.data != null) {
                val remoteList = response.data
                synchronized(stateLock) {
                    val localAppsMap = localAllApps.associateBy { it.appId }

                    val mergedRemoteList = remoteList.map { serverApp ->
                        val localApp = localAppsMap[serverApp.appId]
                        val installedVersionCode = AppUtils.getInstalledVersionCode(context, serverApp.appId)
                        val isInstalled = installedVersionCode != -1L

                        val installState = when {
                            !isInstalled -> InstallState.NOT_INSTALLED
                            (serverApp.id?.toLong() ?: 0) > installedVersionCode -> InstallState.INSTALLED_OLD
                            else -> InstallState.INSTALLED_LATEST
                        }

                        mapToAppInfo(serverApp, localApp).copy(installState = installState, isInstalled = isInstalled)
                    }

                    val otherCategoryApps = localAllApps.filter { it.category != category }
                    localAllApps = otherCategoryApps + mergedRemoteList
                    _allApps.postValue(localAllApps)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun mapToAppInfo(response: AppInfoResponse, localApp: AppInfo?): AppInfo {
        return AppInfo(
            name = response.productName,
            appId = response.appId,
            versionId = response.id?.toLong(),
            category = AppCategory.from(response.appCategory) ?: AppCategory.YANNUO,
            createTime = response.createTime,
            updateTime = response.updateTime,
            remark = response.remark,
            description = response.versionDesc ?: "",
            size = "N/A", // Placeholder
            downloadCount = 0, // Placeholder
            packageName = response.appId,
            apkPath = "", // Placeholder
            installState = localApp?.installState ?: InstallState.NOT_INSTALLED,
            versionName = response.version ?: "N/A",
            releaseDate = response.updateTime ?: response.createTime,
            downloadStatus = localApp?.downloadStatus ?: DownloadStatus.NONE,
            progress = localApp?.progress ?: 0
        )
    }

    private suspend fun resolveDownloadApkPath(appId: String, versionId: Long): String {
        return try {
            val response = apiService.getDownloadLink(DownloadLinkRequest(appId = appId, id = versionId))
            if (response.code == 200 && response.data != null) {
                response.data.fileUrl
            } else {
                Log.e("DOWNLOAD", "API error getting download link: ${response.msg}")
                ""
            }
        } catch (e: Exception) {
            Log.e("DOWNLOAD", "Failed to resolve download URL for versionId: $versionId", e)
            ""
        }
    }

    fun toggleDownload(app: AppInfo) {
        val versionId = app.versionId
        if (versionId == null) {
            Log.e("DOWNLOAD", "Cannot download ${app.name}, versionId is null.")
            return
        }

        when (app.downloadStatus) {
            DownloadStatus.DOWNLOADING -> {
                downloadJobs[app.packageName]?.cancel()
            }

            DownloadStatus.NONE, DownloadStatus.PAUSED -> {
                if (downloadJobs.containsKey(app.packageName)) {
                    return
                }

                addToDownloadQueue(app)

                val newJob = coroutineScope.launch(start = CoroutineStart.LAZY) {
                    try {
                        val realApkPath = resolveDownloadApkPath(app.appId, versionId)
                        if (realApkPath.isBlank()) {
                            throw IllegalStateException("Download URL is blank for versionId $versionId")
                        }

                        updateAppStatus(app.packageName) {
                            it.copy(downloadStatus = DownloadStatus.DOWNLOADING, progress = 0)
                        }

                        val installedPackageName = XcServiceManager.downloadAndInstall(
                            url = realApkPath,
                            onProgress = { percent ->
                                updateAppStatus(app.packageName) { current ->
                                    current.copy(progress = percent)
                                }
                            }
                        )

                        if (installedPackageName == null) {
                            throw IllegalStateException("Download or install failed for ${app.name}")
                        }

                        val masterAppInfo = synchronized(stateLock) { localAllApps.find { it.packageName == app.packageName } }
                        val latestVersionId = masterAppInfo?.versionId ?: app.versionId

                        val newInstallState = if (app.versionId < latestVersionId) {
                            InstallState.INSTALLED_OLD
                        } else {
                            InstallState.INSTALLED_LATEST
                        }

                        updateAppStatus(app.packageName) {
                            it.copy(downloadStatus = DownloadStatus.NONE, progress = 0, installState = newInstallState)
                        }

                        downloadJobs.remove(app.packageName)
                        removeFromDownloadQueue(app.packageName)
                        addToRecentInstalled(app.copy(installState = newInstallState))

                    } catch (e: kotlinx.coroutines.CancellationException) {
                        val packageName = app.packageName
                        val wasDeletion = cancellationsForDeletion.remove(packageName)

                        val finalStatus = if (wasDeletion) DownloadStatus.NONE else DownloadStatus.PAUSED
                        val finalProgress = if (wasDeletion) 0 else app.progress

                        updateAppStatus(packageName) {
                            it.copy(downloadStatus = finalStatus, progress = finalProgress)
                        }
                        downloadJobs.remove(app.packageName)
                        if (!wasDeletion) throw e

                    } catch (e: Exception) {
                        Log.e("DOWNLOAD", "Download/Install failed for ${app.name}", e)
                        updateAppStatus(app.packageName) {
                            it.copy(downloadStatus = DownloadStatus.PAUSED)
                        }
                        downloadJobs.remove(app.packageName)
                    }
                }

                downloadJobs[app.packageName] = newJob
                newJob.start()
            }

            DownloadStatus.VERIFYING, DownloadStatus.INSTALLING -> {
                // Ignore clicks
            }
        }
    }


    fun cancelDownload(app: AppInfo) {
        val packageName = app.packageName
        val job = downloadJobs[packageName]

        removeFromDownloadQueue(packageName)

        if (job != null) {
            cancellationsForDeletion.add(packageName)
            job.cancel()
        } else {
            updateAppStatus(packageName) {
                it.copy(downloadStatus = DownloadStatus.NONE, progress = 0)
            }
            cancellationsForDeletion.remove(packageName)
        }
    }

    fun removeDownload(app: AppInfo) {
        removeFromDownloadQueue(app.packageName)
    }

    suspend fun loadHistoryVersions(app: AppInfo): List<HistoryVersion> {
        return try {
            val response = apiService.getAppHistory(
                AppVersionHistoryRequest(appId = app.appId)
            )
            if (response.code == 200 && response.data != null) {
                response.data.map { versionItem ->
                    HistoryVersion(
                        versionId = versionItem.id,
                        versionName = versionItem.version,
                        apkPath = versionItem.versionDesc ?: versionItem.remark ?: ""
                    )
                }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun installHistoryVersion(app: AppInfo, historyVersion: HistoryVersion) {
        // Create a temporary AppInfo for the specific historical version
        val historyAppInfo = app.copy(
            versionId = historyVersion.versionId,
            versionName = historyVersion.versionName,
            downloadStatus = DownloadStatus.NONE, // Reset status to allow download
            progress = 0
        )
        // Use the unified download logic
        toggleDownload(historyAppInfo)
    }

    fun resumeAllPausedDownloads() {
        val pausedApps = synchronized(stateLock) {
            localDownloadQueue.filter { it.downloadStatus == DownloadStatus.PAUSED }
        }
        pausedApps.forEach {
            toggleDownload(it)
        }
    }

    fun checkAppUpdate() {
        coroutineScope.launch {
            try {
                val appId = "32DQY9LH260HX43U"
                val currentVersion = _appVersion.value?.removePrefix("V") ?: "1.0.0"

                val latestVersionInfo = apiService.checkUpdate(
                    CheckUpdateRequest(
                        packageName = appId,
                        currentVer = currentVersion
                    )
                )

                if (latestVersionInfo != null && latestVersionInfo.versionName > currentVersion) {
                    _checkUpdateResult.postValue(UpdateStatus.NEW_VERSION(latestVersionInfo.versionName))
                } else {
                    _checkUpdateResult.postValue(UpdateStatus.LATEST)
                }
            } catch (e: Exception) {
                _checkUpdateResult.postValue(UpdateStatus.LATEST)
            }
        }
    }

    fun clearUpdateResult() {
        _checkUpdateResult.postValue(null)
    }
}
