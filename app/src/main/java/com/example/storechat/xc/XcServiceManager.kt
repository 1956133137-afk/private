package com.example.storechat.xc

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
 * XcServiceManager - 静默安装管理器 (修复版)
 * 修复内容：
 * 1. [关键] init() 中增加了 bindAIDLService 调用，解决 API 无效的问题。
 * 2. 针对降级安装，采用“死磕卸载”策略，确保先卸载再安装。
 * 3. 移除了不存在的 execSuCmd，使用 execRootCmd 兜底。
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
                    // 【关键修复】根据文档 Page 9，必须调用 bindAIDLService
                    mXHService?.bindAIDLService(appContext)
                    LogUtil.i(TAG, "XH 服务 (MyManager) 已初始化并绑定 AIDL")
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
     * 下载并安装APK
     */
    suspend fun downloadAndInstall(
        appId: String,
        versionId: Long,
        newVersionCode: Int,
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

            // 读取 APK 实际版本号
            val realVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                info.versionCode
            }

            LogUtil.i(TAG, "开始为 $realPackageName 安装。服务器版本: $newVersionCode, APK 实际版本: $realVersionCode")

            var isInstallSuccessful = false

            // 方法 1: 使用首选静默安装服务
            LogUtil.i(TAG, "第一种方式: Preferred silent install (Type $boardType)")
            isInstallSuccessful = trySilentInstall(pm, file, realPackageName, realVersionCode, boardType)

            // 方法 2: 如果首选失败...
            if (!isInstallSuccessful) {
                val altType = if (boardType == 1) 2 else 1
                LogUtil.i(TAG, "方法 1 失败, 第二种方式: 替代静默安装 (类型 $altType)")
                isInstallSuccessful = trySilentInstall(pm, file, realPackageName, realVersionCode, altType)
            }

            // 方法 3: 标准安装
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
     * 尝试静默安装 (修复降级死循环版)
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
            if (type == 1 && mXCService == null) return false
            if (type == 2 && mXHService == null) return false

            val currentVersion = getInstalledVersionCode(pm, realPackageName)

            // 0. 版本一致且应用存在，直接成功
            if (currentVersion == newVersionCode) {
                LogUtil.i(TAG, "版本一致 ($currentVersion)，跳过安装")
                return true
            }

            val isDowngrade = currentVersion > newVersionCode && currentVersion != -1
            if (isDowngrade) {
                LogUtil.w(TAG, "检测到降级 ($currentVersion -> $newVersionCode)，准备强制卸载")
            }

            // -----------------------------------------------------------
            // 1. 卸载阶段 (死磕模式：绑定服务后 unInstallApk 应该生效了)
            // -----------------------------------------------------------
            if (isAppInstalled(pm, realPackageName)) {
                // 降级时给 15秒，普通更新给 5秒
                val maxRetry = if (isDowngrade) 15 else 5

                for (i in 0 until maxRetry) {
                    if (!isAppInstalled(pm, realPackageName)) {
                        LogUtil.i(TAG, "检测到旧版本已卸载")
                        break
                    }

                    // 每 3秒 执行一次卸载指令
                    if (i == 0 || i % 3 == 0) {
                        LogUtil.i(TAG, "执行卸载指令... ($i/$maxRetry)")
                        try {
                            when (type) {
                                1 -> mXCService?.silentUnInstallApk(realPackageName)
                                2 -> {
                                    // 【核心】绑定服务后，unInstallApk 应该能返回 true
                                    // 如果 jar 包版本支持，此处应该不再是瓶颈
                                    val sdkResult = mXHService?.unInstallApk(realPackageName) ?: false

                                    // 如果 SDK 依然不行，使用 Root 命令兜底
                                    if (!sdkResult) {
                                        execRootCmd("pm uninstall $realPackageName")
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            execRootCmd("pm uninstall $realPackageName")
                        }
                    }
                    delay(1000)
                }
            }

            // 再次检查卸载结果
            if (isDowngrade && isAppInstalled(pm, realPackageName)) {
                LogUtil.e(TAG, "【警告】卸载未完成，尝试使用降级参数 (-d) 强行覆盖")
                execRootCmd("pm install -r -d ${file.absolutePath}")
            }

            // -----------------------------------------------------------
            // 2. 安装阶段
            // -----------------------------------------------------------
            LogUtil.i(TAG, "执行安装命令 (类型 $type)")

            when (type) {
                1 -> mXCService?.silentInstallApk(file.absolutePath, realPackageName, false)
                2 -> {
                    // 绑定服务后，silentInstallApk 应该能正常工作
                    mXHService?.silentInstallApk(file.absolutePath, false)

                    if (isDowngrade) {
                        execRootCmd("pm install -r -d ${file.absolutePath}")
                    }
                }
            }

            // -----------------------------------------------------------
            // 3. 结果校验
            // -----------------------------------------------------------
            for (i in 0..24) {
                delay(1000)
                val installedVer = getInstalledVersionCode(pm, realPackageName)

                if (installedVer == newVersionCode) {
                    LogUtil.i(TAG, "安装成功：当前版本 $installedVer")
                    return true
                }

                // 补救措施
                if (type == 2 && i > 0 && i % 5 == 0 && installedVer != newVersionCode) {
                    LogUtil.w(TAG, "安装未生效，重试 Root 命令...")
                    execRootCmd("pm install -r -d ${file.absolutePath}")
                }
            }

        } catch (e: Exception) {
            LogUtil.e(TAG, "trySilentInstall 异常: ${e.message}")
        }
        return false
    }

    /**
     * 通用 Shell 命令执行器
     */
    private fun execRootCmd(cmd: String) {
        try {
            // 尝试以 Root 身份执行
            Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
        } catch (e: Exception) {
            try {
                Runtime.getRuntime().exec(cmd)
            } catch (e2: Exception) {
                LogUtil.e(TAG, "Shell命令执行失败: $cmd")
            }
        }
    }


    private suspend fun tryStandardInstall(apkFile: File, packageName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val tempDir = apkFile.parentFile ?: appContext.cacheDir
            val tempFile = File(tempDir, "install_temp_${System.currentTimeMillis()}.apk")
            try {
                tempDir.listFiles()?.forEach {
                    if (it.name.startsWith("install_temp_") && System.currentTimeMillis() - it.lastModified() > 300_000) {
                        it.delete()
                    }
                }
            } catch (e: Exception) {}

            LogUtil.i(TAG, "正在复制 APK 到临时文件: ${tempFile.absolutePath}")
            apkFile.copyTo(tempFile, overwrite = true)
            try {
                Runtime.getRuntime().exec("chmod 644 ${tempFile.absolutePath}").waitFor()
            } catch (e: Exception) {}

            withContext(Dispatchers.Main) {
                val authority = "${appContext.packageName}.fileprovider"
                val contentUri = FileProvider.getUriForFile(appContext, authority, tempFile)
                LogUtil.i(TAG, "生成的内容 URI: $contentUri")
                val intent = Intent(Intent.ACTION_VIEW)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
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