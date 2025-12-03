package com.example.storechat.ui.detail

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import com.example.storechat.data.AppRepository
import com.example.storechat.model.AppInfo
import com.example.storechat.model.HistoryVersion

class AppDetailViewModel : ViewModel() {

    private val _packageName = MutableLiveData<String>()

    val appInfo: LiveData<AppInfo> = _packageName.switchMap { packageName ->
        val result = MediatorLiveData<AppInfo>()
        result.addSource(AppRepository.allApps) { apps ->
            apps.find { it.packageName == packageName }?.let { foundApp ->
                if (result.value != foundApp) {
                    result.value = foundApp
                }
            }
        }
        result
    }

    // 历史版本列表
    private val _historyVersions = MutableLiveData<List<HistoryVersion>>()
    val historyVersions: LiveData<List<HistoryVersion>> = _historyVersions

    fun loadApp(packageName: String) {
        if (_packageName.value != packageName) {
            _packageName.value = packageName
        }
    }

    /**
     * 加载指定应用的历史版本（目前使用假数据）
     */
    fun loadHistoryFor(app: AppInfo) {
        // 在真实项目中，这里应该从数据库或网络加载
        val fakeHistory = listOf(
            HistoryVersion("1.0.2", "/sdcard/apks/${app.packageName}_102.apk"),
            HistoryVersion("1.0.1", "/sdcard/apks/${app.packageName}_101.apk"),
            HistoryVersion("1.0.0", "/sdcard/apks/${app.packageName}_100.apk")
        )
        _historyVersions.value = fakeHistory
    }
}
