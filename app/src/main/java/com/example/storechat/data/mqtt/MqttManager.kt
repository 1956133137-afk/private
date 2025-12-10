package com.example.storechat.data.mqtt

import android.content.Context
import android.util.Log
import com.example.storechat.data.api.MqttInfo
import com.example.storechat.data.model.CommandAck
import com.example.storechat.data.model.Heartbeat
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * MQTT 连接与订阅管理
 */
class MqttManager(context: Context) {

    private val appContext = context.applicationContext
    private var client: MqttAndroidClient? = null
    private var heartbeatScheduler: ScheduledExecutorService? = null

    /**
     * 根据后端返回的 MqttInfo 连接到 MQTT 服务器，并订阅 topic
     */
    fun connectAndSubscribe(
        config: MqttInfo,
        onMessage: (topic: String, payload: String) -> Unit
    ) {
        val serverUri = config.url         // 例如：tcp://test.yannuozhineng.com:1883
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
                Log.d("MQTT", "connectComplete reconnect=$reconnect uri=$serverURI")
                // 连接完成后订阅
                subscribeInternal(mqttClient, config.topic)
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                val payload = message?.toString() ?: ""
                Log.d("MQTT", "messageArrived topic=$topic payload=$payload")
                if (topic != null) {
                    onMessage(topic, payload)
                }
            }

            override fun connectionLost(cause: Throwable?) {
                Log.e("MQTT", "connectionLost", cause)
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                // 发布消息时的回调，这里可以先不处理
            }
        })

        mqttClient.connect(options, null, object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                Log.d("MQTT", "connect success")
            }

            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                Log.e("MQTT", "connect failed", exception)
            }
        })

        client = mqttClient
    }

    private fun subscribeInternal(mqttClient: MqttAndroidClient, topic: String) {
        try {
            mqttClient.subscribe(topic, 1, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d("MQTT", "subscribe success topic=$topic")
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e("MQTT", "subscribe failed topic=$topic", exception)
                }
            })
        } catch (e: MqttException) {
            Log.e("MQTT", "subscribe error", e)
        }
    }

    /**
     * 发布指令回执到指定的 Topic
     *
     * @param commandAck 指令回执数据对象
     */
    fun publishCommandAck(commandAck: CommandAck) {
        if (client?.isConnected != true) {
            Log.w("MQTT", "MQTT is not connected. Cannot publish command ack.")
            return
        }

        val ackTopic = "acmsIOTEMQXReceive/ack"
        val messagePayload = commandAck.toJson()
        val message = MqttMessage(messagePayload.toByteArray(Charsets.UTF_8)).apply {
            qos = 1 // 保证消息至少到达一次
            isRetained = false
        }

        try {
            client?.publish(ackTopic, message, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.i("MQTT", "Command ack published successfully to topic: $ackTopic")
                    Log.d("MQTT", "Command ack data: $messagePayload")
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e("MQTT", "Failed to publish command ack to topic: $ackTopic", exception)
                }
            })
        } catch (e: MqttException) {
            Log.e("MQTT", "Error while publishing command ack.", e)
        }
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

        // 1. 定义心跳 Topic (根据你的截图)
        val heartbeatTopic = "acmsIOTEMQXReceive/heartbeat"

        // 2. 将心跳数据转换为 JSON 字符串
        val messagePayload = heartbeat.toJson()

        // 3. 创建 MqttMessage
        val message = MqttMessage(messagePayload.toByteArray(Charsets.UTF_8)).apply {
            qos = 1 // 建议 QoS 至少为 1，确保消息到达
            isRetained = false // 心跳消息通常不需要保留
        }

        try {
            // 4. 发布消息
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
     *
     * @param deviceId 设备ID
     * @param intervalInSeconds 心跳间隔（秒），默认为 60
     */
    fun startHeartbeat(deviceId: String, intervalInSeconds: Long = 60) {
        if (heartbeatScheduler?.isShutdown == false) {
            Log.w("MQTT", "Heartbeat is already running.")
            return
        }
        heartbeatScheduler = Executors.newSingleThreadScheduledExecutor()
        val heartbeatTask = Runnable {
            val heartbeat = Heartbeat(
                deviceId = deviceId,
                publicIp = getPublicIp(),
                cpuUsage = getCpuUsage(),
                memoryUsage = getMemoryUsage(),
                storageUsage = getStorageUsage()
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
        // TODO: 实现获取公网 IP 的逻辑, 例如通过访问 http://api.ipify.org
        return "127.0.0.1" // 临时占位
    }

    private fun getCpuUsage(): String {
        // TODO: 实现获取 CPU 使用率的逻辑
        return "0.0" // 临时占位
    }

    private fun getMemoryUsage(): String {
        // TODO: 实现获取内存使用率的逻辑
        return "0.0" // 临时占位
    }

    private fun getStorageUsage(): String {
        // TODO: 实现获取存储使用率的逻辑
        return "0.0" // 临时占位
    }

    fun disconnect() {
        try {
            stopHeartbeat()
            client?.disconnect()
        } catch (_: Exception) {
        }
    }
}
