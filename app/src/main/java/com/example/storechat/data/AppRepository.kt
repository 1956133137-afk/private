package com.example.storechat.data

import android.content.Context
import com.example.storechat.util.LogUtil
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import com.example.storechat.data.api.*
import com.example.storechat.data.db.AppDatabase
import com.example.storechat.data.db.DownloadDao
import com.example.storechat.data.db.DownloadEntity
import com.example.storechat.model.*
import com.example.storechat.util.AppPackageNameCache
import com.example.storechat.util.AppUtils
import com.example.storechat.xc.XcServiceManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object AppRepository {

    private const val TAG = "AppRepository"
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private lateinit var downloadDao: DownloadDao

    private val downloadJobs = ConcurrentHashMap<String, Job>()

    private val refreshJobs = ConcurrentHashMap<Int, Job>()
    private val lastFetchAt = ConcurrentHashMap<Int, Long>()
    private const val MIN_FETCH_INTERVAL_MS = 1500L

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

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _selectedCategory = MutableLiveData(AppCategory.YANNUO)

    val categorizedApps: LiveData<List<AppInfo>> = MediatorLiveData<List<AppInfo>>().apply {
        addSource(allApps) { apps ->
            value = apps.filter { it.category == _selectedCategory.value }
        }
        addSource(_selectedCategory) { category ->
            value = allApps.value?.filter { it.category == category }
        }
    }

    private fun taskKey(appId: String, versionId: Long): String = "$appId@$versionId"

    private fun taskKey(app: AppInfo): String? {
        val vid = app.versionId ?: return null
        return taskKey(app.appId, vid)
    }

    private fun updateAppStatus(
        appId: String,
        versionId: Long?,
        updateMasterList: Boolean, // ★ 控制是否更新主列表
        transform: (AppInfo) -> AppInfo
    ) {
        synchronized(stateLock) {
            val key = versionId?.let { taskKey(appId, it) }

            var masterAppUpdated = false
            if (updateMasterList) {
                localAllApps = localAllApps.map {
                    if (it.appId == appId) {
                        masterAppUpdated = true
                        transform(it)
                    } else {
                        it
                    }
                }
            }

            var queueItemUpdated = false
            if (key != null) {
                localDownloadQueue = localDownloadQueue.map {
                    if (taskKey(it) == key) {
                        queueItemUpdated = true
                        transform(it).also { newAppInfo ->
                            saveToDatabase(newAppInfo)
                        }
                    } else {
                        it
                    }
                }
            }

            if (queueItemUpdated) _downloadQueue.postValue(localDownloadQueue)
            if (masterAppUpdated) _allApps.postValue(localAllApps)
        }
    }

    private fun saveToDatabase(app: AppInfo) {
        coroutineScope.launch {
            val vid = app.versionId ?: return@launch
            val key = taskKey(app.appId, vid)

            val entity = DownloadEntity(
                taskKey = key,
                appId = app.appId,
                versionId = vid,
                packageName = app.packageName,
                name = app.name,
                categoryId = app.category.id,
                downloadUrl = "",
                savePath = app.apkPath ?: "",
                progress = app.progress,
                status = app.downloadStatus.ordinal
            )
            downloadDao.insertOrUpdate(entity)
        }
    }

    private fun addToDownloadQueue(app: AppInfo) {
        val key = taskKey(app) ?: return
        synchronized(stateLock) {
            if (localDownloadQueue.none { taskKey(it) == key }) {
                localDownloadQueue = localDownloadQueue + app
                _downloadQueue.postValue(localDownloadQueue)
                saveToDatabase(app)
            }
        }
    }

    private fun removeFromDownloadQueue(taskKey: String) {
        synchronized(stateLock) {
            localDownloadQueue = localDownloadQueue.filterNot { taskKey(it) == taskKey }
            _downloadQueue.postValue(localDownloadQueue)

            coroutineScope.launch {
                downloadDao.deleteTask(taskKey)
            }
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

    fun initialize(context: Context) {
        val db = AppDatabase.getDatabase(context)
        downloadDao = db.downloadDao()

        coroutineScope.launch {
            val entities = downloadDao.getAllTasks()

            val restoredQueue = entities.map { entity ->
                var status = DownloadStatus.values().getOrElse(entity.status) { DownloadStatus.NONE }
                if (status == DownloadStatus.DOWNLOADING) {
                    status = DownloadStatus.PAUSED
                }

                if (status == DownloadStatus.PAUSED && entity.savePath.isNotEmpty()) {
                    if (!File(entity.savePath).exists()) {
                        status = DownloadStatus.NONE
                    }
                }

                val finalProgress = if (status == DownloadStatus.NONE) 0 else entity.progress

                AppInfo(
                    name = entity.name,
                    appId = entity.appId,
                    versionId = entity.versionId,
                    category = AppCategory.values().find { it.id == entity.categoryId } ?: AppCategory.YANNUO,
                    createTime = null, updateTime = null, remark = null, description = "",
                    size = "N/A", downloadCount = 0,
                    packageName = entity.packageName,
                    apkPath = entity.savePath,
                    installState = InstallState.NOT_INSTALLED,
                    versionName = "已暂停", releaseDate = "",
                    downloadStatus = status, progress = finalProgress
                )
            }.filter { it.downloadStatus != DownloadStatus.NONE }

            synchronized(stateLock) {
                localDownloadQueue = restoredQueue
                _downloadQueue.postValue(localDownloadQueue)
            }

            requestAppList(context, AppCategory.YANNUO)
        }
    }

    fun selectCategory(context: Context, category: AppCategory) {
        _selectedCategory.postValue(category)
        requestAppList(context, category)
    }

    private fun requestAppList(context: Context, category: AppCategory) {
        val key = category.id
        val now = System.currentTimeMillis()

        if (now - (lastFetchAt[key] ?: 0L) < MIN_FETCH_INTERVAL_MS) return
        if (refreshJobs[key]?.isActive == true) return

        lastFetchAt[key] = now
        refreshJobs[key] = coroutineScope.launch {
            refreshAppsFromServer(context, category)
        }
    }

    private suspend fun refreshAppsFromServer(context: Context, category: AppCategory?) {
        _isLoading.postValue(true)
        try {
            LogUtil.d(TAG, "Starting request for app list, category: ${category?.id}")
            val response = apiService.getAppList(AppListRequestBody(appCategory = category?.id))
            LogUtil.d(TAG, "Received response for app list, category: ${category?.id}")

            if (response.code == 200 && response.data != null) {
                LogUtil.d(TAG, "Response successful, data count: ${response.data.size}")
                val distinctList = response.data
                    .groupBy { it.appId }
                    .map { (_, apps) -> apps.maxByOrNull { it.id ?: -1 }!! }

                LogUtil.d(TAG, "Distinct list size: ${distinctList.size}")

                synchronized(stateLock) {
                    val localAppsMap = localAllApps.associateBy { it.appId }

                    val mergedRemoteList = distinctList.map { serverApp ->
                        val localApp = localAppsMap[serverApp.appId]

                        var realPackageName = AppPackageNameCache.getPackageNameByAppId(serverApp.appId)
                        if (realPackageName == null) {
                            realPackageName = AppPackageNameCache.getPackageNameByName(serverApp.productName)
                            if (realPackageName != null) {
                                AppPackageNameCache.saveMapping(serverApp.appId, serverApp.productName, realPackageName)
                            }
                        }

                        val finalPackageName = realPackageName ?: serverApp.appId
                        val installedVersionCode = AppUtils.getInstalledVersionCode(context, finalPackageName)
                        val isInstalled = installedVersionCode != -1L

                        val serverVersionCode = serverApp.versionCode?.toLongOrNull()

                        val installState = when {
                            !isInstalled -> InstallState.NOT_INSTALLED
                            serverVersionCode != null && serverVersionCode > installedVersionCode -> InstallState.INSTALLED_OLD
                            else -> InstallState.INSTALLED_LATEST
                        }

                        val queueItem = localDownloadQueue.find {
                            it.appId == serverApp.appId && it.versionId == serverApp.id?.toLong()
                        }

                        val baseInfo = mapToAppInfo(serverApp, localApp).copy(
                            packageName = finalPackageName,
                            installState = installState,
                            isInstalled = isInstalled
                        )

                        queueItem?.let {
                            baseInfo.copy(
                                downloadStatus = it.downloadStatus,
                                progress = it.progress,
                                apkPath = it.apkPath
                            )
                        } ?: baseInfo
                    }

                    val otherCategoryApps = localAllApps.filter { it.category != category }
                    localAllApps = otherCategoryApps + mergedRemoteList
                    _allApps.postValue(localAllApps)
                }
                _isLoading.postValue(false)
            } else {
                LogUtil.w(TAG, "Response not successful, code: ${response.code}, message: ${response.msg}")
                _allApps.postValue(localAllApps.filter { it.category == category })
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error refreshing apps from server", e)
            _allApps.postValue(localAllApps.filter { it.category == category })
        }
    }

    private fun mapToAppInfo(response: AppInfoResponse, localApp: AppInfo?): AppInfo {
        return AppInfo(
            name = response.productName,
            appId = response.appId,
            versionId = response.id?.toLong(),
            category = AppCategory.from(response.appCategory) ?: AppCategory.YANNUO,
            createTime = response.createTime, updateTime = response.updateTime, remark = response.remark,
            description = response.versionDesc ?: "",
            size = "N/A", downloadCount = 0,
            packageName = response.appId, apkPath = "",
            installState = localApp?.installState ?: InstallState.NOT_INSTALLED,
            versionName = response.version ?: "N/A",
            releaseDate = response.updateTime ?: response.createTime,
            downloadStatus = localApp?.downloadStatus ?: DownloadStatus.NONE,
            progress = localApp?.progress ?: 0
        )
    }

    private suspend fun resolveDownloadApkPath(appId: String, versionId: Long): String {
        return try {
            val response = apiService.getDownloadUrl(GetDownloadUrlRequest(appId = appId, id = versionId))
            if (response.code == 200 && response.data != null) {
                response.data.fileUrl
            } else {
                LogUtil.e(TAG, "API error getting download link: ${response.msg}")
                ""
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to resolve download URL for versionId: $versionId", e)
            ""
        }
    }

    fun toggleDownload(app: AppInfo) {
        val versionId = app.versionId
        if (versionId == null) {
            LogUtil.e(TAG, "Cannot download ${app.name}, versionId is null.")
            return
        }

        val masterAppInfo = synchronized(stateLock) { localAllApps.find { it.appId == app.appId } }
        val isLatestVersion = masterAppInfo == null || versionId >= (masterAppInfo.versionId ?: -1)

        val key = taskKey(app.appId, versionId)
        val isDownloading = downloadJobs.containsKey(key)
        val isPaused = app.downloadStatus == DownloadStatus.PAUSED

        if (isDownloading) {
            downloadJobs[key]?.cancel()
            return
        }

        if (isPaused) {
            updateAppStatus(app.appId, versionId, isLatestVersion) {
                it.copy(downloadStatus = DownloadStatus.DOWNLOADING)
            }
        } else {
            addToDownloadQueue(app)
            updateAppStatus(app.appId, versionId, isLatestVersion) {
                it.copy(downloadStatus = DownloadStatus.DOWNLOADING, progress = 0)
            }
        }

        val newJob = coroutineScope.launch(start = CoroutineStart.LAZY) {
            try {
                val realApkPath = resolveDownloadApkPath(app.appId, versionId)
                if (realApkPath.isBlank()) {
                    throw IllegalStateException("Download URL is blank for versionId $versionId")
                }

                val installedPackageName = XcServiceManager.downloadAndInstall(
                    appId = app.appId, versionId = versionId, url = realApkPath,
                    onProgress = { percent ->
                        updateAppStatus(app.appId, versionId, isLatestVersion) { it.copy(progress = percent) }
                    }
                )

                if (installedPackageName == null) {
                    throw IllegalStateException("Download or install failed for ${app.name}")
                }

                AppPackageNameCache.saveMapping(app.appId, app.name, installedPackageName)

                val latestVersionId = masterAppInfo?.versionId ?: app.versionId
                val newInstallState = if (versionId < latestVersionId) InstallState.INSTALLED_OLD else InstallState.INSTALLED_LATEST

                // ★ 安装成功后，无论如何都要更新主列表
                updateAppStatus(app.appId, versionId, true) {
                    it.copy(
                        downloadStatus = DownloadStatus.NONE,
                        progress = 0,
                        installState = newInstallState,
                        packageName = installedPackageName,
                        isInstalled = true
                    )
                }

                addToRecentInstalled(app.copy(installState = newInstallState, packageName = installedPackageName))

                downloadJobs.remove(key)
                removeFromDownloadQueue(key)

            } catch (e: CancellationException) {
                if (!cancellationsForDeletion.remove(key)) {
                    updateAppStatus(app.appId, versionId, isLatestVersion) { it.copy(downloadStatus = DownloadStatus.PAUSED) }
                }
                downloadJobs.remove(key)

            } catch (e: Exception) {
                LogUtil.e(TAG, "Download/Install failed for ${app.name}", e)
                updateAppStatus(app.appId, versionId, isLatestVersion) { it.copy(downloadStatus = DownloadStatus.PAUSED) }
                downloadJobs.remove(key)
            }
        }

        downloadJobs[key] = newJob
        newJob.start()
    }

    fun installHistoryVersion(app: AppInfo, historyVersion: HistoryVersion) {
        val historyAppInfo = app.copy(
            versionId = historyVersion.versionId,
            versionName = historyVersion.versionName,
            downloadStatus = DownloadStatus.NONE,
            progress = 0
        )
        toggleDownload(historyAppInfo)
    }

    suspend fun loadHistoryVersions(context: Context, app: AppInfo): List<HistoryVersion> {
        return try {
            val installedVersionCode = AppUtils.getInstalledVersionCode(context, app.packageName)
            val response = apiService.getAppHistory(AppVersionHistoryRequest(appId = app.appId))

            if (response.code == 200 && response.data != null) {
                response.data.map { versionItem ->
                    val state = if (versionItem.versionCode.toLongOrNull() == installedVersionCode) {
                        InstallState.INSTALLED_LATEST
                    } else {
                        InstallState.NOT_INSTALLED
                    }
                    HistoryVersion(
                        versionId = versionItem.id,
                        versionName = versionItem.version,
                        apkPath = "",
                        installState = state
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

    fun removeDownload(app: AppInfo) {
        val versionId = app.versionId ?: return
        val key = taskKey(app.appId, versionId)

        val masterAppInfo = synchronized(stateLock) { localAllApps.find { it.appId == app.appId } }
        val isLatestVersion = masterAppInfo == null || versionId >= (masterAppInfo.versionId ?: -1)

        cancellationsForDeletion.add(key)
        downloadJobs[key]?.cancel()

        XcServiceManager.deleteDownloadedFile(app.appId, versionId)

        removeFromDownloadQueue(key)
        updateAppStatus(app.appId, versionId, isLatestVersion) {
            it.copy(downloadStatus = DownloadStatus.NONE, progress = 0)
        }
    }

    fun resumeAllPausedDownloads() {
        val pausedApps = synchronized(stateLock) {
            localDownloadQueue.filter { it.downloadStatus == DownloadStatus.PAUSED }
        }
        pausedApps.forEach { toggleDownload(it) }
    }

    fun checkAppUpdate() {
        coroutineScope.launch {
            try {
                val appId = "32DQY9LH260HX43U"
                val currentVersion = _appVersion.value?.removePrefix("V") ?: "1.0.0"

                val latestVersionInfo = apiService.checkUpdate(
                    CheckUpdateRequest(packageName = appId, currentVer = currentVersion)
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