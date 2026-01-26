package com.example.storechat.data.api

import android.util.Log
import com.google.gson.GsonBuilder
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private const val TAG = "ApiClient"
    private const val BASE_URL = "https://acms.yannuozhineng.com/api/"

    private val loggingInterceptor: HttpLoggingInterceptor by lazy {
        HttpLoggingInterceptor(object : HttpLoggingInterceptor.Logger {
            override fun log(message: String) {
                Log.i(TAG, message)
                // 同时写入文件日志
                com.example.storechat.util.LogUtil.i(TAG, message)
            }
        }).apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
    }

    private val okHttpClient: OkHttpClient by lazy {

        val dispatcher = Dispatcher().apply {
            maxRequests = 32
            maxRequestsPerHost = 8
        }

        OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            .retryOnConnectionFailure(true)

            .addInterceptor(SignInterceptor())
            .addInterceptor(loggingInterceptor)

            //  总超时（防止某些请求挂住很久）
            .callTimeout(20, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)

            .build()
    }

    private val gson = GsonBuilder().create()

    val appApi: AppApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(AppApiService::class.java)
    }
}
