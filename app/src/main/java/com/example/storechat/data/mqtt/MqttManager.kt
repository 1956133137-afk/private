package com.example.storechat.data.mqtt

import android.content.Context
import android.util.Log
import com.example.storechat.data.api.MqttInfo
import com.example.storechat.data.model.Heartbeat
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * MQTT 管理器：仅用于连接和发送心跳
 */
class MqttManager(context: Context) {

    private val appContext = context.applicationContext
    private var client: MqttAndroidClient? = null
    private var heartbeatScheduler: ScheduledExecutorService? = null

    /**
     * 根据后端返回的 MqttInfo 连接到 MQTT 服务器
     */
    fun connect(
        config: MqttInfo,
        onConnectComplete: () -> Unit
    ) {
        val serverUri = config.url
        val clientId = "android-${System.currentTimeMillis()}"

        val mqttClient = MqttAndroidClient(appContext, serverUri, clientId)

        val options = MqttConnectOptions().apply {
            isCleanSession = true
            userName = config.username
            password = config.password.toCharArray()
            connectionTimeout = 10
            keepAliveInterval = 20
        }

        mqttClient.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                Log.d("MQTT", "Connect complete. Reconnect: $reconnect")
                // 连接成功后，调用回调
                onConnectComplete()
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                // 不需要处理接收消息的逻辑
            }

            override fun connectionLost(cause: Throwable?) {
                Log.e("MQTT", "Connection lost", cause)
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                // 心跳消息发送成功的回调
            }
        })

        try {
            mqttClient.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d("MQTT", "Connect success")
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e("MQTT", "Connect failed", exception)
                }
            })
        } catch (e: MqttException) {
            Log.e("MQTT", "Connect error", e)
        }

        client = mqttClient
    }

    /**
     * 发布心跳消息到指定的 Topic
     * @param heartbeat 心跳数据对象
     */
    fun publishHeartbeat(heartbeat: Heartbeat) {
        if (client?.isConnected != true) {
            Log.w("MQTT", "MQTT is not connected. Cannot publish heartbeat.")
            return
        }

        val heartbeatTopic = "acmsIOTEMQXReceive/heartbeat"
        val messagePayload = heartbeat.toJson()

        val message = MqttMessage(messagePayload.toByteArray(Charsets.UTF_8)).apply {
            qos = 1 // 保证消息至少到达一次
            isRetained = false
        }

        try {
            client?.publish(heartbeatTopic, message, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.i("MQTT", "Heartbeat published successfully to topic: $heartbeatTopic")
                    Log.d("MQTT", "Heartbeat data: $messagePayload")
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e("MQTT", "Failed to publish heartbeat to topic: $heartbeatTopic", exception)
                }
            })
        } catch (e: MqttException) {
            Log.e("MQTT", "Error while publishing heartbeat.", e)
        }
    }

    /**
     * 启动周期性心跳
     */
    fun startHeartbeat(appId: String, deviceName: String, version: String, deviceId: String, intervalInSeconds: Long = 60) {
        if (heartbeatScheduler?.isShutdown == false) {
            Log.w("MQTT", "Heartbeat is already running.")
            return
        }
        heartbeatScheduler = Executors.newSingleThreadScheduledExecutor()
        val heartbeatTask = Runnable {
            val heartbeat = Heartbeat(
                appId = appId,
                deviceName = deviceName,
                version = version,
                deviceId = deviceId,
                publicIp = getPublicIp(),
                cpuUsage = getCpuUsage(),
                memoryUsage = getMemoryUsage(),
                storageUsage = getStorageUsage(),
                remark = "",
                timestamp = System.currentTimeMillis()
            )
            publishHeartbeat(heartbeat)
        }
        heartbeatScheduler?.scheduleAtFixedRate(
            heartbeatTask,
            0,
            intervalInSeconds,
            TimeUnit.SECONDS
        )
        Log.i("MQTT", "Heartbeat started. Interval: $intervalInSeconds seconds.")
    }

    /**
     * 停止心跳
     */
    fun stopHeartbeat() {
        heartbeatScheduler?.shutdownNow()
        heartbeatScheduler = null
        Log.i("MQTT", "Heartbeat stopped.")
    }

    private fun getPublicIp(): String {
        // TODO: 实现获取公网 IP 的逻辑
        return "127.0.0.1"
    }

    private fun getCpuUsage(): String {
        // TODO: 实现获取 CPU 使用率的逻辑
        return "0.0"
    }

    private fun getMemoryUsage(): String {
        // TODO: 实现获取内存使用率的逻辑
        return "0.0"
    }

    private fun getStorageUsage(): String {
        // TODO: 实现获取存储使用率的逻辑
        return "0.0"
    }

    fun disconnect() {
        try {
            stopHeartbeat()
            client?.disconnect()
        } catch (e: MqttException) {
            Log.e("MQTT", "Disconnect error", e)
        }
    }
}
