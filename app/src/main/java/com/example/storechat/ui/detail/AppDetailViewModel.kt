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

    // 添加加载状态
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    // 缓存历史版本数据
    private val historyVersionCache = mutableMapOf<String, List<HistoryVersion>>()

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

    fun loadHistoryFor(context: Context, app: AppInfo) {
        // 检查是否有缓存数据
        if (historyVersionCache.containsKey(app.appId)) {
            // 使用缓存数据
            _historyVersions.value = historyVersionCache[app.appId]
            return
        }

        // 没有缓存数据，发送网络请求
        viewModelScope.launch {
            _isLoading.postValue(true)
            val history = AppRepository.loadHistoryVersions(context, app)
            _historyVersions.postValue(history)
            _isLoading.postValue(false)
            
            // 缓存数据
            historyVersionCache[app.appId] = history
        }
    }
    
    // 清除缓存的方法（可根据需要调用）
    fun clearHistoryCache() {
        historyVersionCache.clear()
    }
}