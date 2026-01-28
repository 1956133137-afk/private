package com.example.storechat.ui.detail

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import com.example.storechat.data.AppRepository
import com.example.storechat.model.AppInfo
import com.example.storechat.model.DownloadStatus
import com.example.storechat.model.HistoryVersion
import com.example.storechat.model.InstallState
import kotlinx.coroutines.launch

class AppDetailViewModel : ViewModel() {

    private val _appInfo = MediatorLiveData<AppInfo>()
    val appInfo: LiveData<AppInfo> = _appInfo

    private var appInfoSource: LiveData<AppInfo?>? = null

    private val _historyVersions = MutableLiveData<List<HistoryVersion>>()
    val historyVersions: LiveData<List<HistoryVersion>> = _historyVersions

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _switchToTab = MutableLiveData<Int>()
    val switchToTab: LiveData<Int> = _switchToTab

    val isHistoryTabSelected = MutableLiveData<Boolean>(false)

    private val historyVersionCache = mutableMapOf<String, List<HistoryVersion>>()

    // 保存网络请求回来的原始历史版本列表，用于后续状态刷新
    private var rawHistoryVersions: List<HistoryVersion>? = null

    //  历史版本详情：保存“你点击的那一条版本信息”（版本号/版本ID/安装状态）
    private var historyOverride: AppInfo? = null

    //  缓存：最新版本基础信息（来自 allApps）
    private var baseFromRepo: AppInfo? = null

    // 【核心修复】新增：用于锁定当前应用的唯一ID
    private var trackedAppId: String? = null

    init {
        //  监听下载队列变化：用于刷新“历史版本”的按钮状态（下载中/暂停/进度）
        _appInfo.addSource(AppRepository.downloadQueue) {
            recomputeAppInfo()
        }
    }

    fun loadApp(packageName: String) {
        appInfoSource?.let { _appInfo.removeSource(it) }

        // 【核心修复】修改监听逻辑：优先使用 ID 匹配，防止包名变更导致丢失
        val newSource = AppRepository.allApps.map { apps ->
            // 策略1：如果已经锁定了 appId，直接通过 ID 查找（最稳健，不受包名变化影响）
            if (trackedAppId != null) {
                val match = apps.find { it.appId == trackedAppId }
                if (match != null) return@map match
            }

            // 策略2：如果还没锁定 ID，通过传入的包名查找
            val match = apps.find { it.packageName == packageName }
            if (match != null) {
                // 找到后立即锁定 ID，确保后续安装成功包名变更时能继续追踪
                trackedAppId = match.appId
            }
            match
        }

        _appInfo.addSource(newSource) { appFromRepo ->

            // 如果应用在仓库中因某种原因消失（极为罕见），保持最后状态不更新为 null
            if (appFromRepo == null && baseFromRepo != null) {
                return@addSource
            }
            baseFromRepo = appFromRepo
            // 每次仓库数据更新（例如安装成功后），重新计算当前显示详情和列表状态
            recomputeAppInfo()
            updateHistoryVersionsState()
        }

        appInfoSource = newSource
    }

    /**
     * 最新版本详情：直接展示仓库状态
     */
    fun setAppInfo(app: AppInfo) {
        historyOverride = null
        // 【核心修复】直接锁定 appId
        trackedAppId = app.appId
        loadApp(app.packageName)
        loadAppSize(app)
    }

    private fun loadAppSize(app: AppInfo) {
        if (app.size == "N/A") {
            AppRepository.fetchAndSetAppSize(app)
        }
    }

    /**
     * 历史版本详情：展示该版本的 installState + 下载状态（按 packageName+versionId）
     */
    fun setHistoryAppInfo(app: AppInfo) {
        historyOverride = app
        // 如果当前已经有基础数据且包名一致，直接重新计算，避免闪烁
        if (baseFromRepo != null && baseFromRepo?.packageName == app.packageName) {
            recomputeAppInfo()
        } else {
            loadApp(app.packageName)
        }
    }

