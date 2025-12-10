package com.example.storechat.data

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import com.example.storechat.data.api.ApiClient
import com.example.storechat.data.api.AppHistoryRequest
import com.example.storechat.data.api.AppListRequest
import com.example.storechat.data.api.CheckUpdateRequest
import com.example.storechat.data.api.DownloadLinkRequest
import com.example.storechat.model.AppCategory
import com.example.storechat.model.AppInfo
import com.example.storechat.model.DownloadStatus
import com.example.storechat.model.HistoryVersion
import com.example.storechat.model.InstallState
import com.example.storechat.model.UpdateStatus
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

    init {
        coroutineScope.launch {
            refreshAppsFromServer(AppCategory.YANNUO)
        }
    }

    private fun updateAppStatus(packageName: String, transform: (AppInfo) -> AppInfo) {
        synchronized(stateLock) {
            // Use packageName (which is now appId) as the key
            val app = localAllApps.find { it.packageName == packageName } ?: return
            val newApp = transform(app)

            localAllApps = localAllApps.map { if (it.packageName == packageName) newApp else it }

            if (localDownloadQueue.any { it.packageName == packageName }) {
                localDownloadQueue = localDownloadQueue.map { if (it.packageName == packageName) newApp else it }
                _downloadQueue.postValue(localDownloadQueue)
            }

            _allApps.postValue(localAllApps)
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

    fun selectCategory(category: AppCategory) {
        _selectedCategory.postValue(category)
        coroutineScope.launch {
            refreshAppsFromServer(category)
        }
    }

    private suspend fun refreshAppsFromServer(category: AppCategory) {
        try {
            val response = apiService.getAppList(
                AppListRequest(appId = null, appCategory = category.id)
            )

            if (response.code == 200) {
                val remoteList = response.data
                synchronized(stateLock) {
                    val localAppsMap = localAllApps.associateBy { it.appId }

                    val mergedRemoteList = remoteList.map { serverApp ->
                        val localApp = localAppsMap[serverApp.appId]
                        val newAppInfo = AppInfo(
                                name = serverApp.productName,
                                appId = serverApp.appId,
                                category = AppCategory.from(serverApp.appCategory) ?: AppCategory.YANNUO,
                                createTime = serverApp.createTime,
                                updateTime = serverApp.updateTime,
                                remark = serverApp.remark,
                                description = serverApp.remark ?: "", // Use remark as description
                                size = "N/A", // Placeholder
                                downloadCount = 0, // Placeholder
                                packageName = serverApp.appId, // Use appId as a temporary substitute for packageName
                                apkPath = "", // Placeholder, critical for install
                                installState = localApp?.installState ?: InstallState.NOT_INSTALLED,
                                versionName = "1.0.0", // Placeholder
                                releaseDate = serverApp.createTime, // Use createTime as releaseDate
                                downloadStatus = localApp?.downloadStatus ?: DownloadStatus.NONE,
                                progress = localApp?.progress ?: 0
                        )
                        newAppInfo
                    }

                    val otherCategoryApps = localAllApps.filter { it.category != category }
                    localAllApps = otherCategoryApps + mergedRemoteList
                    _allApps.postValue(localAllApps)
                }
            }
        } catch (e: Exception) {
            // On network error or other issues, we keep the existing local data to be safe.
            e.printStackTrace()
        }
    }

    private suspend fun resolveDownloadApkPath(
        packageName: String,
        fallbackApkPath: String,
        versionName: String?
    ): String {
        return try {
            val resp = apiService.getDownloadLink(
                DownloadLinkRequest(
                    packageName = packageName,
                    versionName = versionName
                )
            )
            if (resp.url.isBlank()) fallbackApkPath else resp.url
        } catch (e: Exception) {
            fallbackApkPath
        }
    }

    fun toggleDownload(app: AppInfo) {
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
                        val realApkPath = resolveDownloadApkPath(
                            packageName = app.packageName,
                            fallbackApkPath = app.apkPath,
                            versionName = app.versionName
                        )

                        updateAppStatus(app.packageName) {
                            it.copy(downloadStatus = DownloadStatus.DOWNLOADING)
                        }

                        for (p in app.progress..100) {
                            delay(80L)
                            updateAppStatus(app.packageName) { it.copy(progress = p) }
                        }

                        updateAppStatus(app.packageName) {
                            it.copy(downloadStatus = DownloadStatus.VERIFYING)
                        }
                        delay(500)

                        updateAppStatus(app.packageName) {
                            it.copy(downloadStatus = DownloadStatus.INSTALLING)
                        }
                        XcServiceManager.installApk(realApkPath, app.packageName, true)
                        delay(1500)

                        var installedApp: AppInfo? = null
                        updateAppStatus(app.packageName) {
                            installedApp = it.copy(
                                downloadStatus = DownloadStatus.NONE,
                                progress = 0,
                                installState = InstallState.INSTALLED_LATEST
                            )
                            installedApp!!
                        }

                        downloadJobs.remove(app.packageName)
                        removeFromDownloadQueue(app.packageName)
                        installedApp?.let { addToRecentInstalled(it) }

                    } catch (e: Exception) {
                        val packageName = app.packageName
                        val wasDeletion = cancellationsForDeletion.remove(packageName)

                        if (e is kotlinx.coroutines.CancellationException) {
                            if (wasDeletion) {
                                updateAppStatus(packageName) {
                                    it.copy(downloadStatus = DownloadStatus.NONE, progress = 0)
                                }
                            } else {
                                updateAppStatus(packageName) {
                                    it.copy(downloadStatus = DownloadStatus.PAUSED)
                                }
                            }
                            downloadJobs.remove(packageName)
                            throw e
                        } else {
                            updateAppStatus(packageName) {
                                it.copy(downloadStatus = DownloadStatus.PAUSED)
                            }
                            downloadJobs.remove(packageName)
                        }
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
            val versions = apiService.getAppHistory(
                AppHistoryRequest(packageName = app.packageName)
            )

            if (versions.isNotEmpty()) {
                versions.map {
                    HistoryVersion(
                        versionName = it.versionName,
                        apkPath = it.apkPath
                    )
                }
            } else {
                buildFakeHistory(app)
            }
        } catch (e: Exception) {
            buildFakeHistory(app)
        }
    }

    private fun buildFakeHistory(app: AppInfo): List<HistoryVersion> {
        return listOf(
            HistoryVersion("1.0.2", "/sdcard/apks/${app.packageName}_102.apk"),
            HistoryVersion("1.0.1", "/sdcard/apks/${app.packageName}_101.apk"),
            HistoryVersion("1.0.0", "/sdcard/apks/${app.packageName}_100.apk")
        )
    }

    fun installHistoryVersion(packageName: String, historyVersion: HistoryVersion) {
        coroutineScope.launch {
            val realApkPath = resolveDownloadApkPath(
                packageName = packageName,
                fallbackApkPath = historyVersion.apkPath,
                versionName = historyVersion.versionName
            )
            XcServiceManager.installApk(realApkPath, packageName, true)
        }
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
                // NOTE: We are using a hardcoded appId for now. 
                // This should be replaced with the actual app's ID.
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
                _checkUpdateResult.postValue(UpdateStatus.LATEST) // Fail silently
            }
        }
    }

    fun clearUpdateResult() {
        _checkUpdateResult.postValue(null)
    }
}
