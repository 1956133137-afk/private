package com.example.storechat.data

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import com.example.storechat.data.api.ApiClient
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

    // ★ 修复1：使用线程安全的 ConcurrentHashMap 存储任务句柄
    private val downloadJobs = ConcurrentHashMap<String, Job>()

    private val apiService = ApiClient.appApi

    // ★ 修复2：全局状态锁，保证状态更新的原子性，防止多线程写入冲突
    private val stateLock = Any()

    // ★ 修复3：本地变量作为“绝对真理”数据源，不再依赖 LiveData.value 读取状态
    private var localAllApps: List<AppInfo> = emptyList()
    private var localDownloadQueue: List<AppInfo> = emptyList()
    private var localRecentApps: List<AppInfo> = emptyList()

    // LiveData 仅作为 UI 通知的“副本”，不参与逻辑计算
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
        val initialApps = listOf(
            AppInfo(
                name = "应用名称A", description = "应用简介说明...", size = "83MB",
                downloadCount = 1002, packageName = "com.demo.appa",
                apkPath = "/sdcard/apks/app_a.apk", installState = InstallState.INSTALLED_LATEST,
                versionName = "1.0.3", releaseDate = "2025-11-12", category = AppCategory.YANNUO
            ),
            AppInfo(
                name = "应用名称C", description = "应用简介说明...", size = "83MB",
                downloadCount = 1002, packageName = "com.demo.appc",
                apkPath = "/sdcard/apks/app_a_103.apk",
                installState = InstallState.INSTALLED_OLD,
                versionName = "1.0.4",
                releaseDate = "2025-11-12",
                category = AppCategory.YANNUO
            ),
            AppInfo(
                name = "应用名称B", description = "应用简介说明...", size = "83MB",
                downloadCount = 1003, packageName = "com.demo.appb",
                apkPath = "/sdcard/apks/app_b.apk", installState = InstallState.INSTALLED_LATEST,
                versionName = "1.0.1", releaseDate = "2025-10-20", category = AppCategory.YANNUO
            ),
            AppInfo(
                name = "工行掌上银行", description = "工行官方移动银行客户端", size = "80MB",
                downloadCount = 500000, packageName = "com.icbc.mobilebank",
                apkPath = "/sdcard/apks/icbc_mobilebank.apk", installState = InstallState.NOT_INSTALLED,
                versionName = "8.2.0.1.1", releaseDate = "2025-11-05", category = AppCategory.ICBC
            ),
            AppInfo(
                name = "建行生活", description = "吃喝玩乐建行优惠", size = "70MB",
                downloadCount = 300000, packageName = "com.ccb.life",
                apkPath = "/sdcard/apks/ccb_life.apk", installState = InstallState.NOT_INSTALLED,
                versionName = "3.2.1", releaseDate = "2025-11-08", category = AppCategory.CCB
            )
        )

        // 初始化本地数据源和 LiveData
        synchronized(stateLock) {
            localAllApps = initialApps
            _allApps.postValue(localAllApps)
        }

        coroutineScope.launch {
            refreshAppsFromServer(AppCategory.YANNUO)
        }
    }

    // --- 核心修复：线程安全的状态更新 ---
    // 所有的状态修改都必须经过这个方法，且加锁，基于本地 localAllApps 修改
    private fun updateAppStatus(packageName: String, transform: (AppInfo) -> AppInfo) {
        synchronized(stateLock) {
            // 1. 基于最新的本地数据源进行修改（不再读取可能过时的 LiveData.value）
            val app = localAllApps.find { it.packageName == packageName } ?: return
            val newApp = transform(app)

            // 2. 更新本地数据源
            localAllApps = localAllApps.map { if (it.packageName == packageName) newApp else it }

            // 3. 同步更新下载队列（如果存在于队列中）
            if (localDownloadQueue.any { it.packageName == packageName }) {
                localDownloadQueue = localDownloadQueue.map { if (it.packageName == packageName) newApp else it }
                _downloadQueue.postValue(localDownloadQueue)
            }

            // 4. 最后推送给 UI
            _allApps.postValue(localAllApps)
        }
    }

    // 线程安全的添加下载任务
    private fun addToDownloadQueue(app: AppInfo) {
        synchronized(stateLock) {
            if (localDownloadQueue.none { it.packageName == app.packageName }) {
                localDownloadQueue = localDownloadQueue + app
                _downloadQueue.postValue(localDownloadQueue)
            }
        }
    }

    // 线程安全的移除下载任务
    private fun removeFromDownloadQueue(packageName: String) {
        synchronized(stateLock) {
            localDownloadQueue = localDownloadQueue.filterNot { it.packageName == packageName }
            _downloadQueue.postValue(localDownloadQueue)
        }
    }

    // 线程安全的添加最近安装
    private fun addToRecentInstalled(app: AppInfo) {
        synchronized(stateLock) {
            // 避免重复添加
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
            val categoryParam = when (category) {
                AppCategory.YANNUO -> "1"
                AppCategory.ICBC -> "2"
                AppCategory.CCB -> "3"
            }
            val remoteList = apiService.getAppList(categoryParam)

            synchronized(stateLock) {
                // 保留其他分类的本地数据，替换当前分类的数据
                val otherApps = localAllApps.filter { it.category != category }
                localAllApps = otherApps + remoteList
                _allApps.postValue(localAllApps)
            }
        } catch (e: Exception) {
            // 接口异常忽略，保持本地数据
        }
    }

    private suspend fun resolveDownloadApkPath(
        packageName: String,
        fallbackApkPath: String,
        versionName: String?
    ): String {
        return try {
            val resp = apiService.getDownloadLink(
                packageName = packageName,
                versionName = versionName
            )
            if (resp.url.isBlank()) fallbackApkPath else resp.url
        } catch (e: Exception) {
            fallbackApkPath
        }
    }

    fun toggleDownload(app: AppInfo) {
        when (app.downloadStatus) {
            DownloadStatus.DOWNLOADING -> {
                // 点击暂停：取消 Job。Job 取消会抛出 CancellationException，在 launch 的 catch 块中处理状态回滚
                downloadJobs[app.packageName]?.cancel()
            }

            DownloadStatus.NONE, DownloadStatus.PAUSED -> {
                // ★ 关键防抖：如果 Map 中已存在 Job，说明已经在启动中或运行中，直接返回，防止覆盖 Job 导致旧任务失控
                if (downloadJobs.containsKey(app.packageName)) {
                    return
                }

                // 添加到队列
                addToDownloadQueue(app)

                // ★ 修复4：使用 LAZY 启动模式
                // 确保 Job 在真正开始运行前，一定已经被放入 downloadJobs Map 中
                // 避免“任务开始了但还没存入Map，此时用户点击暂停无法找到Job”的竞态条件
                val newJob = coroutineScope.launch(start = CoroutineStart.LAZY) {
                    try {
                        // 1. 获取下载地址（模拟网络耗时）
                        val realApkPath = resolveDownloadApkPath(
                            packageName = app.packageName,
                            fallbackApkPath = app.apkPath,
                            versionName = app.versionName
                        )

                        // 2. 更新状态为下载中
                        updateAppStatus(app.packageName) {
                            it.copy(downloadStatus = DownloadStatus.DOWNLOADING)
                        }

                        // 3. 模拟下载进度
                        for (p in app.progress..100) {
                            delay(80L)
                            updateAppStatus(app.packageName) { it.copy(progress = p) }
                        }

                        // 4. 验证中
                        updateAppStatus(app.packageName) {
                            it.copy(downloadStatus = DownloadStatus.VERIFYING)
                        }
                        delay(500)

                        // 5. 安装中
                        updateAppStatus(app.packageName) {
                            it.copy(downloadStatus = DownloadStatus.INSTALLING)
                        }
                        XcServiceManager.installApk(realApkPath, app.packageName, true)
                        delay(1500)

                        // 6. 安装完成
                        var installedApp: AppInfo? = null
                        updateAppStatus(app.packageName) {
                            installedApp = it.copy(
                                downloadStatus = DownloadStatus.NONE,
                                progress = 0,
                                installState = InstallState.INSTALLED_LATEST
                            )
                            installedApp!!
                        }

                        // 7. 成功结束：清理资源
                        downloadJobs.remove(app.packageName)
                        removeFromDownloadQueue(app.packageName)
                        installedApp?.let { addToRecentInstalled(it) }

                    } catch (e: Exception) {
                        // ★ 修复5：正确区分“手动暂停”和“异常错误”
                        if (e is kotlinx.coroutines.CancellationException) {
                            // 手动暂停：从 Map 移除，状态设为 PAUSED
                            downloadJobs.remove(app.packageName)
                            updateAppStatus(app.packageName) {
                                it.copy(downloadStatus = DownloadStatus.PAUSED)
                            }
                            throw e // 重新抛出，让协程框架知道是取消
                        } else {
                            // 网络错误等：也重置为 PAUSED，防止 UI 卡死在下载中
                            downloadJobs.remove(app.packageName)
                            updateAppStatus(app.packageName) {
                                it.copy(downloadStatus = DownloadStatus.PAUSED)
                            }
                        }
                    }
                }

                // 先存入 Map，再启动，保证 Map 中一定有值
                downloadJobs[app.packageName] = newJob
                newJob.start()
            }

            DownloadStatus.VERIFYING, DownloadStatus.INSTALLING -> {
                // 验证和安装阶段不可暂停，忽略点击
            }
        }
    }

    fun cancelDownload(app: AppInfo) {
        // 取消协程
        downloadJobs[app.packageName]?.cancel()
        downloadJobs.remove(app.packageName)

        // 强制移除：恢复为未下载状态
        updateAppStatus(app.packageName) {
            it.copy(downloadStatus = DownloadStatus.NONE, progress = 0)
        }
        removeFromDownloadQueue(app.packageName)
    }

    fun removeDownload(app: AppInfo) {
        removeFromDownloadQueue(app.packageName)
    }

    suspend fun loadHistoryVersions(app: AppInfo): List<HistoryVersion> {
        return try {
            val versions = apiService.getAppHistory(app.packageName)
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
        // 使用本地队列的快照，避免并发修改问题
        val pausedApps = synchronized(stateLock) {
            localDownloadQueue.filter { it.downloadStatus == DownloadStatus.PAUSED }
        }
        // 依次触发下载，由于有了防抖和锁，这里不会再造成状态错乱
        pausedApps.forEach {
            toggleDownload(it)
        }
    }

    fun checkAppUpdate() {
        coroutineScope.launch {
            delay(800L)
            val current = _appVersion.value?.removePrefix("V") ?: "1.0.0"
            val latestFromServer = "1.0.1"
            val status = if (latestFromServer == current) UpdateStatus.LATEST else UpdateStatus.NEW_VERSION(latestFromServer)
            _checkUpdateResult.postValue(status)
        }
    }

    fun clearUpdateResult() {
        _checkUpdateResult.postValue(null)
    }
}