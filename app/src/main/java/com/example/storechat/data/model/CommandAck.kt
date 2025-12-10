package com.example.storechat.data.model

import com.google.gson.Gson

/**
 * 设备指令回执数据模型
 * 用于通过 MQTT 发送给 Topic: acmsIOTEMQXReceive/ack
 *
 * @param msgId 消息ID (指令ID, 必填)
 * @param deviceId 设备唯一标识 (必填)
 * @param status 执行状态 (SUCCESS / FAILED, 必填)
 * @param errorMsg 错误信息 (当 status 为 FAILED 时必填)
 * @param timestamp 回执时间戳 (选填)
 * @param extra 扩展字段 (可选)
 */
data class CommandAck(
    val msgId: String,
    val deviceId: String,
    val status: String,
    val errorMsg: String,
    val timestamp: Long? = System.currentTimeMillis(),
    val extra: Map<String, Any>? = null
) {
    /**
     * 将指令回执数据对象转换为 JSON 字符串
     */
    fun toJson(): String {
        return Gson().toJson(this)
    }

    companion object {
        const val STATUS_SUCCESS = "SUCCESS"
        const val STATUS_FAILED = "FAILED"
    }
}
