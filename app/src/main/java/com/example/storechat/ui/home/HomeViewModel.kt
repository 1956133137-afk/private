package com.example.storechat.ui.home

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import com.example.storechat.data.AppRepository
import com.example.storechat.model.AppCategory
import com.example.storechat.model.AppInfo
import com.example.storechat.model.DownloadStatus
import com.example.storechat.model.InstallState
import com.example.storechat.model.UpdateStatus
import com.example.storechat.util.LogUtil
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

    private val TAG = "HomeViewModel"


    val appVersion: LiveData<String>

    private val _appsMediator = MediatorLiveData<List<AppInfo>>()
    val apps: LiveData<List<AppInfo>> = _appsMediator

    val checkUpdateResult: LiveData<UpdateStatus?>

    private val _navigationEvent = MutableLiveData<String?>()
    val navigationEvent: LiveData<String?> = _navigationEvent

    private val _isLoading = MutableLiveData(true)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _showNetworkError = MutableLiveData(false)
    val showNetworkError: LiveData<Boolean> = _showNetworkError

    private var timeoutJob: Job? = null

    private val sizeFetchTracker = mutableSetOf<String>()



    private val _searchKeyword = MutableLiveData("")



    private val downloadQueue: LiveData<List<AppInfo>> = AppRepository.downloadQueue

    private val recentInstalled: LiveData<List<AppInfo>> = AppRepository.recentInstalledApps

    val isDownloadInProgress: LiveData<Boolean> =
        downloadQueue.map { list -> list.any { it.downloadStatus == DownloadStatus.DOWNLOADING } }

    val totalDownloadProgress: LiveData<Int> =
        downloadQueue.map { list ->
            if (list.isNullOrEmpty()) {
                0
            } else {
                val sum = list.sumOf { it.progress.coerceIn(0, 100) }
                sum / list.size
            }
        }

    private val _downloadDotClearedManually = MutableLiveData(false)

    val downloadFinishedDotVisible: LiveData<Boolean> =
        object : MediatorLiveData<Boolean>() {
            init {
                fun update() {
                    val hasRecent = recentInstalled.value?.isNotEmpty() == true
                    val cleared = _downloadDotClearedManually.value == true
                    value = hasRecent && !cleared
                }

                addSource(recentInstalled) {
                    _downloadDotClearedManually.value = false
                    update()
                }
                addSource(isDownloadInProgress) { update() }
                addSource(_downloadDotClearedManually) { update() }
            }
        }

    init {
        appVersion = AppRepository.appVersion
        checkUpdateResult = AppRepository.checkUpdateResult

        _appsMediator.addSource(AppRepository.categorizedApps) { list ->
            if (!list.isNullOrEmpty()) {
                timeoutJob?.cancel()
                _isLoading.value = false
                _showNetworkError.value = false

                fetchSizesForList(list)

                val kw = _searchKeyword.value.orEmpty()
                _appsMediator.value = filterApps(list, kw)
            }
        }

        _appsMediator.addSource(_searchKeyword) { kw ->
            _appsMediator.value = filterApps(AppRepository.categorizedApps.value, kw)
        }
    }

    private fun fetchSizesForList(apps: List<AppInfo>) {
        apps.forEach { app ->
            if (app.size == "N/A" && !sizeFetchTracker.contains(app.appId)) {
                sizeFetchTracker.add(app.appId)
                AppRepository.fetchAndSetAppSize(app)
            }
        }
    }

    private fun filterApps(apps: List<AppInfo>?, keyword: String): List<AppInfo> {
        val list = apps ?: emptyList()

    
        val filteredBySize = list.filter { app ->
            app.size.isNotBlank() && app.size != "N/A" && app.size != "0B"
        }

        val kw = keyword.trim()
        if (kw.isEmpty()) return filteredBySize

        return filteredBySize.filter { app ->
            app.name.contains(kw, ignoreCase = true)
        }
    }

    fun inlineSearch(keyword: String) {
        _searchKeyword.value = keyword
    }

    private fun clearInlineSearch() {
        _searchKeyword.value = ""
    }

    fun handleAppAction(app: AppInfo) {
        if (app.installState == InstallState.INSTALLED_LATEST) {
            LogUtil.d(TAG, "Attempting to open app. PackageName: ${app.packageName}")
            _navigationEvent.value = app.packageName
        } else {
            AppRepository.toggleDownload(app)
        }
    }

    fun selectCategory(context: Context, category: AppCategory) {
        clearInlineSearch()
        _isLoading.value = true
        _showNetworkError.value = false
        _appsMediator.value = emptyList() // Immediately clear the list

        AppRepository.selectCategory(context, category)

        timeoutJob?.cancel()
        timeoutJob = viewModelScope.launch {
            delay(10000) // 10 seconds
            if (_appsMediator.value.isNullOrEmpty()) {
                _isLoading.value = false
                _showNetworkError.value = true
            }
        }
    }

    fun checkAppUpdate() {
        AppRepository.checkAppUpdate()
    }

    fun startSelfUpdate(status: UpdateStatus.NEW_VERSION) {
        AppRepository.startSelfUpdate(status)
    }

    fun clearUpdateResult() {
        AppRepository.clearUpdateResult()
    }

    fun onNavigationComplete() {
        _navigationEvent.value = null
    }

    fun onDownloadIconClicked() {
        _downloadDotClearedManually.value = true
    }
}
