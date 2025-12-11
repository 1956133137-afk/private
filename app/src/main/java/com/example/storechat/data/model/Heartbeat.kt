package com.example.storechat.data.model

import com.google.gson.Gson

/**
 * 设备心跳数据模型
 * 用于通过 MQTT 发送给 Topic: acmsIOTEMQXReceive/heartbeat
 *
 * @param appId 应用 ID (必填)
 * @param deviceName 设备名称 (必填)
 * @param version 软件版本 (必填)
 * @param deviceId 设备唯一标识 (必填)
 * @param publicIp 公网 IP (选填)
 * @param cpuUsage CPU 使用率 (选填)
 * @param memoryUsage 内存使用率 (选填)
 * @param storageUsage 存储使用率 (选填)
 * @param remark 备注信息 (选填)
 * @param timestamp 心跳时间戳 (选填, 建议有)
 * @param extra 扩展字段 (可选)
 */
data class Heartbeat(
    val appId: String,
    val deviceName: String,
    val version: String,
    val deviceId: String,
    val publicIp: String? = null,
    val cpuUsage: String? = null,
    val memoryUsage: String? = null,
    val storageUsage: String? = null,
    val remark: String? = null,
    val timestamp: Long? = System.currentTimeMillis(),
    val extra: Map<String, Any>? = null
) {
    /**
     * 将心跳数据对象转换为 JSON 字符串
     */
    fun toJson(): String {
        return Gson().toJson(this)
    }
}
