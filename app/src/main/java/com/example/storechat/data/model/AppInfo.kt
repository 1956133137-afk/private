package com.example.storechat.data.model

import com.google.gson.annotations.SerializedName


data class AppInfo(
    @SerializedName("productName")
    val productName: String?,

    @SerializedName("appId")
    val appId: String,

    @SerializedName("appCategory")
    val appCategory: Int?,

    @SerializedName("createTime")
    val createTime: String?,

    @SerializedName("updateTime")
    val updateTime: String?,

    @SerializedName("remark")
    val remark: String?
)
