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
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream

object XcServiceManager {
    private const val TAG = "XcServiceManager"
    private var service: MyService? = null
    private lateinit var appContext: Context
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // FileProvider Authority
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

    /**
     * Downloads an APK, then attempts a silent install. If the hardware service is unavailable,
     * it falls back to the standard system package installer.
     * @return String? The package name of the app if the process was initiated, or null on failure.
     */
    suspend fun downloadAndInstall(
        url: String,
        onProgress: ((Int) -> Unit)?
    ): String? = withContext(Dispatchers.IO) {
        try {
            // 1. Download the file
            val file = downloadApk(url, onProgress) ?: return@withContext null

            // 2. Parse APK to get package name
            val pm = appContext.packageManager
            val info = pm.getPackageArchiveInfo(file.absolutePath, 0)
            val realPackageName = info?.packageName

            if (realPackageName.isNullOrBlank()) {
                Log.e(TAG, "Failed to parse APK, cannot get package name.")
                return@withContext null
            }
            Log.d(TAG, "APK parsed successfully. PackageName: $realPackageName")

            // 3. Attempt installation
            if (service != null) {
                // Hardware service available -> Silent install
                Log.d(TAG, "Attempting silent install for $realPackageName...")
                service?.silentInstallApk(file.absolutePath, realPackageName, false) // Changed to false
            } else {
                // Hardware service unavailable -> Fallback to standard install
                Log.d(TAG, "Hardware service not found. Using standard installer for $realPackageName...")
                promptStandardInstall(file)
            }

            // 4. Return package name to let the repository update the UI state
            return@withContext realPackageName

        } catch (e: Exception) {
            Log.e(TAG, "Download and install process failed.", e)
            if (e is kotlinx.coroutines.CancellationException) throw e // Rethrow cancellation
            return@withContext null
        }
    }

    private suspend fun downloadApk(url: String, onProgress: ((Int) -> Unit)?): File? {
        // 1. Prepare file path
        val tempName = "update_${System.currentTimeMillis()}.apk"
        val dir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: appContext.filesDir
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, tempName)
        if (file.exists()) file.delete()

        // 2. Download via OkHttp
        val client = OkHttpClient()
        val req = Request.Builder().url(url).build()
        val resp = client.newCall(req).execute()
        if (!resp.isSuccessful || resp.body == null) {
            Log.e(TAG, "Download failed: Response unsuccessful or body is null.")
            return null
        }

        val body = resp.body!!
        val total = body.contentLength()
        val input = body.byteStream()
        val output = BufferedOutputStream(FileOutputStream(file))
        val buffer = ByteArray(8192)
        var len: Int
        var current = 0L

        while (input.read(buffer).also { len = it } != -1) {
            if (!currentCoroutineContext().isActive) {
                output.close()
                input.close()
                Log.d(TAG, "Download cancelled.")
                return null
            }
            output.write(buffer, 0, len)
            current += len
            if (total > 0) {
                val progress = ((current * 100) / total).toInt()
                withContext(Dispatchers.Main) { onProgress?.invoke(progress) }
            }
        }
        output.flush()
        output.close()
        resp.close()
        Log.d(TAG, "Download complete: ${file.absolutePath}")
        return file
    }

    private suspend fun promptStandardInstall(apkFile: File) {
        withContext(Dispatchers.Main) {
            val intent = Intent(Intent.ACTION_VIEW)
            val uri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
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
