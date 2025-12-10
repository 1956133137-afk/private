package com.example.storechat.data.api

import com.google.gson.annotations.SerializedName

data class AppListRequest(
    @SerializedName("appId")
    val appId: String?,
    @SerializedName("appCategory")
    val appCategory: Int?
)

data class AppListResponse(
    @SerializedName("msg")
    val msg: String,
    @SerializedName("code")
    val code: Int,
    @SerializedName("data")
    val data: List<AppListItem>
)

data class AppListItem(
    @SerializedName("productName")
    val productName: String,
    @SerializedName("appId")
    val appId: String,
    @SerializedName("appCategory")
    val appCategory: Int,
    @SerializedName("createTime")
    val createTime: String,
    @SerializedName("updateTime")
    val updateTime: String?,
    @SerializedName("remark")
    val remark: String?
)
