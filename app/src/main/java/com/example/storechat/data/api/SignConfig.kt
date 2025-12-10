package com.example.storechat.data.api

/**
 * 签名配置对象
 * 包含应用认证信息和设备标识配置
 */
object SignConfig {

    // 用你提供的正式测试账号
    /**
     * 应用ID
     * 用于API身份验证的唯一标识符
     */
    const val APP_ID = "32DQY9LH260HX43U"
    
    /**
     * 应用密钥
     * 用于API请求签名的密钥
     */
    const val APP_SECRET = "Ask1shvYpchfi0LH4O5Hj2RfBK7VsRDOKVKri5BY8JCx60qWDC"

    /**
     * 设备唯一标识：
     * 现在先写死一个，方便你和后台查数据库、
     * 后面真上生产再改成 AndroidID / SN / UUID 等。
     * 
     * @return 设备唯一标识字符串
     */
    fun getDeviceId(): String {
        return "DEV202501180001"
    }
}