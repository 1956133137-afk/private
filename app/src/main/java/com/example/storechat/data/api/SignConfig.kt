package com.example.storechat.data.api

object SignConfig {

    // 用你提供的正式测试账号
    const val APP_ID = "U5PPF18NU6XTBI1F"
    const val APP_SECRET = "FnqAu70dJ5FzTeHQYLkFL8Nu2EqKQtMIUMVicJ3r0kajIcV8Bg"

    /**
     * 设备唯一标识：
     * 现在先写死一个，方便你和后台查数据库、
     * 后面真上生产再改成 AndroidID / SN / UUID 等。
     */
    fun getDeviceId(): String {
        // 随便一个有格式的，保证唯一即可
        return "DEV202501180001"
    }
}
