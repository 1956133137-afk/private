package com.example.storechat.data

import android.content.Context
import android.os.Build
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.Locale

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

    private var currentVersionCode: Long = 1L

    private val _checkUpdateResult = MutableLiveData<UpdateStatus?>()
    val checkUpdateResult: LiveData<UpdateStatus?> = _checkUpdateResult

    val eventMessage = MutableLiveData<String>()
    val downloadErrorEvent = MutableLiveData<String>()

    fun clearDownloadError() {
        downloadErrorEvent.postValue("")
    }

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

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(
            Locale.US,
            "%.1f%s",
            bytes / Math.pow(1024.0, digitGroups.toDouble()),
            units[digitGroups]
        )
    }

    private fun taskKey(appId: String, versionId: Long): String = "$appId@$versionId"
    private fun taskKey(app: AppInfo): String? {
        val vid = app.versionId ?: return null
        return taskKey(app.appId, vid)
    }

    private fun updateAppStatus(
        appId: String,
        versionId: Long?,
        updateMasterList: Boolean,
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
        AppPackageNameCache.init(context)
        XcServiceManager.init(context)

        val verName = AppUtils.getAppVersionName(context)
        currentVersionCode = AppUtils.getAppVersionCode(context)
        _appVersion.postValue("V$verName")

        val db = AppDatabase.getDatabase(context)
        downloadDao = db.downloadDao()

        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                AppPackageNameCache.scanInstalledPackages(context)
            }

            val entities = downloadDao.getAllTasks()
            val restoredQueue = entities.map { entity ->
                var status = DownloadStatus.values().getOrElse(entity.status) { DownloadStatus.NONE }

                if (status == DownloadStatus.DOWNLOADING) {
                    status = DownloadStatus.PAUSED
                }




                val fileExists = entity.savePath.isNotBlank() && File(entity.savePath).exists()

                if (status == DownloadStatus.PAUSED && !fileExists) {
    
    
                }

                val finalProgress = if (fileExists) entity.progress else 0

                AppInfo(
                    name = entity.name,
                    appId = entity.appId,
                    versionId = entity.versionId,
                    versionCode = null,
                    category = AppCategory.values().find { it.id == entity.categoryId } ?: AppCategory.YANNUO,
                    createTime = null, updateTime = null, remark = null, description = "",
                    size = "N/A", downloadCount = 0,
                    packageName = entity.packageName,
                    apkPath = entity.savePath,
                    installState = InstallState.NOT_INSTALLED,
                    versionName = "已暂停", releaseDate = "",
                    downloadStatus = status,
                    progress = finalProgress,
                    currentSizeStr = "",
                    totalSizeStr = "",
                    installedVersionCode = 0
                )
            }.filter { it.downloadStatus != DownloadStatus.NONE }

            synchronized(stateLock) {
                localDownloadQueue = restoredQueue
                _downloadQueue.postValue(localDownloadQueue)
            }

            requestAppList(context, AppCategory.YANNUO)
        }
    }

    fun reloadTasksFromDb() {
        if (!::downloadDao.isInitialized) return
        coroutineScope.launch {
            val entities = downloadDao.getAllTasks()
            val runningTasksSnapshot = synchronized(stateLock) {
                localDownloadQueue.filter {
                    val key = taskKey(it)
                    key != null && downloadJobs.containsKey(key)
                }.associateBy { taskKey(it) }
            }

            val restoredQueue = entities.map { entity ->
                val key = entity.taskKey
                val runningTask = if (key != null) runningTasksSnapshot[key] else null
                if (runningTask != null) return@map runningTask

                var status = DownloadStatus.values().getOrElse(entity.status) { DownloadStatus.NONE }
                val isJobRunning = key?.let { downloadJobs.containsKey(it) } ?: false
                if (status == DownloadStatus.DOWNLOADING && !isJobRunning) {
                    status = DownloadStatus.PAUSED
                }

                val fileExists = entity.savePath.isNotBlank() && File(entity.savePath).exists()

                if (status == DownloadStatus.PAUSED && !fileExists) {
    
                }
                val finalProgress = if (status == DownloadStatus.NONE) 0 else entity.progress

                AppInfo(
                    name = entity.name,
                    appId = entity.appId,
                    versionId = entity.versionId,
                    versionCode = null,
                    category = AppCategory.values().find { it.id == entity.categoryId } ?: AppCategory.YANNUO,
                    createTime = null, updateTime = null, remark = null, description = "",
                    size = "N/A", downloadCount = 0,
                    packageName = entity.packageName,
                    apkPath = entity.savePath,
                    installState = InstallState.NOT_INSTALLED,
                    versionName = "已暂停", releaseDate = "",
                    downloadStatus = status, progress = finalProgress,
                    currentSizeStr = "",
                    totalSizeStr = "",
                    installedVersionCode = 0
                )
            }.filter { it.downloadStatus != DownloadStatus.NONE }

            synchronized(stateLock) {
                localDownloadQueue = restoredQueue
                _downloadQueue.postValue(localDownloadQueue)
            }
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
        
        withContext(Dispatchers.Main) {
            _isLoading.value = true
        }
        try {
            val response = apiService.getAppList(AppListRequestBody(appCategory = category?.id))


            val updatedList = if (response.code == 200 && response.data != null) {
                val distinctList = response.data
                    .groupBy { it.appId }
                    .map { (_, apps) -> apps.maxByOrNull { it.id ?: -1 }!! }

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

                        val baseInfo = mapToAppInfo(serverApp, localApp, installedVersionCode).copy(
                            packageName = finalPackageName,
                            installState = installState,
                            isInstalled = isInstalled
                        )

                        queueItem?.let {
                            baseInfo.copy(
                                downloadStatus = it.downloadStatus,
                                progress = it.progress,
                                apkPath = it.apkPath,
                                currentSizeStr = it.currentSizeStr,
                                totalSizeStr = it.totalSizeStr
                            )
                        } ?: baseInfo
                    }

                    val otherCategoryApps = localAllApps.filter { it.category != category }
                    localAllApps = otherCategoryApps + mergedRemoteList
        
                    localAllApps
                }

            } else {
    
                synchronized(stateLock) {
                    localAllApps.filter { it.category == category }
                }
            }



            withContext(Dispatchers.Main) {
                _allApps.value = updatedList
            }
        } catch (e: Exception) {
            e.printStackTrace()

            val fallbackList = synchronized(stateLock) {
                localAllApps.filter { it.category == category }
            }
            withContext(Dispatchers.Main) {
                _allApps.value = fallbackList
            }
        } finally {




            withContext(Dispatchers.Main) {
                _isLoading.value = false
            }
        }
    }

    private fun mapToAppInfo(response: AppInfoResponse, localApp: AppInfo?, installedVersionCode: Long = 0): AppInfo {
        return AppInfo(
            name = response.productName,
            appId = response.appId,
            versionId = response.id?.toLong(),
            versionCode = response.versionCode?.toIntOrNull(),
            category = AppCategory.from(response.appCategory) ?: AppCategory.YANNUO,
            createTime = response.createTime, updateTime = response.updateTime, remark = response.remark,
            description = response.versionDesc ?: "",
            size = localApp?.size ?: "N/A",
            downloadCount = 0,
            packageName = response.appId, apkPath = "",
            installState = localApp?.installState ?: InstallState.NOT_INSTALLED,
            versionName = response.version ?: "N/A",
            releaseDate = response.updateTime ?: response.createTime,
            downloadStatus = localApp?.downloadStatus ?: DownloadStatus.NONE,
            progress = localApp?.progress ?: 0,
            currentSizeStr = localApp?.currentSizeStr ?: "",
            totalSizeStr = localApp?.totalSizeStr ?: "",
            installedVersionCode = installedVersionCode
        )
    }

    private suspend fun resolveDownloadApkPath(appId: String, versionId: Long): Pair<String, Long> {
        try {
            val response = apiService.getDownloadUrl(GetDownloadUrlRequest(appId = appId, id = versionId))
            if (response.code == 200 && response.data != null && response.data.fileUrl.isNotBlank()) {
                val fileSize = response.data.fileSize ?: getFileSizeFromUrl(response.data.fileUrl)
                return response.data.fileUrl to fileSize
            } else {
                val errorMsg = response.msg ?: "获取下载地址失败"

                throw IOException(errorMsg)
            }
        } catch (e: Exception) {



            throw e
        }
    }

    private suspend fun getFileSizeFromUrl(url: String): Long {
        if (url.isBlank()) return 0L
        return try {
            val client = OkHttpClient()
            val request = Request.Builder().url(url).head().build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                response.header("Content-Length")?.toLongOrNull() ?: 0L
            } else {
                0L
            }
        } catch (e: IOException) {
            0L
        }
    }

    fun fetchAndSetAppSize(appInfo: AppInfo) {
        if (appInfo.versionId == null) return
        coroutineScope.launch {
            try {
                val (_, fileSize) = resolveDownloadApkPath(appInfo.appId, appInfo.versionId)
                if (fileSize > 0) {
                    val formattedSize = formatSize(fileSize)
                    updateAppStatus(appInfo.appId, appInfo.versionId, true) {
                        it.copy(size = formattedSize)
                    }
                }
            } catch (e: Exception) {
            }
        }
    }


    fun toggleDownload(app: AppInfo) {
        val versionId = app.versionId
        if (versionId == null) {
            eventMessage.postValue("此应用暂无可用版本，无法下载")
            return
        }

        val masterAppInfo = synchronized(stateLock) { localAllApps.find { it.appId == app.appId } }
        val isLatestVersion = masterAppInfo == null || versionId >= (masterAppInfo.versionId ?: -1)

        val key = taskKey(app.appId, versionId)
        val isDownloading = downloadJobs.containsKey(key)
        val isPaused = app.downloadStatus == DownloadStatus.PAUSED

        if (isDownloading) {
            LogUtil.d(TAG, "Pausing download for ${app.name}")
            downloadJobs[key]?.cancel()
            return
        }

        val newJob = coroutineScope.launch {
            try {
                val (realApkPath, fileSize) = resolveDownloadApkPath(app.appId, versionId)
                if (realApkPath.isBlank()) {
                    throw IllegalStateException("下载地址为空")
                }

                addToDownloadQueue(app)
                updateAppStatus(app.appId, versionId, isLatestVersion) {
                    it.copy(
                        downloadStatus = DownloadStatus.DOWNLOADING,
                        progress = if(isPaused) it.progress else 0,
                        size = if (fileSize > 0) formatSize(fileSize) else it.size
                    )
                }

                var lastReportedProgress = -1

                val installedPackageName = XcServiceManager.downloadAndInstall(
                    appId = app.appId,
                    versionId = versionId,
                    newVersionCode = app.versionCode ?: -1,
                    url = realApkPath,
                    onProgress = { percent, currentBytes, totalBytes ->
                        if (percent != lastReportedProgress) {
                            lastReportedProgress = percent

                            val currentStr = formatSize(currentBytes)
                            val totalStr = formatSize(totalBytes)

                            updateAppStatus(app.appId, versionId, isLatestVersion) {
                                it.copy(progress = percent, currentSizeStr = currentStr, totalSizeStr = totalStr)
                            }
                        }
                    }
                )

                if (installedPackageName == null) throw IllegalStateException("安装失败")

                AppPackageNameCache.saveMapping(app.appId, app.name, installedPackageName)
                downloadJobs.remove(key)
                removeFromDownloadQueue(key)

                XcServiceManager.deleteDownloadedFile(app.appId, versionId)

                synchronized(stateLock) {
                    val masterApp = localAllApps.find { it.appId == app.appId }
                    val latestVid = masterApp?.versionId ?: versionId
                    val newInstallState = if (versionId < latestVid) {
                        InstallState.INSTALLED_OLD
                    } else {
                        InstallState.INSTALLED_LATEST
                    }

                    var newlyInstalledApp: AppInfo? = null
                    localAllApps = localAllApps.map {
                        if (it.appId == app.appId) {
                            it.copy(
                                downloadStatus = DownloadStatus.NONE,
                                progress = 0,
                                installState = newInstallState,
                                packageName = installedPackageName,
                                isInstalled = true,
                                currentSizeStr = "",
                                totalSizeStr = "",
                                installedVersionCode = (app.versionCode ?: -1).toLong()
                            ).also { updatedApp -> newlyInstalledApp = updatedApp }
                        } else {
                            it
                        }
                    }

                    newlyInstalledApp?.let {
                        localRecentApps = localRecentApps.filterNot { recent -> recent.packageName == it.packageName }
                        localRecentApps = listOf(it) + localRecentApps
                    }

                    _allApps.postValue(localAllApps)
                    _recentInstalledApps.postValue(localRecentApps)
                }

            } catch (e: CancellationException) {
                if (!cancellationsForDeletion.remove(key)) {
                    updateAppStatus(app.appId, versionId, isLatestVersion) { it.copy(downloadStatus = DownloadStatus.PAUSED) }
                }
                downloadJobs.remove(key)
            } catch (e: Exception) {
                LogUtil.e(TAG, "Download/Install failed", e)
                val msg = e.message ?: "未知错误"
                downloadErrorEvent.postValue("下载或安装失败: $msg")

                removeFromDownloadQueue(key)
                updateAppStatus(app.appId, versionId, isLatestVersion) {
                    it.copy(downloadStatus = DownloadStatus.NONE, progress = 0)
                }
                downloadJobs.remove(key)
            }
        }

        downloadJobs[key] = newJob
    }

    fun installHistoryVersion(app: AppInfo, historyVersion: HistoryVersion) {
        val historyAppInfo = app.copy(
            versionId = historyVersion.versionId,
            versionName = historyVersion.versionName,
            versionCode = historyVersion.versionCode,
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
                    val state = if (versionItem.versionCode?.toLongOrNull() == installedVersionCode) {
                        InstallState.INSTALLED_LATEST
                    } else {
                        InstallState.NOT_INSTALLED
                    }
                    HistoryVersion(
                        versionId = versionItem.id,
                        versionName = versionItem.version,
                        versionCode = versionItem.versionCode?.toIntOrNull(),
                        apkPath = versionItem.fileUrl ?: "",
                        installState = state,
                        appName = app.name // Pass the app name here
                    )
                }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
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
            it.copy(downloadStatus = DownloadStatus.NONE, progress = 0, currentSizeStr = "", totalSizeStr = "")
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
                val appId = SignConfig.APP_ID
                // 使用版本号进行比较，而不是字符串版本名
                val response = apiService.checkUpdate(
                    CheckUpdateRequest(packageName = appId)
                )
                if (response.code == 200 && response.data != null) {
                    val list = response.data
                    val latestVersionInfo = list.maxByOrNull { item: VersionResponse ->
                        item.versionCode?.toLongOrNull() ?: 0L
                    }

                    val latestVC = latestVersionInfo?.versionCode?.toLongOrNull() ?: 0L

                    if (latestVersionInfo != null && latestVC > currentVersionCode) {
                        _checkUpdateResult.postValue(UpdateStatus.NEW_VERSION(
                            latestVersion = latestVersionInfo.version ?: "未知版本",
                            latestVersionCode = latestVC,
                            downloadUrl = latestVersionInfo.fileUrl,
                            description = latestVersionInfo.versionDesc
                        ))
                    } else {
                        _checkUpdateResult.postValue(UpdateStatus.LATEST)
                    }
                } else {
                    _checkUpdateResult.postValue(UpdateStatus.LATEST)
                }
            } catch (e: Exception) {
                LogUtil.e(TAG, "Check update failed", e)
                _checkUpdateResult.postValue(UpdateStatus.LATEST)
            }
        }
    }

    fun startSelfUpdate(status: UpdateStatus.NEW_VERSION) {
        val url = status.downloadUrl
        if (url.isNullOrBlank()) {
            eventMessage.postValue("更新地址无效")
            return
        }

        coroutineScope.launch {
            try {
                eventMessage.postValue("正在后台下载更新...")
                val installedPackageName = XcServiceManager.downloadAndInstall(
                    appId = SignConfig.APP_ID,
                    versionId = System.currentTimeMillis(), // 临时 ID
                    newVersionCode = status.latestVersionCode.toInt(),
                    url = url,
                    onProgress = null
                )
                if (installedPackageName == null) {
                    eventMessage.postValue("更新失败")
                }
            } catch (e: Exception) {
                LogUtil.e(TAG, "Self update failed", e)
                eventMessage.postValue("更新失败: ${e.message}")
            }
        }
    }

    fun clearUpdateResult() {
        _checkUpdateResult.postValue(null)
    }
}
