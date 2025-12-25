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

    //  历史版本详情：保存“你点击的那一条版本信息”（版本号/版本ID/安装状态）
    private var historyOverride: AppInfo? = null

    //  缓存：最新版本基础信息（来自 allApps）
    private var baseFromRepo: AppInfo? = null

    init {
        //  监听下载队列变化：用于刷新“历史版本”的按钮状态（下载中/暂停/进度）
        _appInfo.addSource(AppRepository.downloadQueue) {
            recomputeAppInfo()
        }
    }

    fun loadApp(packageName: String) {
        appInfoSource?.let { _appInfo.removeSource(it) }

        val newSource = AppRepository.allApps.map { apps ->
            apps.find { it.packageName == packageName }
        }

        _appInfo.addSource(newSource) { appFromRepo ->
            baseFromRepo = appFromRepo
            recomputeAppInfo()
        }

        appInfoSource = newSource
    }

    /**
     * 最新版本详情：直接展示仓库状态
     */
    fun setAppInfo(app: AppInfo) {
        historyOverride = null
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
        // 【关键修复】如果当前已经有基础数据且包名一致，不要重新 loadApp，
        // 而是直接重新计算并应用 historyOverride。这样可以避免异步加载导致的数据闪烁或重置。
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
            // 如果历史版本有独立的描述，可以在这里设置；否则暂时用 apkPath 或保持原描述
            description = historyVersion.apkPath,
            installState = historyVersion.installState,
            downloadStatus = DownloadStatus.NONE,progress = 0
        )

        // 设置覆盖信息
        setHistoryAppInfo(historyAppInfo)

        // 切换到 Tab 0 (详情页)
        _switchToTab.value = 0 // Request to switch to the first tab
    }

    private fun recomputeAppInfo() {
        val base = baseFromRepo ?: return
        val override = historyOverride

        // 最新版本详情
        if (override == null) {
            _appInfo.value = base.copy(isHistory = false)
            return
        }

        // 2. 如果有历史版本覆盖，显示历史版本详情 (isHistory = true)
        // 尝试在下载队列中找到这个历史版本（通过 versionId 匹配）
        val queueItemForThisVersion = AppRepository.downloadQueue.value
            ?.firstOrNull { it.packageName == base.packageName && it.versionId == override.versionId }

        // 状态优先级：队列中的状态 > 传入的历史记录状态
        val statusSource = queueItemForThisVersion ?: override

        _appInfo.value = base.copy(
            // 核心信息使用 override (历史版本)
            versionId = override.versionId,
            versionName = override.versionName,
            description = override.description,
            installState = override.installState,

            // 下载状态使用 statusSource (可能是下载队列中的实时状态)
            downloadStatus = statusSource.downloadStatus,
            progress = statusSource.progress,

            // 标记为历史查看模式
            isHistory = true
        )
    }

    fun loadHistoryFor(context: Context, app: AppInfo) {
        if (historyVersionCache.containsKey(app.appId)) {
            _historyVersions.value = historyVersionCache[app.appId]
            return
        }

        viewModelScope.launch {
            _isLoading.postValue(true)
            val history = AppRepository.loadHistoryVersions(context, app)
            _historyVersions.postValue(history)
            _isLoading.postValue(false)
            historyVersionCache[app.appId] = history
        }
    }

    fun clearHistoryCache() {
        historyVersionCache.clear()
    }
}
