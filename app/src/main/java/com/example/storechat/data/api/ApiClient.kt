package com.example.storechat.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Retrofit 客户端单例
 * 之后只需要在这里把 BASE_URL 改成你后台地址即可
 * 
 * 该对象负责创建和配置Retrofit实例，包括HTTP客户端、日志拦截器和签名拦截器
 */
object ApiClient {

    // 换成测试环境域名，注意以 / 结尾
    /**
     * API基础URL地址
     * 所有API端点都将基于此URL构建
     */
    private const val BASE_URL = "https://test.yannuozhineng.com/acms/api/"

    /**
     * 日志拦截器懒加载属性
     * 用于记录HTTP请求和响应的详细信息
     */
    private val loggingInterceptor: HttpLoggingInterceptor by lazy {
        HttpLoggingInterceptor().apply {
            // 调试阶段打印请求/响应日志，方便排查问题
            level = HttpLoggingInterceptor.Level.BODY
        }
    }

    /**
     * OkHttp客户端懒加载属性
     * 配置了签名拦截器和日志拦截器
     */
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(SignInterceptor())
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            // 添加SSL绕过配置以解决证书问题
            .sslSocketFactory(UnsafeClient.sslSocketFactory, UnsafeClient.trustManager)
            .hostnameVerifier { _, _ -> true } // 绕过主机名验证
            .build()
    }

    /**
     * AppApiService实例懒加载属性
     * 提供对所有API端点的访问
     */
    val appApi: AppApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AppApiService::class.java)
    }
}