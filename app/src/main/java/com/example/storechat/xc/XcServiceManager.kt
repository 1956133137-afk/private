package com.example.storechat.xc

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import com.proembed.service.MyService
import com.example.storechat.util.LogUtil
import com.ys.rkapi.MyManager
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream

/**
 * XcServiceManager - 静默安装管理器
 *
 * 支持三种安装方法：
 * 1. 优先使用匹配主板类型的静默安装服务
 * 2. 如果失败，尝试另一种主板类型的静默安装服务
 * 3. 如果依然失败，使用标准安装（Intent）
 */
object XcServiceManager {
    private const val TAG = "XcServiceManager"
    private var mXCService: MyService? = null  // 向成主板服务
    private var mXHService: MyManager? = null  // 芯伙主板服务
    private var boardType = 0 // 0: uninitialized, 1: XC, 2: XH
    private lateinit var appContext: Context
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var isInitialized = false
    private val initLock = Object()

    /**
     * 初始化服务管理器
     * 尝试初始化所有可用的硬件服务
     */
    fun init(context: Context) {
        synchronized(initLock) {
            if (isInitialized) return
            appContext = context.applicationContext
            try {
                // 尝试初始化向成服务
                try {
                    mXCService = MyService(appContext)
                    LogUtil.i(TAG, "XC service (MyService) initialized")
                } catch (e: Throwable) {
                    LogUtil.w(TAG, "XC service init failed: ${e.message}")
                }

                // 尝试初始化芯伙服务
                try {
                    mXHService = MyManager.getInstance(appContext)
                    LogUtil.i(TAG, "XH service (MyManager) initialized")
                } catch (e: Throwable) {
                    LogUtil.w(TAG, "XH service init failed: ${e.message}")
                }

                // 根据设备型号设置首选主板类型
                boardType = when (Build.MODEL.trim()) {
                    "rk3576_u", "QUAD-CORE A133 b7" -> 2
                    else -> 1
                }
                
                isInitialized = true
                LogUtil.i(TAG, "Service manager initialized. Preferred boardType: $boardType")
            } catch (e: Exception) {
                LogUtil.e(TAG, "Hardware service init error.", e)
                isInitialized = false
            }
        }
    }

