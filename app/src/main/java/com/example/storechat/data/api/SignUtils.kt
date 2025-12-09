package com.example.storechat.data.api

import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object SignUtils {

    private const val HMAC_SHA256 = "HmacSHA256"

    /** 秒级时间戳 */
    fun generateTimestampSeconds(): Long = System.currentTimeMillis() / 1000

    /** 随机 nonce */
    fun generateNonce(length: Int = 16): String {
        val chars = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val random = SecureRandom()
        val sb = StringBuilder()
        repeat(length) {
            sb.append(chars[random.nextInt(chars.length)])
        }
        return sb.toString()
    }

    /**
     * 规范化 JSON：key 按字典序排序，嵌套对象递归处理
     * 返回字符串用于参与签名 + 填到 data 字段
     */
    fun canonicalJson(rawJson: String): String {
        if (rawJson.isBlank()) return "{}"
        val json = JSONObject(rawJson)
        return canonicalJson(json)
    }

    private fun canonicalJson(json: JSONObject): String {
        val keys = json.keys().asSequence().toList().sorted()
        val result = JSONObject()
        for (key in keys) {
            val value = json.get(key)
            when (value) {
                is JSONObject -> result.put(key, JSONObject(canonicalJson(value)))
                is JSONArray -> result.put(key, canonicalJsonArray(value))
                else -> result.put(key, value)
            }
        }
        return result.toString()
    }

    private fun canonicalJsonArray(array: JSONArray): JSONArray {
        val result = JSONArray()
        for (i in 0 until array.length()) {
            val v = array.get(i)
            when (v) {
                is JSONObject -> result.put(JSONObject(canonicalJson(v)))
                is JSONArray -> result.put(canonicalJsonArray(v))
                else -> result.put(v)
            }
        }
        return result
    }

    /** HMAC-SHA256 -> Hex */
    fun hmacSha256Hex(data: String, secret: String): String {
        val mac = Mac.getInstance(HMAC_SHA256)
        val key = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), HMAC_SHA256)
        mac.init(key)
        val bytes = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * 测试：按“获取 MQTT 连接信息”接口生成一整套签名数据
     * 只打印日志，不真正发请求
     */
    fun testSign(): String {
        // 用 SignConfig 里的配置（记得自己在 SignConfig 里改真实值）
        val appId = SignConfig.APP_ID
        val appSecret = SignConfig.APP_SECRET
        val deviceId = SignConfig.getDeviceId()

        val timestamp = generateTimestampSeconds()
        val nonce = generateNonce(32)

        // 业务 JSON（就是 data 字段里的“原始 JSON”）
        val bizJsonString = """
            {
              "deviceId": "$deviceId",
              "deviceName": "智慧终端A1",
              "appId": "X6AM8R3O675RBQEM",
              "version": "1.0",
              "publicIp": "112.45.90.12",
              "cpuUsage": "20%",
              "memoryUsage": "1.2GB/4GB",
              "storageUsage": "32GB/64GB",
              "remark": "测试设备 - 心跳正常"
            }
        """.trimIndent()

        // 规范化 JSON
        val canonicalData = canonicalJson(bizJsonString)

        // 拼接 signString（和拦截器一致）
        val signString = "appId=$appId" +
                "&appSecret=$appSecret" +
                "&data=$canonicalData" +
                "&deviceId=$deviceId" +
                "&nonce=$nonce" +
                "&timestamp=$timestamp"

        val sign = hmacSha256Hex(signString, appSecret)

        // 实际请求体 JSON（发给接口的就是这个结构）
        val requestJson = JSONObject().apply {
            put("appId", appId)
            put("deviceId", deviceId)
            put("timestamp", timestamp)
            put("nonce", nonce)
            put("data", canonicalData) // 字符串
            put("sign", sign)
        }.toString()

        android.util.Log.d("MqttSignTest", "bizJson       = $bizJsonString")
        android.util.Log.d("MqttSignTest", "canonicalData = $canonicalData")
        android.util.Log.d("MqttSignTest", "timestamp     = $timestamp")
        android.util.Log.d("MqttSignTest", "nonce         = $nonce")
        android.util.Log.d("MqttSignTest", "signString    = $signString")
        android.util.Log.d("MqttSignTest", "sign          = $sign")
        android.util.Log.d("MqttSignTest", "requestJson   = $requestJson")

        return sign
    }
}
