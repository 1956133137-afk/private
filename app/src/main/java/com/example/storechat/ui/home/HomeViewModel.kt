package com.example.storechat.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import com.example.storechat.data.AppRepository
import com.example.storechat.model.AppCategory
import com.example.storechat.model.AppInfo
import com.example.storechat.model.InstallState
import com.example.storechat.model.UpdateStatus

class HomeViewModel : ViewModel() {

    // --- 基础 UI 数据 --- //
    val appVersion: LiveData<String>

    // apps 通过 Mediator 做“分类列表 + 模糊搜索”组合
    private val _appsMediator = MediatorLiveData<List<AppInfo>>()
    val apps: LiveData<List<AppInfo>> = _appsMediator

    val checkUpdateResult: LiveData<UpdateStatus?>

    // 导航事件：打开已安装应用
    private val _navigationEvent = MutableLiveData<String?>()
    val navigationEvent: LiveData<String?> = _navigationEvent

    // ============ 首页内联搜索（横屏用） ============

    /** 当前搜索关键词（横屏内联搜索） */
    private val _searchKeyword = MutableLiveData("")

    // ============ 下载图标相关状态 ============

    /** 下载队列（仓库已有） */
    private val downloadQueue: LiveData<List<AppInfo>> = AppRepository.downloadQueue

    /** 最近安装的应用（下载完成后加入） */
    private val recentInstalled: LiveData<List<AppInfo>> = AppRepository.recentInstalledApps

    /** 是否有下载任务在进行：队列非空即为 true */
    val isDownloadInProgress: LiveData<Boolean> =
        downloadQueue.map { list -> !list.isNullOrEmpty() }

    /** 所有下载任务的平均进度（0~100）：驱动右上角进度圈 */
    val totalDownloadProgress: LiveData<Int> =
        downloadQueue.map { list ->
            if (list.isNullOrEmpty()) {
                0
            } else {
                val sum = list.sumOf { it.progress.coerceIn(0, 100) }
                sum / list.size
            }
        }

    /** 用户是否手动点击过下载图标清除红点 */
    private val _downloadDotClearedManually = MutableLiveData(false)

    /**
     * 是否显示“下载完成”红点：
     * 1. 有最近安装记录
     * 2. 当前没有下载在进行
     * 3. 用户没有手动清除
     */
    val downloadFinishedDotVisible: LiveData<Boolean> =
        object : MediatorLiveData<Boolean>() {
            init {
                fun update() {
                    val hasRecent = recentInstalled.value?.isNotEmpty() == true
                    val inProgress = isDownloadInProgress.value == true
                    val cleared = _downloadDotClearedManually.value == true
                    value = hasRecent && !inProgress && !cleared
                }

                addSource(recentInstalled) {
                    // 有新的安装完成时，允许再次显示红点
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

        // apps = 分类列表 + 搜索关键词 的组合
        _appsMediator.addSource(AppRepository.categorizedApps) { list ->
            val kw = _searchKeyword.value.orEmpty()
            _appsMediator.value = filterApps(list, kw)
        }
        _appsMediator.addSource(_searchKeyword) { kw ->
            _appsMediator.value = filterApps(AppRepository.categorizedApps.value, kw)
        }
    }

    // ====== 模糊搜索逻辑（首页复用） ======

    private fun filterApps(apps: List<AppInfo>?, keyword: String): List<AppInfo> {
        val list = apps ?: emptyList()
        val kw = keyword.trim()
        if (kw.isEmpty()) return list

        return list.filter { app ->
            app.name.contains(kw, ignoreCase = true)
            // 如果想更模糊，可以加上包名/描述：
            // || app.packageName.contains(kw, ignoreCase = true)
            // || app.description.contains(kw, ignoreCase = true)
        }
    }

    /** 横屏首页调用：更新搜索关键词，触发 apps 重新过滤 */
    fun inlineSearch(keyword: String) {
        _searchKeyword.value = keyword
    }

    /** 切换分类时清空搜索（恢复该分类完整列表） */
    private fun clearInlineSearch() {
        _searchKeyword.value = ""
    }

    // --- Business Logic --- //

    fun handleAppAction(app: AppInfo) {
        if (app.installState == InstallState.INSTALLED_LATEST) {
            _navigationEvent.value = app.packageName
        } else {
            AppRepository.toggleDownload(app)
        }
    }

    fun selectCategory(category: AppCategory) {
        clearInlineSearch()
        AppRepository.selectCategory(category)
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

    /** 点击下载图标：只负责清除红点（跳转逻辑在 Fragment 里） */
    fun onDownloadIconClicked() {
        _downloadDotClearedManually.value = true
    }
}
