package com.example.storechat.data.api

import com.example.storechat.model.AppInfo
import com.example.storechat.model.VersionInfo
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * 后端接口定义占位，方便后续根据文档调整。
 * 目前字段和路径只是示例，可以在接入真实接口时修改。
 */

// b. 下载链接响应模型（占位）
data class DownloadLinkResponse(
    val url: String,          // 有效期下载链接
    val expireTime: Long? = null  // 过期时间（时间戳，可选）
)

// 业务层真正要传的“data”内容（拦截器会把它包起来）
data class AppListRequest(val category: String)

data class AppHistoryRequest(val packageName: String)

data class CheckUpdateRequest(
    val packageName: String,
    val currentVer: String
)

data class DownloadLinkRequest(
    val packageName: String,
    val versionName: String? = null
)

/* ======================  这里开始是 MQTT 相关  ====================== */

// MQTT 初始化请求的业务字段（会被拦截器包到 data 里）
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

// 通用返回包装
data class ApiWrapper<T>(
    val msg: String,
    val code: Int,
    val data: T?
)

// MQTT 连接信息（按你给的返回示例来）
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

/* ======================  Retrofit 接口定义  ====================== */

interface AppApiService {

    // a. 应用列表接口
    @POST("api/apps")
    suspend fun getAppList(
        @Body body: AppListRequest
    ): List<AppInfo>

    // c. 应用历史版本列表接口
    @POST("api/app/history")
    suspend fun getAppHistory(
        @Body body: AppHistoryRequest
    ): List<VersionInfo>

    // 检查更新
    @POST("api/app/check_update")
    suspend fun checkUpdate(
        @Body body: CheckUpdateRequest
    ): VersionInfo?

    // b. 下载链接获取接口
    @POST("api/app/download_link")
    suspend fun getDownloadLink(
        @Body body: DownloadLinkRequest
    ): DownloadLinkResponse

    // ⭐ 新增：获取 MQTT 连接信息（设备初始化）
    @POST("openapi/iotDeviceData/getDeviceMQTTInfo")
    suspend fun getMqttInfo(
        @Body body: MqttInitBizBody
    ): ApiWrapper<MqttInfo>
}
