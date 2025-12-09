package com.example.storechat.data.api

import com.example.storechat.model.AppInfo
import com.example.storechat.model.VersionInfo
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * 后端接口定义占位，方便后续根据文档调整。
 * 目前字段和路径只是示例，可以在接入真实接口时修改。
 *
 * 此接口定义了应用程序与后端服务通信所需的所有API端点，
 * 包括应用列表获取、版本历史查询、更新检查、下载链接获取以及MQTT连接信息获取等功能
 */

// b. 下载链接响应模型（占位）
/**
 * 下载链接响应数据类
 * @property url 有效期下载链接
 * @property expireTime 过期时间（时间戳，可选）
 */
data class DownloadLinkResponse(
    val url: String,          // 有效期下载链接
    val expireTime: Long? = null  // 过期时间（时间戳，可选）
)

// 业务层真正要传的"data"内容（拦截器会把它包起来）
/**
 * 应用列表请求数据类
 * @property category 应用分类
 */
data class AppListRequest(val category: String)

/**
 * 应用历史版本请求数据类
 * @property packageName 应用包名
 */
data class AppHistoryRequest(val packageName: String)

/**
 * 更新检查请求数据类
 * @property packageName 应用包名
 * @property currentVer 当前版本号
 */
data class CheckUpdateRequest(
    val packageName: String,
    val currentVer: String
)

/**
 * 下载链接请求数据类
 * @property packageName 应用包名
 * @property versionName 版本名称（可选）
 */
data class DownloadLinkRequest(
    val packageName: String,
    val versionName: String? = null
)

/* ======================  这里开始是 MQTT 相关  ====================== */

// MQTT 初始化请求的业务字段（会被拦截器包到 data 里）
/**
 * MQTT初始化业务数据类
 * @property deviceId 设备ID
 * @property deviceName 设备名称（可选）
 * @property appId 应用ID（可选）
 * @property version 版本号（可选）
 * @property publicIp 公网IP（可选）
 * @property cpuUsage CPU使用率（可选）
 * @property memoryUsage 内存使用情况（可选）
 * @property storageUsage 存储使用情况（可选）
 * @property remark 备注信息（可选）
 */
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
/**
 * API通用返回包装类
 * @param T 泛型参数，表示实际的数据类型
 * @property msg 返回消息
 * @property code 返回状态码
 * @property data 实际数据内容（可选）
 */
data class ApiWrapper<T>(
    val msg: String,
    val code: Int,
    val data: T?
)

// MQTT 连接信息（按你给的返回示例来）
/**
 * MQTT连接信息数据类
 * @property username 用户名
 * @property password 密码
 * @property url MQTT连接URL
 * @property serverUrl 服务器URL
 * @property serverPort 服务器端口
 * @property topic 主题
 * @property emqxHttpApiUrl EMQX HTTP API URL
 * @property emqxHttpApiName EMQX HTTP API用户名
 * @property emqxHttpApiPassword EMQX HTTP API密码
 * @property emqxHttpApiBaseUrl EMQX HTTP API基础URL
 */
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

/**
 * 应用API服务接口
 * 定义了与应用商店后端服务通信的所有REST API端点
 */
interface AppApiService {

    // a. 应用列表接口
    /**
     * 获取应用列表
     * @param body 应用列表请求参数
     * @return 应用信息列表
     */
    @POST("api/apps")
    suspend fun getAppList(
        @Body body: AppListRequest
    ): List<AppInfo>

    // c. 应用历史版本列表接口
    /**
     * 获取应用历史版本列表
     * @param body 应用历史版本请求参数
     * @return 版本信息列表
     */
    @POST("api/app/history")
    suspend fun getAppHistory(
        @Body body: AppHistoryRequest
    ): List<VersionInfo>

    // 检查更新
    /**
     * 检查应用是否有新版本
     * @param body 更新检查请求参数
     * @return 版本信息（如果有更新的话）
     */
    @POST("api/app/check_update")
    suspend fun checkUpdate(
        @Body body: CheckUpdateRequest
    ): VersionInfo?

    // b. 下载链接获取接口
    /**
     * 获取应用下载链接
     * @param body 下载链接请求参数
     * @return 下载链接响应信息
     */
    @POST("api/app/download_link")
    suspend fun getDownloadLink(
        @Body body: DownloadLinkRequest
    ): DownloadLinkResponse

    // ⭐ 新增：获取 MQTT 连接信息（设备初始化）
    /**
     * 获取MQTT连接信息（用于设备初始化）
     * @param body MQTT初始化业务数据
     * @return 包含MQTT连接信息的API响应
     */
    @POST("openapi/iotDeviceData/getDeviceMQTTInfo")
    suspend fun getMqttInfo(
        @Body body: MqttInitBizBody
    ): ApiWrapper<MqttInfo>
}