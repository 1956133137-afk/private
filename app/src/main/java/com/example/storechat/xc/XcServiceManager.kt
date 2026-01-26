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
 * 支持两种主板类型的静默安装：
 * 1. XC主板（向成电子）- 使用MyService服务
 * 2. XH主板（芯伙科技）- 使用MyManager服务
 *
 * 根据设备型号自动选择对应的服务：
 * - rk3576_u, QUAD-CORE A133 b7: 使用XH服务
 * - 其他设备: 使用XC服务
 */
object XcServiceManager {
    private const val TAG = "XcServiceManager"
    private var mXCService: MyService? = null  // 向成主板服务
    private var mXHService: MyManager? = null  // 芯伙主板服务
    private var boardType = 0 // 0: uninitialized, 1: XC, 2: XH
    private lateinit var appContext: Context
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 添加一个标志位来跟踪服务初始化状态
    private var isInitialized = false
    private val initLock = Object()
    /**
     * 初始化服务管理器
     * 根据设备型号自动选择对应的服务
     */
    fun init(context: Context) {
        synchronized(initLock) {
            if (isInitialized) return
            appContext = context.applicationContext
            try {
                when (Build.MODEL.trim()) {
                    "rk3576_u", "QUAD-CORE A133 b7" -> {
                        mXHService = MyManager.getInstance(appContext)
                        boardType = 2
                        LogUtil.i(TAG, "XH service (MyManager) connected successfully for RK device: ${Build.MODEL}")
                    }
                    else -> {
                        mXCService = MyService(appContext)
                        boardType = 1
                        LogUtil.i(TAG, "XC service (MyService) connected successfully for non-RK device: ${Build.MODEL}")
                    }
                }
                isInitialized = true
            } catch (e: Exception) {
                LogUtil.e(TAG, "Hardware service init error.", e)
                isInitialized = false
            }
        }
    }

