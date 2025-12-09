package com.example.storechat.data.api

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
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
        val originalRequest = chain.request()

        val body = originalRequest.body
        val contentType = body?.contentType()
        val isJson = contentType != null &&
                contentType.type == "application" &&
                contentType.subtype == "json"

        // 只处理 POST + JSON
        if (originalRequest.method != "POST" || !isJson || body == null) {
            return chain.proceed(originalRequest)
        }

        // 1. 读出原始业务 JSON（接口真正的 body）
        val buffer = okio.Buffer()
        body.writeTo(buffer)
        val dataString = buffer.readUtf8()   // 注意：直接作为 data

//        // ⚠️ 不再 canonicalJson，直接用原始字符串作为 data
//        val dataString = bizJsonString

        // 2. 生成签名相关字段
        val timestamp = SignUtils.generateTimestampMillis()
        val nonce = SignUtils.generateNonce()
        val deviceId = SignConfig.getDeviceId()

        // 3. 按照后端约定拼接 signString
        //    这里顺序要跟文档一致，如果后端文档是别的顺序，请按文档改
        val signString = "appId=${SignConfig.APP_ID}" +
                "&appSecret=${SignConfig.APP_SECRET}" +
                "&data=$dataString" +
                "&deviceId=$deviceId" +
                "&nonce=$nonce" +
                "&timestamp=$timestamp"

        val sign = SignUtils.hmacSha256Hex(signString, SignConfig.APP_SECRET)

        // 4. 包一层真正发送给后端的 JSON
        val requestJson = JSONObject().apply {
            put("appId", SignConfig.APP_ID)
            put("deviceId", deviceId)
            put("timestamp", timestamp)
            put("nonce", nonce)
            put("data", dataString)
            put("sign", sign)
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val newBody = requestJson.toString().toRequestBody(mediaType)

        // 5. 自动加上 Device-Traced-Id 头
        val traceId = UUID.randomUUID().toString()
        val newRequest = originalRequest.newBuilder()
            .header("Device-Traced-Id", traceId)
            .post(newBody)
            .build()

        return chain.proceed(newRequest)
    }
}

