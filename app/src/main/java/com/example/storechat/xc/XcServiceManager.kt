package com.example.storechat.xc

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import com.proembed.service.MyService
import com.example.storechat.util.LogUtil
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object XcServiceManager {
    private const val TAG = "XcServiceManager"
    private var service: MyService? = null
    private lateinit var appContext: Context
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun init(context: Context) {
        if (!::appContext.isInitialized) {
            appContext = context.applicationContext
        }
        scope.launch {
            try {
                if (service == null) {
                    service = MyService(appContext)
                    LogUtil.i(TAG, "Hardware service connected successfully.")
                }
            } catch (e: Exception) {
                LogUtil.e(TAG, "Hardware service init error.", e)
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

    suspend fun downloadAndInstall(
        appId: String,
        versionId: Long,
        newVersionCode: Int,
        url: String,
        onProgress: ((progress: Int, currentBytes: Long, totalBytes: Long) -> Unit)?
    ): String? = withContext(Dispatchers.IO) {
        try {
            val file = downloadApkWithResume(appId, versionId, url, onProgress) ?: return@withContext null

            val pm = appContext.packageManager
            val info = pm.getPackageArchiveInfo(file.absolutePath, 0)
            val realPackageName = info?.packageName

            if (realPackageName.isNullOrBlank()) {
                file.delete()
                return@withContext null
            }

            var isInstallSuccessful = false
            if (service != null) {
                try {
                    if (isAppInstalled(pm, realPackageName)) {
                        service?.silentUnInstallApk(realPackageName)
                        // 改为轮询检查，最多等待10秒
                        for (i in 0..9) {
                            delay(1000)
                            if (!isAppInstalled(pm, realPackageName)) {
                                break
                            }
                        }
                    }
                    
                    if (!isAppInstalled(pm, realPackageName)) {
                        service?.silentInstallApk(file.absolutePath, realPackageName, false)
                        // 改为轮询检查，最多等待15秒
                        for (i in 0..14) {
                            delay(1000)
                            if (getInstalledVersionCode(pm, realPackageName) >= newVersionCode) {
                                isInstallSuccessful = true
                                break
                            }
                        }
                    }
                } catch (e: Exception) {
                    LogUtil.e(TAG, "Silent install error", e)
                    if (e is CancellationException) throw e
                }
            }

            if (!isInstallSuccessful) return@withContext null
            return@withContext realPackageName

        } catch (e: Exception) {
            if (e is CancellationException) throw e
            return@withContext null
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
            if (response.code == 416) { // Range Not Satisfiable
                if (file.exists() && file.length() > 0) return file
                return null
            }
            if (response.code == 404) return null

            val isResumable = response.code == 206
            if (!response.isSuccessful && !isResumable) {
                 if (file.exists()) {
                    file.delete()
                }
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
}