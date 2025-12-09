package com.example.storechat.data.api

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.and

/**
 * 与签名相关的工具方法
 */
object SignUtils {

    /**
     * 毫秒级时间戳（13 位）
     */
    fun generateTimestampMillis(): String = System.currentTimeMillis().toString()

    /**
     * 生成随机 nonce
     */
    fun generateNonce(length: Int = 32): String {
        val chars = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val random = SecureRandom()
        val sb = StringBuilder(length)
        repeat(length) {
            sb.append(chars[random.nextInt(chars.length)])
        }
        return sb.toString()
    }

    /**
     * 对 JSON 字符串做“规范化”：按 key 排序，递归处理嵌套结构。
     * 这样 Android 和后端都用同一种顺序，避免因为字段顺序不同导致签名不一致。
     */
//    fun canonicalJson(json: String): String {
//        if (json.isBlank()) return json
//
//        val trimmed = json.trim()
//        return when {
//            trimmed.startsWith("{") -> canonicalJsonObject(JSONObject(trimmed)).toString()
//            trimmed.startsWith("[") -> canonicalJsonArray(JSONArray(trimmed)).toString()
//            else -> json
//        }
//    }
//
//    private fun canonicalJsonObject(obj: JSONObject): JSONObject {
//        val keys = obj.keys().asSequence().toList().sorted()
//        val result = JSONObject()
//        for (key in keys) {
//            val value = obj.get(key)
//            when (value) {
//                is JSONObject -> result.put(key, canonicalJsonObject(value))
//                is JSONArray -> result.put(key, canonicalJsonArray(value))
//                else -> result.put(key, value)
//            }
//        }
//        return result
//    }
//
//    private fun canonicalJsonArray(array: JSONArray): JSONArray {
//        val result = JSONArray()
//        for (i in 0 until array.length()) {
//            val value = array.get(i)
//            when (value) {
//                is JSONObject -> result.put(canonicalJsonObject(value))
//                is JSONArray -> result.put(canonicalJsonArray(value))
//                else -> result.put(value)
//            }
//        }
//        return result
//    }
//
//    /**
//     * HMAC-SHA256，返回十六进制字符串
//     */
//    /**
//     * HMAC-SHA256，返回十六进制字符串
//     */
//    fun hmacSha256Hex(data: String, secret: String): String {
//        val mac = Mac.getInstance("HmacSHA256")
//        val secretKeySpec = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256")
//        mac.init(secretKeySpec)
//
//        val bytes = mac.doFinal(data.toByteArray(Charsets.UTF_8))
//        val sb = StringBuilder(bytes.size * 2)
//
//        for (b in bytes) {
//            val i = b.toInt() and 0xff  // 先转成 0~255 的 int
//            if (i < 0x10) {
//                sb.append('0')          // 补 0，保证两位
//            }
//            sb.append(i.toString(16))   // 转 16 进制字符串
//        }
//
//        return sb.toString()
//    }


