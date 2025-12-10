package com.example.storechat

import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.example.storechat.data.api.ApiClient
import com.example.storechat.data.api.MqttInitBizBody
import com.example.storechat.data.api.SignConfig
import com.example.storechat.data.mqtt.MqttManager
import com.example.storechat.data.model.CommandAck
import com.example.storechat.ui.home.HomeFragment
import com.example.storechat.xc.XcServiceManager
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.launch
import me.jessyan.autosize.internal.CustomAdapt
import java.lang.Exception

class MainActivity : AppCompatActivity(), CustomAdapt {  //  实现 CustomAdapt

    private var drawerLayout: DrawerLayout? = null
    private lateinit var mqttManager: MqttManager
    private lateinit var deviceId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mqttManager = MqttManager(this)

        // 调用"获取 MQTT 连接信息"接口（加签逻辑在 SignInterceptor 里自动完成）
        lifecycleScope.launch {
            try {
                deviceId = SignConfig.getDeviceId()

                val bizBody = MqttInitBizBody(
                    deviceId = deviceId,
                    deviceName = "智慧终端A1",
                    appId = "X6AM8R3O675RBQEM",
                    version = "1.0",
                    publicIp = "112.45.90.12",
                    cpuUsage = "20%",
                    memoryUsage = "1.2GB/4GB",
                    storageUsage = "32GB/64GB",
                    remark = "测试设备 - 心跳正常"
                )

                val resp = ApiClient.appApi.getMqttInfo(bizBody)
                Log.d("MQTT-API", "code=${resp.code}, msg=${resp.msg}, data=${resp.data}")

                val mqttInfo = resp.data
                if (resp.code == 200 && mqttInfo != null) {
                    // 拿到 MQTT 信息后连接并订阅 topic
                    mqttManager.connectAndSubscribe(mqttInfo) { topic, payload ->
                        Log.d("MQTT-MSG", "topic=$topic payload=$payload")
                        // 处理接收到的指令并发送回执
                        handleCommandAndSendAck(topic, payload)
                    }
                } else {
                    Log.e("MQTT-API", "获取 MQTT 信息失败 code=${resp.code} msg=${resp.msg}")
                }
            } catch (e: Exception) {
                Log.e("MQTT-API", "请求异常", e)
            }
        }

        // Initialize XC Service Manager
        XcServiceManager.init(this)

        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            drawerLayout = findViewById(R.id.drawerLayout)
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.mainContainer, HomeFragment())
                .commit()
        }
    }

    /**
     * 处理接收到的指令并发送回执
     */
    private fun handleCommandAndSendAck(topic: String, payload: String) {
        try {
            // 解析收到的消息
            val jsonObject = JsonParser().parse(payload) as JsonObject
            val msgIdElement = jsonObject.get("msgId")
            val msgId = if (msgIdElement != null && !msgIdElement.isJsonNull) msgIdElement.asString else ""
            
            // 处理指令
            val success = processCommand(jsonObject)
            
            // 发送回执
            sendCommandAck(msgId, if (success) CommandAck.STATUS_SUCCESS else CommandAck.STATUS_FAILED, 
                          if (success) "" else "指令处理失败")
        } catch (e: Exception) {
            Log.e("COMMAND", "处理指令时发生错误", e)
            // 发送错误回执
            sendCommandAck("", CommandAck.STATUS_FAILED, "解析指令失败: ${e.message}")
        }
    }

    /**
     * 发送指令回执
     */
    private fun sendCommandAck(msgId: String, status: String, errorMsg: String) {
        val commandAck = CommandAck(
            msgId = msgId,
            deviceId = deviceId,
            status = status,
            errorMsg = errorMsg
        )
        mqttManager.publishCommandAck(commandAck)
        Log.d("COMMAND", "已发送指令回执: msgId=$msgId, status=$status")
    }

    /**
     * 实际处理指令的方法，根据您的业务需求实现
     */
    private fun processCommand(command: JsonObject): Boolean {
        try {
            // 获取指令类型
            val typeElement = command.get("type")
            val commandType = if (typeElement != null && !typeElement.isJsonNull) typeElement.asString else ""
            Log.d("COMMAND", "收到指令类型: $commandType")
            
            when (commandType) {
                "INSTALL_APP" -> {
                    // 处理安装应用指令
                    val data = command.getAsJsonObject("data")
                    val apkPathElement = data.get("apkPath")
                    val packageNameElement = data.get("packageName")
                    val apkPath = if (apkPathElement != null && !apkPathElement.isJsonNull) apkPathElement.asString else ""
                    val packageName = if (packageNameElement != null && !packageNameElement.isJsonNull) packageNameElement.asString else ""
                    
                    if (apkPath.isNotEmpty() && packageName.isNotEmpty()) {
                        // 调用XC服务管理器安装应用
                        XcServiceManager.installApk(apkPath, packageName, false)
                        Log.d("COMMAND", "开始安装应用: $packageName")
                        return true
                    } else {
                        Log.e("COMMAND", "安装应用指令缺少必要参数")
                        return false
                    }
                }
                "UNINSTALL_APP" -> {
                    // 处理卸载应用指令
                    val data = command.getAsJsonObject("data")
                    val packageNameElement = data.get("packageName")
                    val packageName = if (packageNameElement != null && !packageNameElement.isJsonNull) packageNameElement.asString else ""
                    
                    if (packageName.isNotEmpty()) {
                        // 调用XC服务管理器卸载应用
                        XcServiceManager.uninstallApk(packageName)
                        Log.d("COMMAND", "开始卸载应用: $packageName")
                        return true
                    } else {
                        Log.e("COMMAND", "卸载应用指令缺少必要参数")
                        return false
                    }
                }
                "UPDATE_CONFIG" -> {
                    // 处理配置更新指令
                    Log.d("COMMAND", "处理配置更新指令")
                    // 这里添加配置更新的具体实现
                    return true
                }
                "RESTART_DEVICE" -> {
                    // 处理重启设备指令
                    Log.d("COMMAND", "处理重启设备指令")
                    // 这里添加重启设备的具体实现
                    return true
                }
                else -> {
                    Log.w("COMMAND", "未知指令类型: $commandType")
                    return false
                }
            }
        } catch (e: Exception) {
            Log.e("COMMAND", "处理指令时发生异常", e)
            return false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mqttManager.disconnect()
    }

    fun openDrawer() {
        drawerLayout?.openDrawer(GravityCompat.END)
    }

    // 竖屏：按宽度 411 适配；横屏：按高度 731 适配
    override fun isBaseOnWidth(): Boolean {
        return resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    }

    override fun getSizeInDp(): Float {
        return if (isBaseOnWidth()) {
            411f   // 竖屏：设计稿宽度
        } else {
            500f   // 横屏：用竖屏的"高度"当基准，保证纵向比例正常
        }
    }
}
