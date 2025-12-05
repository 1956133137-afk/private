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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object AppRepository {


    //3个接口
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val downloadJobs = mutableMapOf<String, Job>()

    //统一Retorize 接口
    private val apiService = ApiClient.appApi

    private val _appVersion = MutableLiveData("V1.0.0")
    val appVersion: LiveData<String> = _appVersion
    private val _checkUpdateResult = MutableLiveData<UpdateStatus?>()
    val checkUpdateResult: LiveData<UpdateStatus?> = _checkUpdateResult

    fun checkAppUpdate() {
        coroutineScope.launch {
            delay(800L)
            val current = _appVersion.value?.removePrefix("V") ?: "1.0.0"
            // 这里仍然是模拟，后面可换成 apiService.checkUpdate(...)
            val latestFromServer = "1.0.1"
            val status = if (latestFromServer == current) UpdateStatus.LATEST else UpdateStatus.NEW_VERSION(latestFromServer)
            _checkUpdateResult.postValue(status)
        }
    }

    fun clearUpdateResult() {
        _checkUpdateResult.postValue(null)
    }
    // 应用列表 & 分类
    private val _allApps = MutableLiveData<List<AppInfo>>(emptyList())
    val allApps: LiveData<List<AppInfo>> = _allApps

    private val _selectedCategory = MutableLiveData(AppCategory.YANNUO)

    // 由 allApps 和 selectedCategory 派生出分类应用列表
    val categorizedApps: LiveData<List<AppInfo>> = MediatorLiveData<List<AppInfo>>().apply {
        addSource(allApps) { apps ->
            value = apps.filter { it.category == _selectedCategory.value }
        }
        addSource(_selectedCategory) { category ->
            value = allApps.value?.filter { it.category == category }
        }
    }

    //下载接口 & 最近安装
    private val _downloadQueue = MutableLiveData<List<AppInfo>>(emptyList())
    val downloadQueue: LiveData<List<AppInfo>> = _downloadQueue

    private val _recentInstalledApps = MutableLiveData<List<AppInfo>>(emptyList())
    val recentInstalledApps: LiveData<List<AppInfo>> = _recentInstalledApps

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
                // 假设当前机器上其实是 1.0.2，这里标记为已安装旧版本，按钮就会显示“升级”
                installState = InstallState.INSTALLED_OLD,
                versionName = "1.0.4",   // 新版本号
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
        _allApps.postValue(initialApps)

        //    启动时尝试用接口刷新默认分类（如果接口未通，会自动忽略异常）
        coroutineScope.launch {
            refreshAppsFromServer(AppCategory.YANNUO)
        }
    }


    /**
     * UI 选择分类时调用
     */
    fun selectCategory(category: AppCategory) {
        _selectedCategory.postValue(category)
        coroutineScope.launch {
            refreshAppsFromServer(category)
        }
    }

    /**
     * a. 应用列表接口：按分类从服务端获取列表
     * 目前失败时静默忽略，保留本地数据
     */
    private suspend fun refreshAppsFromServer(category: AppCategory) {
        try {
            val categoryParam = when (category) {
                AppCategory.YANNUO -> "1"
                AppCategory.ICBC -> "2"
                AppCategory.CCB -> "3"
            }
            val remoteList = apiService.getAppList(categoryParam)

            // 更新 allApps 列表
            _allApps.value?.let { currentApps ->
                val otherApps = currentApps.filter { it.category != category }
                _allApps.postValue(otherApps + remoteList)
            }
        } catch (e: Exception) {
            // 接口未通或报错时保持原有本地数据即可
        }
    }

    //内部工具函数
    private fun findApp(packageName: String): AppInfo? {
        return _allApps.value?.find { it.packageName == packageName }
    }

    private fun updateAppStatus(packageName: String, transform: (AppInfo) -> AppInfo) {
        val app = findApp(packageName) ?: return
        val newApp = transform(app)

        _allApps.value?.let { list ->
            _allApps.postValue(list.map { if (it.packageName == packageName) newApp else it })
        }

        _downloadQueue.value?.let { list ->
            if (list.any{ it.packageName == packageName }) {
                _downloadQueue.postValue(list.map { if (it.packageName == packageName) newApp else it })
            }
        }
    }


    /**
     * b. 统一封装“下载链接获取逻辑”
     * - 接入接口后只要改这里，不用改 UI 和其他业务代码
     */
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
            val url = resp.url
            if (url.isBlank()) fallbackApkPath else url
        } catch (e: Exception) {
            // 接口失败时直接用原来的 apkPath，保证 Demo 仍可运行
            fallbackApkPath
        }
    }


    //    下载队列 & 安装逻辑

    //    下载队列 & 安装逻辑
    fun toggleDownload(app: AppInfo) {
        when (app.downloadStatus) {
            // 1. 正在下载 -> 点击变为暂停
            DownloadStatus.DOWNLOADING -> {
                downloadJobs[app.packageName]?.cancel()
                downloadJobs.remove(app.packageName)
                updateAppStatus(app.packageName) {
                    it.copy(downloadStatus = DownloadStatus.PAUSED)
                }
            }

            // 2. 未在下载 / 已暂停 -> 开始（或继续）下载：包括“安装”和“升级”
            DownloadStatus.NONE, DownloadStatus.PAUSED -> {
                val currentQueue = _downloadQueue.value ?: emptyList()
                if (currentQueue.none { it.packageName == app.packageName }) {
                    _downloadQueue.postValue(currentQueue + app)
                }

                val newJob = coroutineScope.launch {
                    // 先解析出真正要用的 apk 下载地址（接口 or 本地路径）
                    val realApkPath = resolveDownloadApkPath(
                        packageName = app.packageName,
                        fallbackApkPath = app.apkPath,
                        versionName = app.versionName   // 这里带上版本号，后端即可区分是升级哪一版
                    )

                    // 标记为下载中
                    updateAppStatus(app.packageName) {
                        it.copy(downloadStatus = DownloadStatus.DOWNLOADING)
                    }

                    // 模拟下载进度
                    for (p in app.progress..100) {
                        delay(80L)
                        updateAppStatus(app.packageName) { it.copy(progress = p) }
                    }

                    // 验证
                    updateAppStatus(app.packageName) {
                        it.copy(downloadStatus = DownloadStatus.VERIFYING)
                    }
                    delay(500)

                    // 安装
                    updateAppStatus(app.packageName) {
                        it.copy(downloadStatus = DownloadStatus.INSTALLING)
                    }
                    // ★ 这里用 realApkPath，确保升级时是装“新包”
                    XcServiceManager.installApk(realApkPath, app.packageName, true)
                    delay(1500)

                    // 安装完成 -> 变为“已最新”，并从下载队列移除，加入“最近安装”
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
                    _downloadQueue.value?.let { queue ->
                        _downloadQueue.postValue(queue.filterNot { it.packageName == app.packageName })
                    }

                    installedApp?.let { appInfo ->
                        val currentRecent = _recentInstalledApps.value ?: emptyList()
                        _recentInstalledApps.postValue(listOf(appInfo) + currentRecent)
                    }
                }

                downloadJobs[app.packageName] = newJob
            }

            // 3. 验证/安装阶段点击无效
            DownloadStatus.VERIFYING, DownloadStatus.INSTALLING -> {
                // 忽略点击
            }
        }
    }





