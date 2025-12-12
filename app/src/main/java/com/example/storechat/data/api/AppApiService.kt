package com.example.storechat.data.api

import com.example.storechat.model.VersionInfo
import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.POST

interface AppApiService {

    @POST("iotDeviceData/queryAppList")
    suspend fun getAppList(
        @Body body: AppListRequestBody
    ): ApiWrapper<List<AppInfoResponse>>

    @POST("iotDeviceData/queryAppVersionList")
    suspend fun getAppHistory(
        @Body body: AppVersionHistoryRequest
    ): AppVersionHistoryResponse

    @POST("iotDeviceData/queryAppVersionList")
    suspend fun checkUpdate(
        @Body body: CheckUpdateRequest
    ): VersionInfo?

    @POST("iotDeviceData/appVersionDownload")
    suspend fun getDownloadUrl(
        @Body body: GetDownloadUrlRequest
    ): ApiWrapper<DownloadLinkData>
}

// --- Wrapper --- //
data class ApiWrapper<T>(
    val msg: String,
    val code: Int,
    val data: T?
)

// --- App List --- //
data class AppListRequestBody(
    val appId: String? = null,
    val appCategory: Int? = null
)

data class AppInfoResponse(
    val productName: String,
    val appId: String,
    val appCategory: Int,
    val id: Int?,
    val version: String?,
    val versionCode: String?,
    val versionDesc: String?,
    val status: Int?,
    val createTime: String,
    val updateTime: String?,
    val remark: String?
)

// --- Download URL --- //
data class GetDownloadUrlRequest(
    val appId: String,
    val id: Long
)

data class DownloadLinkData(
    val id: Long,
    val appId: String,
    val fileUrl: String,
    val version: String,
    val versionCode: String,
    val versionDesc: String?,
    val status: Int,
    val createTime: String,
    val updateTime: String,
    val remark: String?
)

// --- History Version --- //
data class AppVersionHistoryRequest(
    val appId: String
)

data class AppVersionHistoryResponse(
    val code: Int,
    val msg: String,
    val data: List<HistoryVersionItem>?
)

data class HistoryVersionItem(
    val id: Long,
    val version: String,
    val versionCode: String
)

// --- Check Update --- //
data class CheckUpdateRequest(
    @SerializedName("appId")
    val packageName: String,
    @SerializedName("version")
    val currentVer: String
)
