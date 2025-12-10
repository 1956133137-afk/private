package com.example.storechat.data.model

import com.google.gson.annotations.SerializedName

/**
 * 代表应用商店中的单个应用信息
 */
data class AppInfo(
    @SerializedName("productName")
    val productName: String?, // 改为可空，增加健壮性

    @SerializedName("appId")
    val appId: String, // appId通常是必需的，保持非空

    @SerializedName("appCategory")
    val appCategory: Int?, // 改为可空

    @SerializedName("createTime")
    val createTime: String?, // 改为可空

    @SerializedName("updateTime")
    val updateTime: String?, // 本身就是可空

    @SerializedName("remark")
    val remark: String? // 本身就是可空
)
