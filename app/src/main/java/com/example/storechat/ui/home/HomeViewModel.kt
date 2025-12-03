package com.example.storechat.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.storechat.data.AppRepository
import com.example.storechat.model.AppCategory
import com.example.storechat.model.AppInfo
import com.example.storechat.model.InstallState
import com.example.storechat.model.UpdateStatus

class HomeViewModel : ViewModel() {

    // --- LiveData for UI --- //
    val appVersion: LiveData<String>
    val apps: LiveData<List<AppInfo>>
    val checkUpdateResult: LiveData<UpdateStatus?>

    // 用于从 ViewModel 向 UI 层发送“导航”等一次性事件
    private val _navigationEvent = MutableLiveData<String?>()
    val navigationEvent: LiveData<String?> = _navigationEvent

    init {
        // ViewModel 的数据直接来源于 Repository
        appVersion = AppRepository.appVersion
        apps = AppRepository.categorizedApps
        checkUpdateResult = AppRepository.checkUpdateResult
    }

    // --- Business Logic --- //

    /**
     * 处理应用列表项右侧按钮的点击事件，这是唯一的入口
     */
    fun handleAppAction(app: AppInfo) {
        if (app.installState == InstallState.INSTALLED_LATEST) {
            // 如果是最新版本，则触发“打开应用”的导航事件
            _navigationEvent.value = app.packageName
        } else {
            // 对于其他所有状态（未安装、可更新、下载中、已暂停），都调用 toggleDownload
            AppRepository.toggleDownload(app)
        }
    }

    fun selectCategory(category: AppCategory) {
        AppRepository.selectCategory(category)
    }

    fun checkAppUpdate() {
        AppRepository.checkAppUpdate()
    }

    fun clearUpdateResult() {
        AppRepository.clearUpdateResult()
    }

    /**
     * 当导航事件被处理后，调用此方法来“消费”这个事件
     */
    fun onNavigationComplete() {
        _navigationEvent.value = null
    }
}
