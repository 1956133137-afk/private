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


    private var rawHistoryVersions: List<HistoryVersion>? = null


    private var historyOverride: AppInfo? = null


    private var baseFromRepo: AppInfo? = null


    private var trackedAppId: String? = null

    init {

        _appInfo.addSource(AppRepository.downloadQueue) {
            recomputeAppInfo()
        }
    }

    fun loadApp(packageName: String) {
        appInfoSource?.let { _appInfo.removeSource(it) }


        val newSource = AppRepository.allApps.map { apps ->

            if (trackedAppId != null) {
                val match = apps.find { it.appId == trackedAppId }
                if (match != null) return@map match
            }


            val match = apps.find { it.packageName == packageName }
            if (match != null) {

                trackedAppId = match.appId
            }
            match
        }

        _appInfo.addSource(newSource) { appFromRepo ->


            if (appFromRepo == null && baseFromRepo != null) {
                return@addSource
            }
            baseFromRepo = appFromRepo

            recomputeAppInfo()
            updateHistoryVersionsState()
        }

        appInfoSource = newSource
    }


    fun setAppInfo(app: AppInfo) {
        historyOverride = null
    
        trackedAppId = app.appId
        loadApp(app.packageName)
        loadAppSize(app)
    }

    private fun loadAppSize(app: AppInfo) {
        if (app.size == "N/A") {
            AppRepository.fetchAndSetAppSize(app)
        }
    }


    fun setHistoryAppInfo(app: AppInfo) {
        historyOverride = app

        if (baseFromRepo != null && baseFromRepo?.packageName == app.packageName) {
            recomputeAppInfo()
        } else {
            loadApp(app.packageName)
        }
    }

    fun selectHistoryVersion(historyVersion: HistoryVersion) {
        val currentApp = baseFromRepo ?: return

        val historyAppInfo = currentApp.copy(
            versionId = historyVersion.versionId,
            versionName = historyVersion.versionName,
            versionCode = historyVersion.versionCode, // 【修复】必须传递历史版本的 versionCode
    
            description = historyVersion.apkPath,
            installState = historyVersion.installState,
            downloadStatus = DownloadStatus.NONE,
            progress = 0
        )


        setHistoryAppInfo(historyAppInfo)


        _switchToTab.value = 0
    }

    private fun recomputeAppInfo() {
        val base = baseFromRepo ?: return
        val override = historyOverride


        if (override == null) {
            _appInfo.value = base.copy(isHistory = false)
            return
        }




        val queueItemForThisVersion = AppRepository.downloadQueue.value
            ?.firstOrNull { it.packageName == base.packageName && it.versionId == override.versionId }



        val dynamicInstallState = if (base.installedVersionCode != 0L && override.versionCode != null && base.installedVersionCode == override.versionCode.toLong()) {
            InstallState.INSTALLED_LATEST // 如果版本号一致，视为"已安装"（显示"打开"）
        } else {
            InstallState.NOT_INSTALLED // 否则一律视为"未安装"（显示"安装"）
        }


        val finalInstallState = queueItemForThisVersion?.installState ?: dynamicInstallState
        val finalDownloadStatus = queueItemForThisVersion?.downloadStatus ?: DownloadStatus.NONE
        val finalProgress = queueItemForThisVersion?.progress ?: 0

        _appInfo.value = base.copy(
    
            versionId = override.versionId,
            versionName = override.versionName,
            versionCode = override.versionCode, // 确保版本号也同步过去
            description = override.description,

    
            installState = finalInstallState,

    
            downloadStatus = finalDownloadStatus,
            progress = finalProgress,

    
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

    
    private fun updateHistoryVersionsState() {
        val raw = rawHistoryVersions ?: return
        val currentInstalledCode = baseFromRepo?.installedVersionCode ?: 0L

        val updatedList = raw.map { historyVer ->
            val code = historyVer.versionCode?.toLong() ?: -1L
    
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
