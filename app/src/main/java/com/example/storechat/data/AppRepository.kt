package com.example.storechat.data

import android.content.Context
import android.util.Log
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

    // ★ 数据库 DAO
    private lateinit var downloadDao: DownloadDao

    /**
     *  方案B：下载任务按 taskKey 管理
     * taskKey = "packageName@versionId"
     */
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

    // ----------------------------
    //  taskKey helpers
    // ----------------------------

    private fun taskKey(packageName: String, versionId: Long): String = "$packageName@$versionId"

    private fun taskKey(app: AppInfo): String? {
        val vid = app.versionId ?: return null
        return taskKey(app.packageName, vid)
    }

    // ----------------------------
    //  状态更新（主列表按 packageName，队列按 taskKey）
    // ----------------------------

    private fun updateAppStatus(
        packageName: String,
        versionId: Long?,
        transform: (AppInfo) -> AppInfo
    ) {
        synchronized(stateLock) {
            val key = versionId?.let { taskKey(packageName, it) }

            // 1) 主列表（一个包只保留一个条目）
            val master = localAllApps.find { it.packageName == packageName }
            val newMaster = master?.let(transform)

            // 2) 队列（同包可多个版本）
            val queueItem = key?.let { k -> localDownloadQueue.find { taskKey(it) == k } }
            val newQueueItem = queueItem?.let(transform)

            var allAppsChanged = false
            var queueChanged = false

            if (newMaster != null) {
                localAllApps = localAllApps.map { if (it.packageName == packageName) newMaster else it }
                allAppsChanged = true
            }

            if (key != null && newQueueItem != null) {
                localDownloadQueue = localDownloadQueue.map { if (taskKey(it) == key) newQueueItem else it }
                queueChanged = true

                // ★ 触发数据库保存
                saveToDatabase(newQueueItem)
            }

            if (queueChanged) _downloadQueue.postValue(localDownloadQueue)
            if (allAppsChanged) _allApps.postValue(localAllApps)
        }
    }

    /**
     * ★ 新增：保存任务到数据库
     */
    private fun saveToDatabase(app: AppInfo) {
        coroutineScope.launch {
            val vid = app.versionId ?: return@launch
            val key = taskKey(app.packageName, vid)

            val entity = DownloadEntity(
                taskKey = key,
                appId = app.appId,
                versionId = vid,
                packageName = app.packageName,
                name = app.name,
                categoryId = app.category.id,
                downloadUrl = "", // 这里暂存空，实际可在下载开始时传入
                savePath = app.apkPath ?: "", // 关键：保存下载路径
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
                // 首次加入数据库
                saveToDatabase(app)
            }
        }
    }

    private fun removeFromDownloadQueue(taskKey: String) {
        synchronized(stateLock) {
            localDownloadQueue = localDownloadQueue.filterNot { taskKey(it) == taskKey }
            _downloadQueue.postValue(localDownloadQueue)

            //  从数据库删除
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

    // ----------------------------
    // 初始化逻辑
    // ----------------------------

    fun initialize(context: Context) {
        // ★ 1. 初始化数据库
        val db = AppDatabase.getDatabase(context)
        downloadDao = db.downloadDao()

        // ★ 2. 异步加载历史任务，再请求网络
        coroutineScope.launch {
            val entities = downloadDao.getAllTasks()

            val restoredQueue = entities.map { entity ->
                // 状态修正：重启后所有的 DOWNLOADING 应该变为 PAUSED
                var status = DownloadStatus.values().getOrElse(entity.status) { DownloadStatus.NONE }
                if (status == DownloadStatus.DOWNLOADING) {
                    status = DownloadStatus.PAUSED
                }

                // 检查文件是否存在，如果文件丢了，状态重置
                if (status == DownloadStatus.PAUSED && entity.savePath.isNotEmpty()) {
                    val file = File(entity.savePath)
                    if (!file.exists()) {
                        status = DownloadStatus.NONE
                    }
                }

                val finalProgress = if (status == DownloadStatus.NONE) 0 else entity.progress

                AppInfo(
                    name = entity.name,
                    appId = entity.appId,
                    versionId = entity.versionId,
                    category = AppCategory.values().find { it.id == entity.categoryId } ?: AppCategory.YANNUO,
                    createTime = null,
                    updateTime = null,
                    remark = null,
                    description = "",
                    size = "N/A",
                    downloadCount = 0,
                    packageName = entity.packageName,
                    apkPath = entity.savePath,
                    installState = InstallState.NOT_INSTALLED, // 后续网络请求会修正
                    versionName = "已暂停",
                    releaseDate = "",
                    downloadStatus = status,
                    progress = finalProgress
                )
            }.filter { it.downloadStatus != DownloadStatus.NONE }

            synchronized(stateLock) {
                localDownloadQueue = restoredQueue
                _downloadQueue.postValue(localDownloadQueue)
            }

            // 3. 初始请求网络
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

        //  1. 节流
        val last = lastFetchAt[key] ?: 0L
        if (now - last < MIN_FETCH_INTERVAL_MS) return
        lastFetchAt[key] = now

        //  2. 单飞
        if (refreshJobs[key]?.isActive == true) return

        refreshJobs[key] = coroutineScope.launch {
            refreshAppsFromServer(context, category)
        }
    }

    private suspend fun refreshAppsFromServer(context: Context, category: AppCategory?) {
        _isLoading.postValue(true)
        try {
            val response = apiService.getAppList(
                AppListRequestBody(appCategory = category?.id)
            )

            if (response.code == 200 && response.data != null) {
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

                        // ★ 关键：如果本地下载队列里有这个任务，优先使用队列的状态（因为那是断点续传的真理）
                        val queueItem = localDownloadQueue.find {
                            it.appId == serverApp.appId && it.versionId == serverApp.id?.toLong()
                        }

                        val baseInfo = mapToAppInfo(serverApp, localApp).copy(
                            packageName = finalPackageName,
                            installState = installState,
                            isInstalled = isInstalled
                        )

                        if (queueItem != null) {
                            baseInfo.copy(
                                downloadStatus = queueItem.downloadStatus,
                                progress = queueItem.progress,
                                apkPath = queueItem.apkPath
                            )
                        } else {
                            baseInfo
                        }
                    }

                    val otherCategoryApps = localAllApps.filter { it.category != category }
                    localAllApps = otherCategoryApps + mergedRemoteList
                    _allApps.postValue(localAllApps)
                }
                _isLoading.postValue(false)
            } else {
                _allApps.postValue(localAllApps.filter { it.category == category })
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _allApps.postValue(localAllApps.filter { it.category == category })
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
            size = "N/A",
            downloadCount = 0,
            packageName = response.appId,
            apkPath = "",
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
                Log.e(TAG, "API error getting download link: ${response.msg}")
                ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve download URL for versionId: $versionId", e)
            ""
        }
    }

    /**
     *  方案B最关键修复点：taskKey 管理 + 数据库持久化
     */
    fun toggleDownload(app: AppInfo) {
        val versionId = app.versionId
        if (versionId == null) {
            Log.e(TAG, "Cannot download ${app.name}, versionId is null.")
            return
        }

        val key = taskKey(app.packageName, versionId)
        val isDownloading = downloadJobs.containsKey(key)
        val isPaused = app.downloadStatus == DownloadStatus.PAUSED

        if (isDownloading) {
            downloadJobs[key]?.cancel()
            return
        }

        if (isPaused) {
            updateAppStatus(app.packageName, versionId) {
                it.copy(downloadStatus = DownloadStatus.DOWNLOADING)
            }
        } else {
            addToDownloadQueue(app)
            updateAppStatus(app.packageName, versionId) {
                it.copy(downloadStatus = DownloadStatus.DOWNLOADING, progress = 0)
            }
        }

        val newJob = coroutineScope.launch(start = CoroutineStart.LAZY) {
            try {
                val realApkPath = resolveDownloadApkPath(app.appId, versionId)
                if (realApkPath.isBlank()) {
                    throw IllegalStateException("Download URL is blank for versionId $versionId")
                }

                // ★ 这里没有现成的路径，但在 XcServiceManager 下载过程中会生成
                // 通常建议预先构建路径并保存到 DB，这里我们假设 onProgress 回调时状态会自动写入 DB

                val installedPackageName = XcServiceManager.downloadAndInstall(
                    appId = app.appId,
                    versionId = versionId,
                    url = realApkPath,
                    onProgress = { percent ->
                        updateAppStatus(app.packageName, versionId) { current ->
                            // 如果 apkPath 为空，可以在这里补全（需修改 XcServiceManager 返回路径或推断路径）
                            // 简单起见，我们假设下次启动检查文件存在性
                            current.copy(progress = percent)
                        }
                    }
                )

                if (installedPackageName != null) {
                    AppPackageNameCache.saveMapping(app.appId, app.name, installedPackageName)
                } else {
                    throw IllegalStateException("Download or install failed for ${app.name}")
                }

                val masterAppInfo = synchronized(stateLock) {
                    localAllApps.find { it.packageName == app.packageName }
                }
                val latestVersionId = masterAppInfo?.versionId ?: app.versionId

                val newInstallState = if (app.versionId < latestVersionId) {
                    InstallState.INSTALLED_OLD
                } else {
                    InstallState.INSTALLED_LATEST
                }

                updateAppStatus(app.packageName, versionId) {
                    it.copy(downloadStatus = DownloadStatus.NONE, progress = 0, installState = newInstallState)
                }

                addToRecentInstalled(app.copy(installState = newInstallState))

                downloadJobs.remove(key)
                removeFromDownloadQueue(key) // 任务完成后从 DB 和队列移除

            } catch (e: CancellationException) {
                val isDeletion = cancellationsForDeletion.remove(key)
                if (!isDeletion) {
                    updateAppStatus(app.packageName, versionId) {
                        it.copy(downloadStatus = DownloadStatus.PAUSED)
                    }
                }
                downloadJobs.remove(key)

            } catch (e: Exception) {
                Log.e(TAG, "Download/Install failed for ${app.name}", e)
                updateAppStatus(app.packageName, versionId) {
                    it.copy(downloadStatus = DownloadStatus.PAUSED)
                }
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

            val response = apiService.getAppHistory(
                AppVersionHistoryRequest(appId = app.appId)
            )

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
        val key = taskKey(app.packageName, versionId)

        cancellationsForDeletion.add(key)
        downloadJobs[key]?.cancel()

        XcServiceManager.deleteDownloadedFile(app.appId, versionId)

        removeFromDownloadQueue(key) // 会触发 DB 删除
        updateAppStatus(app.packageName, versionId) {
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