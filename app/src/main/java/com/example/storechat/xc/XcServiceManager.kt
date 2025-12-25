package com.example.storechat.xc

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
                LogUtil.e(TAG, "Failed to parse APK, cannot get package name.")
                file.delete()
                return@withContext null
            }
            LogUtil.d(TAG, "APK parsed successfully. PackageName: $realPackageName")

            var isInstallSuccessful = false

            if (service != null) {
                try {
                    var canProceedToInstall = !isAppInstalled(pm, realPackageName)

                    if (!canProceedToInstall) {
                        LogUtil.d(TAG, "Old version detected. Attempting to silently uninstall $realPackageName...")
                        service?.silentUnInstallApk(realPackageName)
                        
                        LogUtil.d(TAG, "Waiting 5 seconds to verify uninstallation...")
                        delay(5000)

                        if (!isAppInstalled(pm, realPackageName)) {
                            LogUtil.d(TAG, "Silent uninstall successful.")
                            canProceedToInstall = true
                        } else {
                            LogUtil.w(TAG, "Silent uninstall failed or took too long.")
                        }
                    }

                    if (canProceedToInstall) {
                        LogUtil.d(TAG, "Attempting to silently install $realPackageName...")
                        service?.silentInstallApk(file.absolutePath, realPackageName, false)

                        LogUtil.d(TAG, "Waiting 5 seconds to verify installation...")
                        delay(5000)
                        
                        val currentVersionCode = getInstalledVersionCode(pm, realPackageName)
                        if (currentVersionCode >= newVersionCode) {
                            isInstallSuccessful = true
                            LogUtil.d(TAG, "Silent install successful. New version: $currentVersionCode")
                        } else {
                            LogUtil.w(TAG, "Silent install verification failed. Expected: $newVersionCode, Found: $currentVersionCode")
                        }
                    }
                } catch (e: Exception) {
                    LogUtil.e(TAG, "An error occurred during silent uninstall/install process.", e)
                }
            } else {
                 LogUtil.w(TAG, "Hardware service (MyService) not available.")
            }

            if (!isInstallSuccessful) {
                LogUtil.w(TAG, "Silent process failed. Falling back to standard installer.")
//                promptStandardInstall(file)
            }

            return@withContext realPackageName

        } catch (e: Exception) {
            LogUtil.e(TAG, "Download and install process failed.", e)
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

        suspend fun executeDownload(allowRange: Boolean): File? {
            val requestBuilder = Request.Builder().url(url)
            var downloadedBytes = 0L

            if (allowRange && file.exists()) {
                downloadedBytes = file.length()
                requestBuilder.addHeader("Range", "bytes=$downloadedBytes-")
            }

            val request = requestBuilder.build()
            var response: Response? = null
            try {
                response = client.newCall(request).execute()
                if (response.code == 416) return null 

                val isResumable = response.code == 206 && response.header("Content-Range") != null

                if (!response.isSuccessful && !isResumable) {
                    return null
                }

                if (file.exists() && downloadedBytes > 0 && !isResumable) {
                    file.delete()
                    downloadedBytes = 0
                }

                val body = response.body ?: return null
                val totalBytes = body.contentLength() + downloadedBytes

                body.byteStream().use { inputStream ->
                    FileOutputStream(file, downloadedBytes > 0 && isResumable).use { outputStream ->
                        var currentBytes = downloadedBytes
                        val buffer = ByteArray(8192)
                        var bytesRead: Int

                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            if (!currentCoroutineContext().isActive) throw CancellationException("Cancelled by user")
                            
                            outputStream.write(buffer, 0, bytesRead)
                            currentBytes += bytesRead
                            if (totalBytes > 0) {
                                val progress = ((currentBytes * 100) / totalBytes).toInt()
                                withContext(Dispatchers.Main) {
                                    onProgress?.invoke(progress, currentBytes, totalBytes)
                                }
                            }
                        }

                        if (currentBytes < totalBytes && totalBytes > 0) {
                             throw IOException("Download incomplete. Expected $totalBytes but got $currentBytes.")
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

        try {
            val first = executeDownload(allowRange = true)
            if (first != null) return first
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            return null
        }

        if (file.exists()) file.delete()
        return executeDownload(allowRange = false)
    }

    fun deleteDownloadedFile(appId: String, versionId: Long) {
        scope.launch {
            try {
                val fileName = "${appId}_${versionId}.apk"
                val dir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: appContext.filesDir
                val file = File(dir, fileName)
                if (file.exists()) file.delete()
            } catch (e: Exception) {
                LogUtil.e(TAG, "Error deleting downloaded file for $appId-$versionId", e)
            }
        }
    }

//    private suspend fun promptStandardInstall(apkFile: File) {
//        withContext(Dispatchers.Main) {
//            val intent = Intent(Intent.ACTION_VIEW)
//            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
//                FileProvider.getUriForFile(appContext, FILE_PROVIDER_AUTHORITY, apkFile)
//            } else {
//                Uri.fromFile(apkFile)
//            }
//            intent.setDataAndType(uri, "application/vnd.android.package-archive")
//            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
//            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
//
//            if (appContext.packageManager.resolveActivity(intent, 0) != null) {
//                appContext.startActivity(intent)
//            } else {
//                Toast.makeText(appContext, "无法打开安装器", Toast.LENGTH_SHORT).show()
//            }
//        }
//    }
}