    fun selectHistoryVersion(historyVersion: HistoryVersion) {
        val currentApp = baseFromRepo ?: return
        // 构造一个临时的 AppInfo，携带历史版本的信息
        val historyAppInfo = currentApp.copy(
            versionId = historyVersion.versionId,
            versionName = historyVersion.versionName,
            versionCode = historyVersion.versionCode, // 【修复】必须传递历史版本的 versionCode
            // 如果历史版本有独立的描述，可以在这里设置；否则暂时用 apkPath 或保持原描述
            description = historyVersion.apkPath,
            installState = historyVersion.installState,
            downloadStatus = DownloadStatus.NONE,
            progress = 0
        )

        // 设置覆盖信息
        setHistoryAppInfo(historyAppInfo)

        // 切换到 Tab 0 (详情页)
        _switchToTab.value = 0
    }

    private fun recomputeAppInfo() {
        val base = baseFromRepo ?: return
        val override = historyOverride

        // 1. 最新版本详情
        if (override == null) {
            _appInfo.value = base.copy(isHistory = false)
            return
        }

        // 2. 历史版本详情

        // 尝试在下载队列中找到这个历史版本
        val queueItemForThisVersion = AppRepository.downloadQueue.value
            ?.firstOrNull { it.packageName == base.packageName && it.versionId == override.versionId }

        // 【核心修复】动态计算历史版本的安装状态
        // 使用 base.installedVersionCode (来自 Repository 的最新数据) 与当前历史版本的 versionCode 进行比对。
        val dynamicInstallState = if (base.installedVersionCode != 0L && override.versionCode != null && base.installedVersionCode == override.versionCode.toLong()) {
            InstallState.INSTALLED_LATEST // 如果版本号一致，视为"已安装"（显示"打开"）
        } else {
            InstallState.NOT_INSTALLED // 否则一律视为"未安装"（显示"安装"）
        }

        // 状态优先级：队列中的状态 > 动态计算的安装状态 > 覆盖记录的原始状态(fallback)
        val finalInstallState = queueItemForThisVersion?.installState ?: dynamicInstallState
        val finalDownloadStatus = queueItemForThisVersion?.downloadStatus ?: DownloadStatus.NONE
        val finalProgress = queueItemForThisVersion?.progress ?: 0

        _appInfo.value = base.copy(
            // 核心信息使用 override (历史版本)
            versionId = override.versionId,
            versionName = override.versionName,
            versionCode = override.versionCode, // 确保版本号也同步过去
            description = override.description,

            // 使用动态计算的状态
            installState = finalInstallState,

            // 下载状态使用 statusSource (可能是下载队列中的实时状态)
            downloadStatus = finalDownloadStatus,
            progress = finalProgress,

            // 标记为历史查看模式
            isHistory = true
        )
    }

    fun loadHistoryFor(context: Context, app: AppInfo) {
        if (historyVersionCache.containsKey(app.appId)) {
            rawHistoryVersions = historyVersionCache[app.appId]
            updateHistoryVersionsState() // 使用缓存也需要刷新状态
            return
        }

        viewModelScope.launch {
            _isLoading.postValue(true)
            val history = AppRepository.loadHistoryVersions(context, app)
            rawHistoryVersions = history
            historyVersionCache[app.appId] = history

            updateHistoryVersionsState() // 加载完成后刷新状态

            _isLoading.postValue(false)
        }
    }

    // 根据当前的 installedVersionCode 刷新历史列表的状态
    private fun updateHistoryVersionsState() {
        val raw = rawHistoryVersions ?: return
        val currentInstalledCode = baseFromRepo?.installedVersionCode ?: 0L

        val updatedList = raw.map { historyVer ->
            val code = historyVer.versionCode?.toLong() ?: -1L
            // 只有当版本号完全一致时，才认为是“已安装”
            val newState = if (code != -1L && code == currentInstalledCode) {
                InstallState.INSTALLED_LATEST
            } else {
                InstallState.NOT_INSTALLED
            }
            historyVer.copy(installState = newState)
        }
        _historyVersions.postValue(updatedList)
    }

    fun clearHistoryCache() {
        historyVersionCache.clear()
        rawHistoryVersions = null
    }
}
