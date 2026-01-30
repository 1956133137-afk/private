package com.example.storechat.ui.search

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.storechat.data.AppRepository
import com.example.storechat.model.AppInfo
import com.example.storechat.model.InstallState
import com.example.storechat.model.UpdateStatus

class SearchViewModel : ViewModel() {


    val appVersion: LiveData<String>
    val checkUpdateResult: LiveData<UpdateStatus?>
    val result: LiveData<List<AppInfo>>

    private val _searchKeyword = MutableLiveData("")
    private val _resultMediator = MediatorLiveData<List<AppInfo>>()

    private val _navigationEvent = MutableLiveData<String?>()
    val navigationEvent: LiveData<String?> = _navigationEvent

    init {
        appVersion = AppRepository.appVersion
        checkUpdateResult = AppRepository.checkUpdateResult
        result = _resultMediator

        _resultMediator.addSource(AppRepository.allApps) { allApps ->
            _resultMediator.value = filterApps(allApps, _searchKeyword.value ?: "")
        }
        _resultMediator.addSource(_searchKeyword) { keyword ->
            _resultMediator.value = filterApps(AppRepository.allApps.value, keyword)
        }
    }



    fun handleAppAction(app: AppInfo) {
        if (app.installState == InstallState.INSTALLED_LATEST) {
            _navigationEvent.value = app.packageName
        } else {
            AppRepository.toggleDownload(app)
        }
    }

    private fun filterApps(apps: List<AppInfo>?, keyword: String): List<AppInfo> {
        val appList = apps ?: emptyList()
        
    
        val filteredBySize = appList.filter { app ->
            app.size.isNotBlank() && app.size != "N/A" && app.size != "0B"
        }
        
        val kw = keyword.trim()
        if (kw.isEmpty()) return filteredBySize
        
        return filteredBySize.filter { it.name.contains(kw, ignoreCase = true) }
    }

    fun search(keyword: String) {
        _searchKeyword.value = keyword
    }

    fun checkAppUpdate() {
        AppRepository.checkAppUpdate()
    }

    fun clearUpdateResult() {
        AppRepository.clearUpdateResult()
    }

    fun onNavigationComplete() {
        _navigationEvent.value = null
    }
}
