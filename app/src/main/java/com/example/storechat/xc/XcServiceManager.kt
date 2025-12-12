package com.example.storechat.xc

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.proembed.service.MyService
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

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
                    Log.i(TAG, "Hardware service connected successfully.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Hardware service init error. Fallback to standard installer will be used.", e)
            }
        }
    }

    suspend fun downloadAndInstall(
        appId: String,
        versionId: Long,
        url: String,
        onProgress: ((Int) -> Unit)?
    ): String? = withContext(Dispatchers.IO) {
        try {
            val file = downloadApkWithResume(appId, versionId, url, onProgress) ?: return@withContext null

            val pm = appContext.packageManager
            val info = pm.getPackageArchiveInfo(file.absolutePath, 0)
            val realPackageName = info?.packageName

            if (realPackageName.isNullOrBlank()) {
                Log.e(TAG, "Failed to parse APK, cannot get package name.")
                file.delete() // Clean up invalid APK
                return@withContext null
            }
            Log.d(TAG, "APK parsed successfully. PackageName: $realPackageName")

            if (service != null) {
                Log.d(TAG, "Attempting silent install for $realPackageName...")
                service?.silentInstallApk(file.absolutePath, realPackageName, false)
            } else {
                Log.d(TAG, "Hardware service not found. Using standard installer for $realPackageName...")
                promptStandardInstall(file)
            }

            return@withContext realPackageName

        } catch (e: Exception) {
            Log.e(TAG, "Download and install process failed.", e)
            if (e is kotlinx.coroutines.CancellationException) throw e
            return@withContext null
        }
    }

    private suspend fun downloadApkWithResume(
        appId: String,
        versionId: Long,
        url: String,
        onProgress: ((Int) -> Unit)?
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
            Log.d(TAG, "[Resume] File exists with size: $downloadedBytes. Adding Range header.")
            requestBuilder.addHeader("Range", "bytes=$downloadedBytes-")
        }

        val request = requestBuilder.build()
        var response: Response? = null
        try {
            response = client.newCall(request).execute()
            Log.d(TAG, "[Resume] Server responded with code: ${response.code}")

            val isResumable = response.code == 206 && response.header("Content-Range") != null

            if (!response.isSuccessful && !isResumable) {
                Log.e(TAG, "Download failed: Server returned unsuccessful code ${response.code}")
                return null
            }
            
            if (file.exists() && downloadedBytes > 0 && !isResumable) {
                Log.w(TAG, "[Resume] Server does not support resume (sent code ${response.code}). Restarting download.")
                file.delete()
                downloadedBytes = 0
            }

            val body = response.body ?: return null
            val totalBytes = body.contentLength() + downloadedBytes
            Log.d(TAG, "[Resume] Starting download. Append: ${downloadedBytes > 0 && isResumable}, Downloaded: $downloadedBytes, ContentLength: ${body.contentLength()}, Total: $totalBytes")

            body.byteStream().use { inputStream ->
                FileOutputStream(file, downloadedBytes > 0 && isResumable).use { outputStream ->
                    var currentBytes = downloadedBytes
                    val buffer = ByteArray(8192)
                    var bytesRead: Int

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        if (!currentCoroutineContext().isActive) {
                            Log.d(TAG, "Download cancelled by coroutine.")
                            return null
                        }
                        outputStream.write(buffer, 0, bytesRead)
                        currentBytes += bytesRead
                        if (totalBytes > 0) {
                            val progress = ((currentBytes * 100) / totalBytes).toInt()
                            withContext(Dispatchers.Main) { onProgress?.invoke(progress) }
                        }
                    }

                    if (currentBytes < totalBytes && totalBytes > 0) {
                        Log.e(TAG, "Download incomplete. Expected $totalBytes but got $currentBytes.")
                        return null
                    }
                }
            }

            Log.d(TAG, "Download finished successfully: ${file.absolutePath}")
            return file

        } catch (e: Exception) {
            Log.e(TAG, "Exception during download.", e)
            return null
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
                if (file.exists()) {
                    if (file.delete()) {
                        Log.d(TAG, "Successfully deleted partially downloaded file: $fileName")
                    } else {
                        Log.e(TAG, "Failed to delete partially downloaded file: $fileName")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting downloaded file for $appId-$versionId", e)
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
                Log.e(TAG, "No activity found to handle standard installation intent.")
                Toast.makeText(appContext, "无法打开安装器", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
