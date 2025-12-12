package com.example.storechat.xc

import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import android.util.Log
import android.widget.Toast
// ★★★ 关键导包步骤 ★★★
// 1. 如果下面这行报红，请删掉它。
// 2. 然后在代码中找到红色的 "MyService"，按 Alt+Enter (或 Option+Enter)，
// 3. 选择 "Import class"，系统会自动导入 jar 包中的正确路径 (通常是 com.proembed.service 或 android_serialport_api 等)。
import com.proembed.service.MyService

import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream

object XcServiceManager {
    private const val TAG = "XcServiceManager"
    private var service: MyService? = null
    private lateinit var appContext: Context
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun init(context: Context) {
        appContext = context.applicationContext
        scope.launch {
            try {
                if (service == null) {
                    // 如果这里报错，请确保删除了本地的 MyService.kt 并正确导入了 jar 包中的类
                    service = MyService(appContext)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Service init error", e)
            }
        }
    }

    /**
     * 下载 -> 解析包名 -> 静默安装
     * @return String? 安装成功返回真实的包名(packageName)，失败返回 null
     */
    suspend fun downloadAndInstall(
        url: String,
        onProgress: ((Int) -> Unit)?
    ): String? = withContext(Dispatchers.IO) {
        if (service == null) {
            withContext(Dispatchers.Main) {
                Toast.makeText(appContext, "硬件服务未连接", Toast.LENGTH_SHORT).show()
            }
            return@withContext null
        }

        try {
            // 1. 准备路径
            val tempName = "update_${System.currentTimeMillis()}.apk"
            val dir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: appContext.filesDir
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, tempName)
            if (file.exists()) file.delete()

            // 2. 下载文件
            val client = OkHttpClient()
            val req = Request.Builder().url(url).build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful || resp.body == null) return@withContext null

            val body = resp.body!!
            val total = body.contentLength()
            val input = body.byteStream()
            val output = BufferedOutputStream(FileOutputStream(file))
            val buffer = ByteArray(8192)
            var len: Int
            var current = 0L

            while (input.read(buffer).also { len = it } != -1) {
                if (!isActive) { output.close(); return@withContext null }
                output.write(buffer, 0, len)
                current += len
                if (total > 0) onProgress?.invoke(((current * 100) / total).toInt())
            }
            output.flush()
            output.close()
            resp.close()

            // 3. ★★★ 自动解析 APK 获取真实包名 ★★★
            // 这就是不需要您手动填 ID 映射的关键
            val pm = appContext.packageManager
            val info = pm.getPackageArchiveInfo(file.absolutePath, 0)
            val realPackageName = info?.packageName

            if (realPackageName.isNullOrBlank()) {
                Log.e(TAG, "APK 解析失败，无法获取包名")
                return@withContext null
            }

            Log.d(TAG, "解析成功，真实包名: $realPackageName，开始安装...")

            // 4. 调用 Jar 包的静默安装
            // 这里的参数顺序参考了您的文档：path, packageName, isOpen
            service?.silentInstallApk(file.absolutePath, realPackageName, true)

            // 5. 返回包名给 Repository 记录
            return@withContext realPackageName

        } catch (e: Exception) {
            Log.e(TAG, "下载安装流程异常", e)
            return@withContext null
        }
    }
}