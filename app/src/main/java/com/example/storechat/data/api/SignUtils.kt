package com.example.storechat.data.api

import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec


object SignUtils {


    fun generateTimestampMillis(): String = System.currentTimeMillis().toString()


    fun generateNonce(length: Int = 32): String {
        val chars = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val random = SecureRandom()
        val sb = StringBuilder(length)
        repeat(length) {
            sb.append(chars[random.nextInt(chars.length)])
        }
        return sb.toString()
    }

    // 已经验证过正确的 HMAC-SHA256
    fun hmacSha256Hex(data: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        val secretKeySpec = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256")
        mac.init(secretKeySpec)

        val bytes = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(bytes.size * 2)

        for (b in bytes) {
            val i = b.toInt() and 0xff
            if (i < 0x10) sb.append('0')
            sb.append(i.toString(16))
        }
        return sb.toString()
    }
}
