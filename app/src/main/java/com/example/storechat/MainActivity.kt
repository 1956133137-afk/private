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
import com.example.storechat.ui.home.HomeFragment
import com.example.storechat.xc.XcServiceManager
import kotlinx.coroutines.launch
import me.jessyan.autosize.internal.CustomAdapt
import java.lang.Exception

class MainActivity : AppCompatActivity(), CustomAdapt {  //  实现 CustomAdapt

    private var drawerLayout: DrawerLayout? = null
    private lateinit var mqttManager: MqttManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mqttManager = MqttManager(this)

        // 初始化 MQTT 并启动心跳
        initMqttAndStartHeartbeat()

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

    private fun initMqttAndStartHeartbeat() {
        lifecycleScope.launch {
            try {
                val deviceId = SignConfig.getDeviceId()

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
                    // 连接到 MQTT 服务器
                    mqttManager.connect(mqttInfo) {
                        // 连接成功后，启动心跳
                        if (bizBody.appId != null && bizBody.deviceName != null && bizBody.version != null) {
                            mqttManager.startHeartbeat(
                                appId = bizBody.appId,
                                deviceName = bizBody.deviceName,
                                version = bizBody.version,
                                deviceId = bizBody.deviceId
                            )
                        } else {
                            Log.e("MQTT-API", "Cannot start heartbeat, required info is missing.")
                        }
                    }
                } else {
                    Log.e("MQTT-API", "获取 MQTT 信息失败 code=${resp.code} msg=${resp.msg}")
                }
            } catch (e: Exception) {
                Log.e("MQTT-API", "请求异常", e)
            }
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
