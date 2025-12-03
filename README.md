# StoreChat Application Documentation

This document provides a summary of the Android application "StoreChat", based on its `AndroidManifest.xml` file.

## Overview

The application is named "StoreChat" and uses the theme `@style/Theme.StoreChat`. It supports Right-to-Left (RTL) layouts and has backup features enabled.

## Permissions

The application requests the following permissions:

*   `android.permission.REQUEST_INSTALL_PACKAGES`: Allows requesting the installation of packages. This is likely for installing apps from within StoreChat.
*   `android.permission.READ_EXTERNAL_STORAGE`: Allows reading from external storage.
*   `android.permission.WRITE_EXTERNAL_STORAGE`: Allows writing to external storage.

**Note:** For modern Android versions, you should consider using Scoped Storage and the Storage Access Framework instead of broad storage permissions.

## Screen Density Adaptation

The app uses the `AndroidAutoSize` library for screen adaptation.
*   **Design Width:** The UI is designed based on a screen width of `570dp`.
*   **Adaptation Mode:** It uses a "mixed adaptation mode" (`autosize_is_adapting_by_default` is `false`), meaning that adaptation must be explicitly enabled for specific Activities or Fragments.

## Application Components

### Activities

*   `.MainActivity`: This is the main launcher activity of the application.
*   `.ui.detail.AppDetailActivity`: This activity likely displays the details of a specific application.
*   `.ui.search.SearchActivity`: This activity provides search functionality.
*   `.ui.download.DownloadQueueActivity`: This activity is likely used to display and manage a queue of downloads.

1. 顶层目录结构（逻辑层级）
   app/src/main/java/com/example/storechat/

MainActivity：应用入口 Activity，加载首页 HomeFragment

MainActivity

ui/：所有 UI 界面模块

home/：首页（搜索条 + Tab 分类 + 应用列表）

HomeViewModel.kt

HomeViewModel

HomeFragment（未显示，但布局引用了它）

detail/：应用详情页

AppDetailViewModel.kt

AppDetailViewModel

search/：搜索页

SearchViewModel.kt

SearchViewModel

download/：下载管理页

DownloadRecentAdapter.kt

DownloadRecentAdapter

data/：数据层（Repository 层）

AppRepository.kt：核心数据中心、模拟服务器、管理下载任务

AppRepository

model/：所有数据模型

AppInfo.kt 应用数据模型

AppInfo

HistoryVersion.kt 历史版本模型

HistoryVersion

VersionInfo.kt 版本信息

VersionInfo

xc/：外部静默安装服务封装

MyService.kt（提供 silentInstallApk 之类方法）

MyService

🎨 2. UI 布局结构（res/layout）
fragment_home.xml（首页主要结构）

包含：

顶部搜索栏（Logo + 搜索块 + 下载管理图标）

fragment_home

中间内容卡片（分类 Tab + 应用列表 RecyclerView）

fragment_home

底部版本号区域

fragment_home

其他：

item_app（应用列表项）

fragment_detail（应用详情）

fragment_search（搜索结果）

item_recent_app（最近下载）

⚙️ 3. AndroidManifest 权限与 Activity

Manifest 显示项目是一个完整的 Android App：

入口 Activity：MainActivity

AndroidManifest

其他页面：

AppDetailActivity（详情）

SearchActivity（搜索）

DownloadQueueActivity（下载队列）

权限包括：

安装 APK 权限：REQUEST_INSTALL_PACKAGES

AndroidManifest

文件权限：读取/写入存储

🔧 4. 业务逻辑层（Repository）

最重要的业务代码在 AppRepository.kt：

① 管理 APP 列表

按分类生成、管理 YANNUO / ICBC / CCB 三类应用

AppRepository

② 查询更新

模拟服务器延迟返回版本更新状态

AppRepository

③ 下载流程

模拟下载过程 + 更新 UI 状态：
包括

DOWNLOADING → PAUSED → VERIFYING → INSTALLING → INSTALLED_LATEST

AppRepository

涉及下载进度、暂停、继续、安装、版本更新。

🧩 5. MVVM 架构清晰分层
ViewModel 层

HomeViewModel
控制首页列表、分类切换、顶部版本号

HomeViewModel

SearchViewModel
根据关键字过滤应用

SearchViewModel

AppDetailViewModel
加载应用详情与历史版本

AppDetailViewModel

Repository 层

一个对象 AppRepository 负责作为 单一数据源（Single Source of Truth）

UI 层

Fragment + RecyclerView + DataBinding

📥 6. 下载模块
主要文件

DownloadRecentAdapter.kt

DownloadRecentAdapter

功能：

展示最近下载应用列表

使用 ListAdapter + DiffUtil

点击跳转到详情页

🚀 7. 项目整体说明

结合所有文件，该项目包含如下功能：

功能	说明
首页分类展示	3 大分类标签，展示对应 app 列表
搜索	模糊搜索应用名称
下载	支持下载、暂停、继续、安装全流程
应用详情	展示版本信息、历史版本
静默安装	通过 xc/MyService 进行 silent install（模拟）
版本更新检查	顶部自动显示“最新版本”或“有新版本”
✅ 总结

你的 StoreChat 项目是一个 完整的 Android 应用商店 Demo，采用：

Kotlin + MVVM + LiveData

Repository + ViewModel 分层清晰

模拟下载 + 本地应用列表 + 动态 UI 更新

静默安装服务集成（xc/MyService）

完整界面布局与适配支持