    private fun isAppInstalled(pm: PackageManager, packageName: String): Boolean {
        return try {
            pm.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun getInstalledVersionCode(pm: PackageManager, packageName: String): Int {
        return try {
            val pInfo = pm.getPackageInfo(packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode
            }
        } catch (e: PackageManager.NameNotFoundException) {
            -1
        }
    }

    /**
     * 下载并安装APK，采用三级回退机制
     */
    suspend fun downloadAndInstall(
        appId: String,
        versionId: Long,
        newVersionCode: Int, // 这个是服务器返回的版本号，可能不准
        url: String,
        onProgress: ((progress: Int, currentBytes: Long, totalBytes: Long) -> Unit)?
    ): String? = withContext(Dispatchers.IO) {
        waitForInitialization()

        try {
            // 1. 下载APK文件
            val file = downloadApkWithResume(appId, versionId, url, onProgress) ?: return@withContext null

            val pm = appContext.packageManager
            val info = pm.getPackageArchiveInfo(file.absolutePath, 0)
            val realPackageName = info?.packageName

            if (realPackageName.isNullOrBlank()) {
                file.delete()
                return@withContext null
            }

            // 【核心修复】直接从下载的 APK 文件中读取真实版本号
            // 如果 APK 本身是 57，我们就以 57 为目标，而不是死等服务器说的 58
            val realVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                info.versionCode
            }

            LogUtil.i(TAG, "Starting installation for $realPackageName. Server ver: $newVersionCode, APK real ver: $realVersionCode")

            var isInstallSuccessful = false

            // 方法 1: 使用首选静默安装服务
            // 注意：这里传入 realVersionCode 而不是 newVersionCode
            LogUtil.i(TAG, "第一种方式: Preferred silent install (Type $boardType)")
            isInstallSuccessful = trySilentInstall(pm, file, realPackageName, realVersionCode, boardType)

            // 方法 2: 如果首选失败...
            if (!isInstallSuccessful) {
                val altType = if (boardType == 1) 2 else 1
                LogUtil.i(TAG, "Method 1 failed, 第二种方式: Alternative silent install (Type $altType)")
                isInstallSuccessful = trySilentInstall(pm, file, realPackageName, realVersionCode, altType)
            }

            // ... (后续逻辑保持不变)

            // 方法 3: 如果静默安装都失败，尝试标准安装方法
            if (!isInstallSuccessful) {
                LogUtil.i(TAG, "Silent installation failed, 第三种方式: Standard installation")
                isInstallSuccessful = tryStandardInstall(file, realPackageName)
            }

            if (!isInstallSuccessful) return@withContext null
            
            LogUtil.i(TAG, "Installation flow completed for: $realPackageName")
            return@withContext realPackageName

        } catch (e: Exception) {
            LogUtil.e(TAG, "Unexpected error during download and install", e)
            if (e is CancellationException) throw e
            return@withContext null
        }
    }

    /**
     * 尝试静默安装
     */
    private suspend fun trySilentInstall(
        pm: PackageManager,
        file: File,
        realPackageName: String,
        newVersionCode: Int,
        type: Int
    ): Boolean {
        if (!isInitialized) return false
        
        try {
            // 检查对应服务是否可用
            if (type == 1 && mXCService == null) return false
            if (type == 2 && mXHService == null) return false

            // 1. 如果目标应用已安装，先静默卸载
            if (isAppInstalled(pm, realPackageName)) {
                LogUtil.i(TAG, "Package $realPackageName exists, attempting silent uninstall via Type $type")
                when (type) {
                    1 -> mXCService?.silentUnInstallApk(realPackageName)
                    2 -> mXHService?.selfStart(realPackageName) // 保持原有逻辑
                }
                
                // 等待卸载完成 (最多5秒)
                for (i in 0..4) {
                    delay(1000)
                    if (!isAppInstalled(pm, realPackageName)) {
                        LogUtil.i(TAG, "Uninstall successful")
                        break
                    }
                }
            }

            // 2. 执行静默安装
            if (!isAppInstalled(pm, realPackageName)) {
                LogUtil.i(TAG, "Executing silent install via Type $type")
                when (type) {
                    1 -> mXCService?.silentInstallApk(file.absolutePath, realPackageName, false)
                    2 -> mXHService?.silentInstallApk(file.absolutePath, false)
                }

                // 将等待时间从 60秒 (0..59) 增加到 100秒 (0..99)
                for (i in 0..99) {
                    delay(1000)
                    val installedVersion = getInstalledVersionCode(pm, realPackageName)

                    // 增加日志方便调试，看看到底读到了什么版本
                    if (i % 5 == 0) { // 每5秒打印一次
                        LogUtil.d(TAG, "Checking install status ($i/100): current=$installedVersion, target=$newVersionCode")
                    }

                    if (installedVersion >= newVersionCode) {
                        LogUtil.i(TAG, "Silent install 成功 via 方式 $type")
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error in trySilentInstall (Type $type): ${e.message}")
            if (e is CancellationException) throw e
        }
        return false
    }


    /**
     * 尝试使用标准安装方法（复制到公共目录 + 绕过校验）
     */
    private suspend fun tryStandardInstall(apkFile: File, packageName: String): Boolean = withContext(Dispatchers.IO) {
        // 定义一个临时文件在公共下载目录，这是系统安装器绝对有权限访问的地方
        val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!publicDir.exists()) publicDir.mkdirs()

        // 为了防止文件名冲突导致解析错误，使用固定名字或时间戳
        val targetFile = File(publicDir, "update_temp_${System.currentTimeMillis()}.apk")

        try {
            LogUtil.i(TAG, "Copying APK to public directory: ${targetFile.absolutePath}")

            // 1. 将文件复制到公共目录 (解决私有目录权限问题)
            apkFile.copyTo(targetFile, overwrite = true)

            // 2. 暴力赋予 777 权限 (解决 Linux 文件权限问题)
            try {
                val p = Runtime.getRuntime().exec("chmod 777 ${targetFile.absolutePath}")
                p.waitFor()
            } catch (e: Exception) {
                LogUtil.w(TAG, "Chmod failed: ${e.message}")
            }

            // 3. 禁用 Android 7.0+ 的 FileUriExposed 检查 (强制允许 file:// 协议)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    val builder = android.os.StrictMode.VmPolicy.Builder()
                    android.os.StrictMode.setVmPolicy(builder.build())
                } catch (e: Exception) {
                    LogUtil.e(TAG, "Disable StrictMode failed: ${e.message}")
                }
            }

            // 4. 启动安装
            withContext(Dispatchers.Main) {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                // 直接使用文件路径 URI
                val uri = Uri.fromFile(targetFile)
                LogUtil.i(TAG, "Starting installation with public file: $uri")

                intent.setDataAndType(uri, "application/vnd.android.package-archive")
                appContext.startActivity(intent)
            }
            true
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to copy/install APK: ${e.message}")
            e.printStackTrace()
            // 如果复制失败，尝试直接用源文件兜底（虽然很大概率还是不行）
            withContext(Dispatchers.Main) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    intent.setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive")
                    appContext.startActivity(intent)
                } catch (ex: Exception) {}
            }
            false
        }
    }

    private suspend fun downloadApkWithResume(
        appId: String,
        versionId: Long,
        url: String,
        onProgress: ((progress: Int, currentBytes: Long, totalBytes: Long) -> Unit)?
    ): File? {
        val client = OkHttpClient()
        val fileName = "${appId}_${versionId}.apk"
        val dir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: appContext.filesDir
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, fileName)

        val requestBuilder = Request.Builder().url(url)
        var downloadedBytes = 0L

        if (file.exists()) {
            downloadedBytes = file.length()
            requestBuilder.addHeader("Range", "bytes=$downloadedBytes-")
        }

        val request = requestBuilder.build()
        var response: Response? = null
        try {
            response = client.newCall(request).execute()
            if (response.code == 416) {
                if (file.exists() && file.length() > 0) return file
                return null
            }
            if (response.code == 404) return null

            val isResumable = response.code == 206
            if (!response.isSuccessful && !isResumable) {
                 if (file.exists()) file.delete()
                return null
            }
            
            if (file.exists() && downloadedBytes > 0 && !isResumable) {
                file.delete()
                downloadedBytes = 0
            }

            val body = response.body ?: return null
            val contentLength = body.contentLength()
            val totalBytes = if (contentLength == -1L) -1L else contentLength + downloadedBytes

            body.byteStream().use { inputStream ->
                FileOutputStream(file, downloadedBytes > 0 && isResumable).use { outputStream ->
                    var currentBytes = downloadedBytes
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        if (!currentCoroutineContext().isActive) throw CancellationException()
                        outputStream.write(buffer, 0, bytesRead)
                        currentBytes += bytesRead
                        if (totalBytes > 0) {
                            val progress = ((currentBytes * 100) / totalBytes).toInt()
                            withContext(Dispatchers.Main) {
                                onProgress?.invoke(progress, currentBytes, totalBytes)
                            }
                        }
                    }
                }
            }
            return file
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw e
        } finally {
            response?.close()
        }
    }

    fun deleteDownloadedFile(appId: String, versionId: Long) {
        scope.launch {
            try {
                val fileName = "${appId}_${versionId}.apk"
                val dir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: appContext.filesDir
                val file = File(dir, fileName)
                if (file.exists()) file.delete()
            } catch (e: Exception) { }
        }
    }

    fun isServiceAvailable(): Boolean {
        return isInitialized && (mXCService != null || mXHService != null)
    }

    private suspend fun waitForInitialization() {
        while (!isInitialized) {
            delay(100)
        }
    }
}
