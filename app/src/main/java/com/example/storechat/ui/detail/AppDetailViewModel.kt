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

    // ✅ 历史版本详情：保存“你点击的那一条版本信息”（版本号/版本ID/安装状态）
    private var historyOverride: AppInfo? = null

    // ✅ 缓存：最新版本基础信息（来自 allApps）
    private var baseFromRepo: AppInfo? = null

    init {
        // ✅ 监听下载队列变化：用于刷新“历史版本”的按钮状态（下载中/暂停/进度）
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
    }

    /**
     * ✅ 历史版本详情：展示该版本的 installState + 下载状态（按 packageName+versionId）
     */
    fun setHistoryAppInfo(app: AppInfo) {
        historyOverride = app
        loadApp(app.packageName)
    }

    fun selectHistoryVersion(historyVersion: HistoryVersion) {
        val currentApp = baseFromRepo ?: return
        val historyAppInfo = currentApp.copy(
            versionId = historyVersion.versionId,
            versionName = historyVersion.versionName,
            description = historyVersion.apkPath, // Use apkPath as description for history version
            installState = historyVersion.installState
        )
        setHistoryAppInfo(historyAppInfo)
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

        // ✅ 历史版本详情：去 downloadQueue 找到该版本的实时下载状态/进度
        val queueItemForThisVersion = AppRepository.downloadQueue.value
            ?.firstOrNull { it.packageName == base.packageName && it.versionId == override.versionId }

        val statusSource = queueItemForThisVersion ?: override

        _appInfo.value = base.copy(
            // 版本信息必须使用历史版本
            versionId = override.versionId,
            versionName = override.versionName,
            description = override.description,

            // ✅ 安装状态也必须使用历史版本（从历史列表传进来）
            installState = override.installState,

            // ✅ 下载状态/进度使用该历史版本对应的队列项（如果存在）
            downloadStatus = statusSource.downloadStatus,
            progress = statusSource.progress,
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
