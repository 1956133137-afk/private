package com.example.storechat.ui.detail

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

    fun loadApp(packageName: String) {
        appInfoSource?.let { _appInfo.removeSource(it) }

        val newSource = AppRepository.allApps.map { apps ->
            apps.find { it.packageName == packageName }
        }

        _appInfo.addSource(newSource) { app ->
            app?.let { _appInfo.value = it }
        }
        appInfoSource = newSource
    }

    fun setAppInfo(app: AppInfo) {
        appInfoSource?.let { _appInfo.removeSource(it) }
        appInfoSource = null
        _appInfo.value = app
    }

    fun loadHistoryFor(app: AppInfo) {
        viewModelScope.launch {
            val history = AppRepository.loadHistoryVersions(app)
            _historyVersions.postValue(history)
        }
    }
}
