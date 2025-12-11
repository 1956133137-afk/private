package com.example.storechat.data.api

import com.example.storechat.data.model.AppListResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface AppApi {
    /**
     * 获取应用商店的应用列表
     */
    @POST("api/app/list") // <-- TODO: 请务必替换为您的真实接口路径
    suspend fun getAppList(@Body body: AppListBizBody): AppListResponse
}
