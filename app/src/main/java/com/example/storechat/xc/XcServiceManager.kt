package com.example.storechat.xc

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
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

    private const val FILE_PROVIDER_AUTHORITY = "com.example.storechat.fileprovider"

    fun init(context: Context) {
        appContext = context.applicationContext
        scope.launch {
            try {
                if (service == null) {
                    service = MyService(appContext)
                    LogUtil.i(TAG, "Hardware service connected successfully.")
                }
            } catch (e: Exception) {
                LogUtil.e(TAG, "Hardware service init error. Fallback to standard installer will be used.", e)
            }
        }
    }

    // 修改点：增加 currentBytes 和 totalBytes 参数
    suspend fun downloadAndInstall(
        appId: String,
        versionId: Long,
        url: String,
        onProgress: ((progress: Int, currentBytes: Long, totalBytes: Long) -> Unit)?
    ): String? = withContext(Dispatchers.IO) {
        try {
            val file = downloadApkWithResume(appId, versionId, url, onProgress) ?: return@withContext null

            val pm = appContext.packageManager
            val info = pm.getPackageArchiveInfo(file.absolutePath, 0)
            val realPackageName = info?.packageName

            if (realPackageName.isNullOrBlank()) {
                LogUtil.e(TAG, "Failed to parse APK, cannot get package name.")
                file.delete()
                return@withContext null
            }
            LogUtil.d(TAG, "APK parsed successfully. PackageName: $realPackageName")

            if (service != null) {
                LogUtil.d(TAG, "Attempting silent install for $realPackageName...")
                service?.silentInstallApk(file.absolutePath, realPackageName, false)
            } else {
                LogUtil.d(TAG, "Hardware service not found. Using standard installer for $realPackageName...")
                promptStandardInstall(file)
            }

            return@withContext realPackageName

        } catch (e: Exception) {
            LogUtil.e(TAG, "Download and install process failed.", e)
            if (e is CancellationException) throw e
            return@withContext null
        }
    }

    // 修改点：同步修改签名
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

        suspend fun executeDownload(allowRange: Boolean): File? {
            val requestBuilder = Request.Builder().url(url)
            var downloadedBytes = 0L

            if (allowRange && file.exists()) {
                downloadedBytes = file.length()
                LogUtil.d(TAG, "[Resume] File exists with size: $downloadedBytes. Adding Range header.")
                requestBuilder.addHeader("Range", "bytes=$downloadedBytes-")
            }

            val request = requestBuilder.build()
            var response: Response? = null
            try {
                response = client.newCall(request).execute()
                LogUtil.d(TAG, "[Resume] Server responded with code: ${response.code}")

                if (response.code == 416) {
                    LogUtil.w(TAG, "[Resume] Server returned 416. Range not satisfiable.")
                    return null
                }

                val isResumable = response.code == 206 && response.header("Content-Range") != null

                if (!response.isSuccessful && !isResumable) {
                    LogUtil.e(TAG, "Download failed: Server returned unsuccessful code ${response.code}")
                    return null
                }

                if (file.exists() && downloadedBytes > 0 && !isResumable) {
                    LogUtil.w(TAG, "[Resume] Server does not support resume (sent code ${response.code}). Restarting download.")
                    file.delete()
                    downloadedBytes = 0
                }

                val body = response.body ?: return null
                val totalBytes = body.contentLength() + downloadedBytes
                LogUtil.d(
                    TAG,
                    "[Resume] Starting download. Append: ${downloadedBytes > 0 && isResumable}, Downloaded: $downloadedBytes, ContentLength: ${body.contentLength()}, Total: $totalBytes"
                )

                body.byteStream().use { inputStream ->
                    FileOutputStream(file, downloadedBytes > 0 && isResumable).use { outputStream ->
                        var currentBytes = downloadedBytes
                        val buffer = ByteArray(8192)
                        var bytesRead: Int

                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            if (!currentCoroutineContext().isActive) {
                                LogUtil.d(TAG, "Download cancelled by coroutine.")
                                throw CancellationException("Cancelled by user")
                            }
                            outputStream.write(buffer, 0, bytesRead)
                            currentBytes += bytesRead
                            if (totalBytes > 0) {
                                val progress = ((currentBytes * 100) / totalBytes).toInt()
                                // 修改点：回调详细字节数到主线程
                                withContext(Dispatchers.Main) {
                                    onProgress?.invoke(progress, currentBytes, totalBytes)
                                }
                            }
                        }

                        if (currentBytes < totalBytes && totalBytes > 0) {
                            val msg = "Download incomplete. Expected $totalBytes but got $currentBytes."
                            LogUtil.e(TAG, msg)
                            throw IOException(msg)
                        }
                    }
                }

                LogUtil.d(TAG, "Download finished successfully: ${file.absolutePath}")
                return file

            } catch (e: Exception) {
                if (e is CancellationException) {
                    LogUtil.d(TAG, "Download cancelled (no retry).")
                    throw e
                }
                LogUtil.e(TAG, "Exception during download: ${e.message}", e)
                throw e
            } finally {
                response?.close()
            }
        }

        try {
            val first = executeDownload(allowRange = true)
            if (first != null) return first
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            LogUtil.e(TAG, "[Resume] Network error during resume, preserving file. Error: ${e.message}")
            return null
        }

        if (file.exists()) {
            LogUtil.w(TAG, "[Resume] Retry: deleting local file and downloading from scratch.")
            file.delete()
        }

        return executeDownload(allowRange = false)
    }

    fun deleteDownloadedFile(appId: String, versionId: Long) {
        scope.launch {
            try {
                val fileName = "${appId}_${versionId}.apk"
                val dir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: appContext.filesDir
                val file = File(dir, fileName)
                if (file.exists()) {
                    if (file.delete()) {
                        LogUtil.d(TAG, "Successfully deleted partially downloaded file: $fileName")
                    } else {
                        LogUtil.e(TAG, "Failed to delete partially downloaded file: $fileName")
                    }
                }
            } catch (e: Exception) {
                LogUtil.e(TAG, "Error deleting downloaded file for $appId-$versionId", e)
            }
        }
    }

    private suspend fun promptStandardInstall(apkFile: File) {
        withContext(Dispatchers.Main) {
            val intent = Intent(Intent.ACTION_VIEW)
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(appContext, FILE_PROVIDER_AUTHORITY, apkFile)
            } else {
                Uri.fromFile(apkFile)
            }
            intent.setDataAndType(uri, "application/vnd.android.package-archive")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            if (appContext.packageManager.resolveActivity(intent, 0) != null) {
                appContext.startActivity(intent)
            } else {
                LogUtil.e(TAG, "No activity found to handle standard installation intent.")
                Toast.makeText(appContext, "无法打开安装器", Toast.LENGTH_SHORT).show()
            }
        }
    }
}