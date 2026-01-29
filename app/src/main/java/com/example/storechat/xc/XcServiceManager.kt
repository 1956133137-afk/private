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
                    LogUtil.i(TAG, "XC 服务 (MyService) 已初始化")
                } catch (e: Throwable) {
                    LogUtil.w(TAG, "XC 服务初始化失败: ${e.message}")
                }

                // 尝试初始化芯伙服务
                try {
                    mXHService = MyManager.getInstance(appContext)
                    LogUtil.i(TAG, "XH 服务 (MyManager) 已初始化")
                } catch (e: Throwable) {
                    LogUtil.w(TAG, "XH 服务初始化失败: ${e.message}")
                }

                // 根据设备型号设置首选主板类型
                boardType = when (Build.MODEL.trim()) {
                    "rk3576_u","QUAD-CORE A133 c3" -> 2
                    else -> 1
                }

                isInitialized = true
                LogUtil.i(TAG, "服务管理器已初始化。首选主板类型: $boardType")
            } catch (e: Exception) {
                LogUtil.e(TAG, "硬件服务初始化错误。", e)
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

            LogUtil.i(TAG, "开始为 $realPackageName 安装。服务器版本: $newVersionCode, APK 实际版本: $realVersionCode")

            var isInstallSuccessful = false

            // 方法 1: 使用首选静默安装服务
            // 注意：这里传入 realVersionCode 而不是 newVersionCode
            LogUtil.i(TAG, "第一种方式: Preferred silent install (Type $boardType)")
            isInstallSuccessful = trySilentInstall(pm, file, realPackageName, realVersionCode, boardType)

            // 方法 2: 如果首选失败...
            if (!isInstallSuccessful) {
                val altType = if (boardType == 1) 2 else 1
                LogUtil.i(TAG, "方法 1 失败, 第二种方式: 替代静默安装 (类型 $altType)")
                isInstallSuccessful = trySilentInstall(pm, file, realPackageName, realVersionCode, altType)
            }

            // ... (后续逻辑保持不变)

            // 方法 3: 如果静默安装都失败，尝试标准安装方法
            if (!isInstallSuccessful) {
                LogUtil.i(TAG, "静默安装失败, 第三种方式: 标准安装")
                isInstallSuccessful = tryStandardInstall(file, realPackageName)
            }

            if (!isInstallSuccessful) return@withContext null

            LogUtil.i(TAG, "安装流程已完成: $realPackageName")
            return@withContext realPackageName

        } catch (e: Exception) {
            LogUtil.e(TAG, "下载和安装过程中发生意外错误", e)
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
                LogUtil.i(TAG, "应用包 $realPackageName 已存在，正在尝试通过类型 $type 进行静默卸载")
                when (type) {
                    1 -> mXCService?.silentUnInstallApk(realPackageName)
                    2 -> mXHService?.selfStart(realPackageName) // 保持原有逻辑
                }

                // 等待卸载完成 (最多5秒)
                for (i in 0..4) {
                    delay(1000)
                    if (!isAppInstalled(pm, realPackageName)) {
                        LogUtil.i(TAG, "卸载成功")
                        break
                    }
                }
            }

            // 2. 执行静默安装
            if (!isAppInstalled(pm, realPackageName)) {
                LogUtil.i(TAG, "正在通过类型 $type 执行静默安装")
                when (type) {
                    1 -> mXCService?.silentInstallApk(file.absolutePath, realPackageName, false)
                    2 -> mXHService?.silentInstallApk(file.absolutePath, false)
                }

                // 将等待时间从 60秒 (0..59) 增加到 100秒 (0..99)
                for (i in 0..24) {
                    delay(1000)
                    val installedVersion = getInstalledVersionCode(pm, realPackageName)

                    // 增加日志方便调试，看看到底读到了什么版本
                    if (i % 5 == 0) { // 每5秒打印一次
                        LogUtil.d(TAG, "检查安装状态 ($i/100): 当前=$installedVersion, 目标=$newVersionCode")
                    }

                    if (installedVersion >= newVersionCode) {
                        LogUtil.i(TAG, "Silent install 成功 via 方式 $type")
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "trySilentInstall 错误 (类型 $type): ${e.message}")
            if (e is CancellationException) throw e
        }
        return false
    }


    /**
     * 修复版标准安装：
     * 1. 使用 FileProvider 兼容 Android 7.0+
     * 2. 【关键】创建临时副本，防止原文件被上层逻辑立即删除导致安装器读不到文件
     * 3. 添加 ClipData 和权限标志，确保安装器有权读取
     */
    private suspend fun tryStandardInstall(apkFile: File, packageName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // 1. 创建临时文件副本
            // 原因：downloadAndInstall 返回后，上层会立即删除 apkFile。
            // 如果直接传原文件，系统安装器还没来得及读，文件就没了，导致 "Cannot parse package"。
            val tempDir = apkFile.parentFile ?: appContext.cacheDir
            val tempFile = File(tempDir, "install_temp_${System.currentTimeMillis()}.apk")

            // 清理旧的临时文件 (超过5分钟的)
            try {
                tempDir.listFiles()?.forEach {
                    if (it.name.startsWith("install_temp_") &&
                        System.currentTimeMillis() - it.lastModified() > 300_000) {
                        it.delete()
                    }
                }
            } catch (e: Exception) {}

            LogUtil.i(TAG, "正在复制 APK 到临时文件: ${tempFile.absolutePath}")
            apkFile.copyTo(tempFile, overwrite = true)

            // 2. 尝试修改权限 (兜底措施，确保可读)
            try {
                val p = Runtime.getRuntime().exec("chmod 644 ${tempFile.absolutePath}")
                p.waitFor()
            } catch (e: Exception) {}

            withContext(Dispatchers.Main) {
                // 3. 获取 FileProvider URI
                // 注意：authority 必须与 AndroidManifest.xml 一致
                val authority = "${appContext.packageName}.fileprovider"
                val contentUri = FileProvider.getUriForFile(appContext, authority, tempFile)

                LogUtil.i(TAG, "生成的内容 URI: $contentUri")

                val intent = Intent(Intent.ACTION_VIEW)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                // 关键：授予读权限
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                // 关键：兼容 Android 10+ 和部分定制 ROM，显式设置 ClipData 以增强权限传递稳定性
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    intent.clipData = android.content.ClipData.newRawUri("archive", contentUri)
                }

                intent.setDataAndType(contentUri, "application/vnd.android.package-archive")

                appContext.startActivity(intent)
                LogUtil.i(TAG, "标准安装意图发送成功。")
            }
            true
        } catch (e: Exception) {
            LogUtil.e(TAG, "标准安装失败: ${e.message}", e)
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
