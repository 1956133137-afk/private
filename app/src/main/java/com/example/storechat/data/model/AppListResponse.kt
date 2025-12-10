package com.example.storechat.data.model

/**
 * 应用列表接口的完整响应体
 */
data class AppListResponse(
    val msg: String?, // 改为可空
    val code: Int,
    val data: List<AppInfo>?
)
