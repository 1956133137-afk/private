package com.example.storechat.data.api

import android.util.Log
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer
import org.json.JSONObject
import java.io.IOException
import java.util.UUID


class SignInterceptor : Interceptor {

    private val TAG = "SignInterceptor"

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()


        val body = request.body
        if (request.method != "POST" || body == null || body.contentType()?.subtype != "json") {
            return chain.proceed(request)
        }


        val buffer = Buffer()
        body.writeTo(buffer)
        val dataString = buffer.readUtf8()
        
        Log.d(TAG, "Original request URL: ${request.url}")
        Log.d(TAG, "Original request body: $dataString")


        val timestamp = SignUtils.generateTimestampMillis()
        val nonce = SignUtils.generateNonce()
        val deviceId = SignConfig.getDeviceId()


        val signString = "appId=${SignConfig.APP_ID}" +
                "&appSecret=${SignConfig.APP_SECRET}" +
                "&data=$dataString" +
                "&deviceId=$deviceId" +
                "&nonce=$nonce" +
                "&timestamp=$timestamp"


        val sign = SignUtils.hmacSha256Hex(signString, SignConfig.APP_SECRET)


        val newJsonBody = JSONObject().apply {
            put("appId", SignConfig.APP_ID)
            put("deviceId", deviceId)
            put("timestamp", timestamp)
            put("nonce", nonce)
            put("data", dataString)
            put("sign", sign)
        }

        Log.d(TAG, "Signed request body: ${newJsonBody.toString()}")


        val newRequestBody = newJsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())


        request = request.newBuilder()
            .header("Device-Traced-Id", UUID.randomUUID().toString())
            .post(newRequestBody)
            .build()

        val response = chain.proceed(request)
        Log.d(TAG, "Response code: ${response.code}, message: ${response.message}")
        
        return response
    }
}