    // 已经验证过正确的 HMAC-SHA256
    fun hmacSha256Hex(data: String, secret: String): String {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        val secretKeySpec = javax.crypto.spec.SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256")
        mac.init(secretKeySpec)

        val bytes = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(bytes.size * 2)

        for (b in bytes) {
            val i = b.toInt() and 0xff
            if (i < 0x10) sb.append('0')
            sb.append(i.toString(16))
        }
        return sb.toString()
    }
    /**
     * 动态版本的本地测试：跟拦截器逻辑保持完全一致
     */
    fun testSign(): String {
        val timestamp = generateTimestampMillis()
        val nonce = generateNonce()
        val deviceId = SignConfig.getDeviceId()

        // 注意字段顺序：按你刚才对签通过的顺序来写
        val dataString = "{\"deviceId\":\"$deviceId\"," +
                "\"deviceName\":\"智慧终端A1\"," +
                "\"appId\":\"X6AM8R3O675RBQEM\"," +
                "\"version\":\"1.0\"," +
                "\"publicIp\":\"112.45.90.12\"," +
                "\"cpuUsage\":\"20%\"," +
                "\"memoryUsage\":\"1.2GB/4GB\"," +
                "\"storageUsage\":\"32GB/64GB\"," +
                "\"remark\":\"测试设备 - 心跳正常\"}"

        val signString = "appId=${SignConfig.APP_ID}" +
                "&appSecret=${SignConfig.APP_SECRET}" +
                "&data=$dataString" +
                "&deviceId=$deviceId" +
                "&nonce=$nonce" +
                "&timestamp=$timestamp"

        val sign = hmacSha256Hex(signString, SignConfig.APP_SECRET)

        val requestJson = org.json.JSONObject().apply {
            put("appId", SignConfig.APP_ID)
            put("deviceId", deviceId)
            put("timestamp", timestamp)
            put("nonce", nonce)
            put("data", dataString)
            put("sign", sign)
        }.toString()

        android.util.Log.d("MqttSignTest", "dataString  = $dataString")
        android.util.Log.d("MqttSignTest", "timestamp   = $timestamp")
        android.util.Log.d("MqttSignTest", "nonce       = $nonce")
        android.util.Log.d("MqttSignTest", "signString  = $signString")
        android.util.Log.d("MqttSignTest", "sign(local) = $sign")
        android.util.Log.d("MqttSignTest", "requestJson = $requestJson")

        return requestJson
    }


    /**
     * 用于在本地测试“签名是否跟后端一致”的函数。
     * 运行 App 后，看 Logcat 中 tag=MqttSignTest 的日志。
     */










//    fun testSign(): String {
//        // 1. 先用后端给的固定 timestamp 和 nonce 来对签
//        val timestamp = "1765279775462"
//        val nonce = "9f2a8b1c-43e712d-3a5b-8c2d"
//        val deviceId = "DEV202501180001"   // 和示例一致
//
//        // 2. data 字符串，注意字段顺序和 remark 一定要和后台的一样（remark 为空字符串）
//        val dataString = "{\"deviceId\":\"DEV202501180001\"," +
//                "\"deviceName\":\"智慧终端A1\"," +
//                "\"appId\":\"X6AM8R3O675RBQEM\"," +
//                "\"version\":\"1.0\"," +
//                "\"publicIp\":\"112.45.90.12\"," +
//                "\"cpuUsage\":\"20%\"," +
//                "\"memoryUsage\":\"1.2GB/4GB\"," +
//                "\"storageUsage\":\"32GB/64GB\"," +
//                "\"remark\":\"\"}"
//
//        // 3. 按你们约定的规则拼接 signString（这里用你原来的那套，如果文档是别的顺序，就改成文档的）
//        val signString = "appId=${SignConfig.APP_ID}" +
//                "&appSecret=${SignConfig.APP_SECRET}" +
//                "&data=$dataString" +
//                "&deviceId=$deviceId" +
//                "&nonce=$nonce" +
//                "&timestamp=$timestamp"
//
//        val sign = hmacSha256Hex(signString, SignConfig.APP_SECRET)
//
//        val requestJson = JSONObject().apply {
//            put("appId", SignConfig.APP_ID)
//            put("deviceId", deviceId)
//            put("timestamp", timestamp)   // 和后台示例一样，用字符串
//            put("nonce", nonce)
//            put("data", dataString)
//            put("sign", sign)
//        }.toString()
//
//        Log.d("MqttSignTest", "BACKEND SAMPLE >>>")
//        Log.d("MqttSignTest", "dataString  = $dataString")
//        Log.d("MqttSignTest", "timestamp   = $timestamp")
//        Log.d("MqttSignTest", "nonce       = $nonce")
//        Log.d("MqttSignTest", "signString  = $signString")
//        Log.d("MqttSignTest", "sign(local) = $sign")
//        Log.d("MqttSignTest", "requestJson = $requestJson")
//
//        return requestJson
//    }


}