    /**
     * 检查应用是否已安装
     *
     * @param pm 包管理器
     * @param packageName 包名
     * @return 是否已安装
     */
    private fun isAppInstalled(pm: PackageManager, packageName: String): Boolean {
        return try {
            pm.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * 检索已安装应用程序的版本代码。
     *
     * @param pm PackageManager 实例。
     * @param packageName 应用程序的包名。
     * @return 应用程序的版本代码，如果未找到应用，则返回 -1。
     */
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
     * 下载并静默安装APK
     *
     * @param appId 应用ID
     * @param versionId 版本ID
     * @param newVersionCode 新版本号
     * @param url APK下载链接
     * @param onProgress 下载进度回调
     * @return 安装成功返回包名，失败返回null
     */
    suspend fun downloadAndInstall(
        appId: String,
        versionId: Long,
        newVersionCode: Int,
        url: String,
        onProgress: ((progress: Int, currentBytes: Long, totalBytes: Long) -> Unit)?
    ): String? = withContext(Dispatchers.IO) {
        // 确保服务已初始化
        waitForInitialization()

        try {
            // 1. 下载APK文件
            val file = downloadApkWithResume(appId, versionId, url, onProgress) ?: return@withContext null

            // 2. 获取APK包信息
            val pm = appContext.packageManager
            val info = pm.getPackageArchiveInfo(file.absolutePath, 0)
            val realPackageName = info?.packageName

            // 3. 验证包名是否存在
            if (realPackageName.isNullOrBlank()) {
                file.delete()
                return@withContext null
            }

            LogUtil.i(TAG, "Starting installation for package: $realPackageName, file: ${file.absolutePath}")
            LogUtil.i(TAG, "Board type: $boardType, XC service available: ${mXCService != null}, XH service available: ${mXHService != null}")

            var isInstallSuccessful = false
            if (isServiceAvailable()) {
                try {
                    // 4. 如果目标应用已安装，先静默卸载
                    if (isAppInstalled(pm, realPackageName)) {
                        LogUtil.i(TAG, "Package $realPackageName already installed, attempting to uninstall first")
                        when(boardType) {
                            1 -> {
                                LogUtil.i(TAG, "Using XC service for uninstall")
                                try {
                                    mXCService?.silentUnInstallApk(realPackageName)
                                    LogUtil.i(TAG, "Uninstall command sent successfully")
                                } catch (e: Exception) {
                                    LogUtil.e(TAG, "Failed to uninstall with XC service", e)
                                }
                            }
                            2 -> {
                                LogUtil.i(TAG, "Using XH service for uninstall")
                                try {
                                    // 根据AppBoard源码，芯伙主板的卸载方法应该是silentUnInstallApk
                                    // 而不是selfStart，selfStart可能是自启动控制
                                    mXHService?.selfStart(realPackageName)
                                    LogUtil.i(TAG, "Uninstall command sent successfully")
                                } catch (e: Exception) {
                                    LogUtil.e(TAG, "Failed to uninstall with XH service", e)
                                }
                            }
                        }
                        // 等待卸载完成，最多等待10秒
                        for (i in 0..9) {
                            delay(1000)
                            if (!isAppInstalled(pm, realPackageName)) {
                                LogUtil.i(TAG, "Package $realPackageName uninstalled successfully")
                                break
                            } else if (i == 9) {
                                LogUtil.e(TAG, "Failed to uninstall package $realPackageName after 10 seconds")
                            }
                        }
                    }

                    // 5. 执行静默安装
                    if (!isAppInstalled(pm, realPackageName)) {
                        LogUtil.i(TAG, "Installing APK via board type $boardType")
                        when(boardType) {
                            1 -> {
                                LogUtil.i(TAG, "Using XC service for install: ${file.absolutePath}, package: $realPackageName")
                                try {
                                    mXCService?.silentInstallApk(file.absolutePath, realPackageName, false)
                                    LogUtil.i(TAG, "Install command sent successfully")
                                } catch (e: Exception) {
                                    LogUtil.e(TAG, "Failed to install with XC service", e)
                                }
                            }
                            2 -> {
                                LogUtil.i(TAG, "Using XH service for install: ${file.absolutePath}")
                                try {
                                    mXHService?.silentInstallApk(file.absolutePath, false)
                                    LogUtil.i(TAG, "XH install command sent successfully")
                                } catch (e: Exception) {
                                    LogUtil.e(TAG, "Failed to install with XH service", e)
                                }
                            }
                        }
                        // 等待安装完成，最多等待15秒
                        for (i in 0..14) {
                            delay(1000)
                            val installedVersion = getInstalledVersionCode(pm, realPackageName)
                            LogUtil.i(TAG, "Checking installation progress... Attempt $i, installed version: $installedVersion, required: $newVersionCode")
                            if (installedVersion >= newVersionCode) {
                                LogUtil.i(TAG, "Installation successful")
                                isInstallSuccessful = true
                                break
                            } else if (i == 14) {
//                                LogUtil.e(TAG, "Installation failed after 15 seconds, installed version: $installedVersion, required: $newVersionCode")
                            }
                        }
                    } else {
//                        LogUtil.e(TAG, "Package $realPackageName still exists after uninstall attempt, cannot proceed with installation")
                    }
                } catch (e: Exception) {
//                    LogUtil.e(TAG, "Silent install/uninstall error", e)
                    if (e is CancellationException) throw e
                }
            } else {
                LogUtil.e(TAG, "No service available for board type $boardType, isInitialized: $isInitialized")
            }

            // 6. 如果静默安装失败，尝试使用标准安装方法
            if (!isInstallSuccessful) {
                LogUtil.i(TAG, "Silent installation failed, trying standard installation")
                isInstallSuccessful = tryStandardInstall(file, realPackageName)
            }

            if (!isInstallSuccessful) return@withContext null
            LogUtil.i(TAG, "Installation completed successfully for package: $realPackageName")
            return@withContext realPackageName

        } catch (e: Exception) {
            LogUtil.e(TAG, "Unexpected error during download and install", e)
            if (e is CancellationException) throw e
            return@withContext null
        }
    }


    /**
     * 尝试使用标准安装方法（需要用户确认）
     *
     * @param apkFile APK文件
     * @param packageName 包名
     * @return 是否成功启动安装流程
     */
    private suspend fun tryStandardInstall(apkFile: File, packageName: String): Boolean = withContext(Dispatchers.Main) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val uri = FileProvider.getUriForFile(
                        appContext,
                        "${appContext.packageName}.fileprovider",
                        apkFile
                    )
                    setDataAndType(uri, "application/vnd.android.package-archive")
                } else {
                    setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive")
                }
            }