//    fun toggleDownload(app: AppInfo) {
//        when (app.downloadStatus) {
//            DownloadStatus.DOWNLOADING -> {
//                downloadJobs[app.packageName]?.cancel()
//                downloadJobs.remove(app.packageName)
//                updateAppStatus(app.packageName) { it.copy(downloadStatus = DownloadStatus.PAUSED) }
//            }
//            DownloadStatus.NONE, DownloadStatus.PAUSED -> {
//                val currentQueue = _downloadQueue.value ?: emptyList()
//                if (currentQueue.none { it.packageName == app.packageName }) {
//                    _downloadQueue.postValue(currentQueue + app)
//                }
//
//                val newJob = coroutineScope.launch {
////                   先解析出真正要用的 apk 下载地址（接口 or 本地路径）
//                    val realApkPath = resolveDownloadApkPath(
//                        packageName = app.packageName,
//                        fallbackApkPath = app.apkPath,
//                        versionName = app.versionName
//                    )
//                    updateAppStatus(app.packageName) { it.copy(downloadStatus = DownloadStatus.DOWNLOADING) }
//
//                    for (p in app.progress..100) {
//                        delay(80L)
//                        updateAppStatus(app.packageName) { it.copy(progress = p) }
//                    }
//
//                    updateAppStatus(app.packageName) { it.copy(downloadStatus = DownloadStatus.VERIFYING) }
//                    delay(500)
//
//                    updateAppStatus(app.packageName) { it.copy(downloadStatus = DownloadStatus.INSTALLING) }
//                    XcServiceManager.installApk(app.apkPath, app.packageName, true)
//                    delay(1500)
//
//                    var installedApp: AppInfo? = null
//                    updateAppStatus(app.packageName) {
//                        installedApp = it.copy(
//                            downloadStatus = DownloadStatus.NONE,
//                            progress = 0,
//                            installState = InstallState.INSTALLED_LATEST
//                        )
//                        installedApp!!
//                    }
//                    downloadJobs.remove(app.packageName)
//                    _downloadQueue.value?.let { queue ->
//                        _downloadQueue.postValue(queue.filterNot { it.packageName == app.packageName })
//                    }
//                    installedApp?.let { appInfo ->
//                        val currentRecent = _recentInstalledApps.value ?: emptyList()
//                        _recentInstalledApps.postValue(listOf(appInfo) + currentRecent)
//                    }
//                }
//                downloadJobs[app.packageName] = newJob
//            }
//            DownloadStatus.VERIFYING, DownloadStatus.INSTALLING -> {
//                //忽略
//            }
//        }
//    }

    fun cancelDownload(app: AppInfo) {
        downloadJobs[app.packageName]?.cancel()
        downloadJobs.remove(app.packageName)
        updateAppStatus(app.packageName) {
            it.copy(
                downloadStatus = DownloadStatus.NONE,
                progress = 0
            )
        }
    }

    fun removeDownload(app: AppInfo) {
        _downloadQueue.value?.let { list ->
            _downloadQueue.postValue(list.filterNot { it.packageName == app.packageName })
        }
    }
    // ---------------- c. 历史版本列表 & 安装 ----------------

    /**
     * 历史版本列表接口预留：
     * - 先尝试从接口获取
     * - 接口失败或返回空，则回退到本地假数据
     */
    suspend fun loadHistoryVersions(app: AppInfo): List<HistoryVersion> {
        return try {
            val versions = apiService.getAppHistory(app.packageName)
            if (versions.isNotEmpty()) {
                versions.map { versionInfo ->
                    HistoryVersion(
                        versionName = versionInfo.versionName,
                        apkPath = versionInfo.apkPath
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

    /**
     * 历史版本安装：
     * - 被 HistoryVersionFragment 在点击“安装”时调用
     * - 内部同样通过 resolveDownloadApkPath 统一处理下载链接
     */
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
}
