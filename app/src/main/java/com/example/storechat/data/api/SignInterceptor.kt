package com.example.storechat.data.api

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer
import org.json.JSONObject
import java.io.IOException
import java.util.UUID

/**
 * 自动对 POST JSON 请求进行封装 + 加签
 */
class SignInterceptor : Interceptor {

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()

        // 只处理 POST + JSON 请求
        val body = request.body
        if (request.method != "POST" || body == null || body.contentType()?.subtype != "json") {
            return chain.proceed(request)
        }

        // 1. 读取原始请求体内容
        val buffer = Buffer()
        body.writeTo(buffer)
        val dataString = buffer.readUtf8()

        // 2. 准备签名所需参数
        val timestamp = SignUtils.generateTimestampMillis()
        val nonce = SignUtils.generateNonce()
        val deviceId = SignConfig.getDeviceId()

        // 3. 严格按照服务器要求的固定顺序拼接参数，并将 appSecret 包含在内
        val signString = "appId=${SignConfig.APP_ID}" +
                "&appSecret=${SignConfig.APP_SECRET}" +
                "&data=$dataString" +
                "&deviceId=$deviceId" +
                "&nonce=$nonce" +
                "&timestamp=$timestamp"

        // 4. 使用 appSecret 作为密钥进行 HmacSHA256 加密
        val sign = SignUtils.hmacSha256Hex(signString, SignConfig.APP_SECRET)

        // 5. 构建包含签名的新请求体 JSON
        val newJsonBody = JSONObject().apply {
            put("appId", SignConfig.APP_ID)
            put("deviceId", deviceId)
            put("timestamp", timestamp)
            put("nonce", nonce)
            put("data", dataString)
            put("sign", sign)
        }

        // 6. 创建新的 RequestBody
        val newRequestBody = newJsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        // 7. 用新的请求体和 Header 构建最终请求
        request = request.newBuilder()
            .header("Device-Traced-Id", UUID.randomUUID().toString())
            .post(newRequestBody)
            .build()

        return chain.proceed(request)
    }
}
