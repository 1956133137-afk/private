package com.example.storechat.data.api

import com.google.gson.annotations.SerializedName

// --- Request ---

data class AppVersionHistoryRequest(
    @SerializedName("appId")
    val appId: String,
    @SerializedName("version")
    val version: String? = null // Optional, for querying a specific version
)

// --- Response ---

data class AppVersionHistoryResponse(
    @SerializedName("msg")
    val msg: String,
    @SerializedName("code")
    val code: Int,
    @SerializedName("data")
    val data: List<AppVersionHistoryItem>?
)

data class AppVersionHistoryItem(
    @SerializedName("appId")
    val appId: String,
    @SerializedName("fileUrl")
    val fileUrl: String?,
    @SerializedName("version")
    val version: String,
    @SerializedName("versionCode")
    val versionCode: String,
    @SerializedName("versionDesc")
    val versionDesc: String?,
    @SerializedName("status")
    val status: Int,
    @SerializedName("createTime")
    val createTime: String,
    @SerializedName("updateTime")
    val updateTime: String,
    @SerializedName("remark")
    val remark: String?
)