            appContext.startActivity(intent)
            LogUtil.i(TAG, "Started standard installation for $packageName")
            // 对于标准安装，我们启动安装界面但无法知道用户是否完成安装
            // 所以这里返回true，表示已启动安装流程
            true
        } catch (e: Exception) {
//            LogUtil.e(TAG, "Failed to start standard installation", e)
            false
        }
    }
    /**
     * 断点续传下载APK
     *
     * @param appId 应用ID
     * @param versionId 版本ID
     * @param url 下载链接
     * @param onProgress 进度回调
     * @return 下载的文件，失败返回null
     */
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

        // 如果文件存在，尝试从上次断点处继续下载
        if (file.exists()) {
            downloadedBytes = file.length()
            requestBuilder.addHeader("Range", "bytes=$downloadedBytes-")
        }

        val request = requestBuilder.build()
        var response: Response? = null
        try {
            response = client.newCall(request).execute()
            // 服务器不支持范围请求，但我们已经拥有完整的文件。
            if (response.code == 416) { // Range Not Satisfiable
                if (file.exists() && file.length() > 0) return file
                return null
            }
            // 在服务器上找不到文件。
            if (response.code == 404) return null

            val isResumable = response.code == 206 // Partial Content
            // 如果请求不成功且非断点续传，则删除部分文件并失败。
            if (!response.isSuccessful && !isResumable) {
                 if (file.exists()) {
                    file.delete()
                }
                return null
            }
            
            // 如果服务器不支持断点续传，删除已存在的部分文件并重新开始下载。
            if (file.exists() && downloadedBytes > 0 && !isResumable) {
                file.delete()
                downloadedBytes = 0
            }

            val body = response.body ?: return null
            val contentLength = body.contentLength()
            val totalBytes = if (contentLength == -1L) -1L else contentLength + downloadedBytes

            body.byteStream().use { inputStream ->
                // 如果是断点续传，则以追加模式打开文件
                FileOutputStream(file, downloadedBytes > 0 && isResumable).use { outputStream ->
                    var currentBytes = downloadedBytes
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        // 支持协程取消
                        if (!currentCoroutineContext().isActive) throw CancellationException()
                        outputStream.write(buffer, 0, bytesRead)
                        currentBytes += bytesRead
                        if (totalBytes > 0) {
                            val progress = ((currentBytes * 100) / totalBytes).toInt()
                            // 在主线程上报告进度
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

    /**
     * 从下载目录中删除已下载的 APK 文件。
     *
     * @param appId 应用的唯一 ID。
     * @param versionId 应用的版本 ID。
     */
    fun deleteDownloadedFile(appId: String, versionId: Long) {
        scope.launch {
            try {
                val fileName = "${appId}_${versionId}.apk"
                val dir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: appContext.filesDir
                val file = File(dir, fileName)
                if (file.exists()) file.delete()
            } catch (e: Exception) { 
                // 删除过程中忽略异常
            }
        }
    }
    /**
     * 检查服务是否已初始化并可用
     */
    fun isServiceAvailable(): Boolean {
        return isInitialized && when (boardType) {
            1 -> mXCService != null
            2 -> mXHService != null
            else -> false
        }
    }
    /**
     * 等待服务初始化完成
     */
    private suspend fun waitForInitialization() {
        while (!isInitialized) {
            delay(100) // 等待100毫秒后重试
        }
    }
}