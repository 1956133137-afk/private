package com.example.storechat.data.api

import com.example.storechat.data.model.AppListResponse
import com.example.storechat.data.mqtt.MqttManager
// 确保这个类存在
import retrofit2.http.Body
import retrofit2.http.POST

interface AppApi {
    /**
     * 获取 MQTT 连接信息
     * 假设请求体是 MqttInitBizBody，响应体是 MqttInfoResponse
     */
    @POST("api/mqtt/init") // <-- TODO: 请务必替换为您的真实接口路径
    suspend fun getMqttInfo(@Body body: MqttInitBizBody): MqttManager

    /**
     * 获取应用商店的应用列表
     */
    @POST("api/app/list") // <-- TODO: 请务必替换为您的真实接口路径
    suspend fun getAppList(@Body body: AppListBizBody): AppListResponse
}
