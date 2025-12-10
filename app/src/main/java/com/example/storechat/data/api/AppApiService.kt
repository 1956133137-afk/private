package com.example.storechat.data.api

import com.example.storechat.model.VersionInfo
import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.POST


interface AppApiService {

    @POST("iotDeviceData/queryAppList")
    suspend fun getAppList(
        @Body body: AppListRequest
    ): AppListResponse

    @POST("app/history")
    suspend fun getAppHistory(
        @Body body: AppHistoryRequest
    ): List<VersionInfo>

    @POST("iotDeviceData/queryAppVersionList")
    suspend fun checkUpdate(
        @Body body: CheckUpdateRequest
    ): VersionInfo?

    @POST("app/download_link")
    suspend fun getDownloadLink(
        @Body body: DownloadLinkRequest
    ): DownloadLinkResponse

    @POST("iotDeviceData/getDeviceMQTTInfo")
    suspend fun getMqttInfo(
        @Body body: MqttInitBizBody
    ): ApiWrapper<MqttInfo>
}

data class DownloadLinkResponse(
    val url: String,
    val expireTime: Long? = null
)

data class AppHistoryRequest(val packageName: String)

data class CheckUpdateRequest(
    @SerializedName("appId")
    val packageName: String,
    @SerializedName("version")
    val currentVer: String
)

data class DownloadLinkRequest(
    val packageName: String,
    val versionName: String? = null
)

data class MqttInitBizBody(
    val deviceId: String,
    val deviceName: String? = null,
    val appId: String? = null,
    val version: String? = null,
    val publicIp: String? = null,
    val cpuUsage: String? = null,
    val memoryUsage: String? = null,
    val storageUsage: String? = null,
    val remark: String? = null,
)

data class ApiWrapper<T>(
    val msg: String,
    val code: Int,
    val data: T?
)

data class MqttInfo(
    val username: String,
    val password: String,
    val url: String,
    val serverUrl: String,
    val serverPort: String,
    val topic: String,
    val emqxHttpApiUrl: String,
    val emqxHttpApiName: String,
    val emqxHttpApiPassword: String,
    val emqxHttpApiBaseUrl: String
)
