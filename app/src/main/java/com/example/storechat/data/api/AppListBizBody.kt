package com.example.storechat.data.api

/**
 * 获取应用列表接口的业务请求体 (BizBody)
 * @param appId 应用ID，可选
 * @param appCategory 应用分类，可选. 0-未设置 1-建行 2-工行 3-智慧通行 4-电子班牌
 */
data class AppListBizBody(
    val appId: String? = null,
    val appCategory: Int? = null
)
