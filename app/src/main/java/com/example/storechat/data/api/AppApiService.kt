package com.example.storechat.data.api

import com.example.storechat.model.AppInfo
import com.example.storechat.model.VersionInfo
import retrofit2.http.GET
import retrofit2.http.Query

interface AppApiService {

    // 获取首页应用列表
    // 假设后端接口为 /api/apps?category=1
    @GET("api/apps")
    suspend fun getAppList(@Query("category") category: String): List<AppInfo>

    // 获取指定应用的详情/历史版本
    @GET("api/app/history")
    suspend fun getAppHistory(@Query("packageName") packageName: String): List<VersionInfo>

    // 检查更新
    @GET("api/app/check_update")
    suspend fun checkUpdate(@Query("packageName") packageName: String, @Query("currentVer") version: String): VersionInfo?
}
