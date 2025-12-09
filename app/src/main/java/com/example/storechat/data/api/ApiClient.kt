package com.example.storechat.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Retrofit 客户端单例
 * 之后只需要在这里把 BASE_URL 改成你后台地址即可
 */
object ApiClient {

    // 换成测试环境域名，注意以 / 结尾
    private const val BASE_URL = "https://test.yannuozhineng.com/acms/api/"

    private val loggingInterceptor: HttpLoggingInterceptor by lazy {
        HttpLoggingInterceptor().apply {
            // 调试阶段打印请求/响应日志，方便排查问题
            level = HttpLoggingInterceptor.Level.BODY
        }
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            // 先加签，再打印日志，这样日志里能看到最终发送给服务器的完整 JSON
            .addInterceptor(SignInterceptor())
            .addInterceptor(loggingInterceptor)
            .build()
    }

    val appApi: AppApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AppApiService::class.java)
    }
}
