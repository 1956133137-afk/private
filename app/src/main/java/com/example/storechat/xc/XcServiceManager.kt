package com.example.storechat.xc

import android.content.Context
import android.os.Environment
import android.util.Log
import android.widget.Toast
import com.proembed.service.MyService
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.coroutines.cancellation.CancellationException

/**
 * XcServiceManager 保留原来的静默安装能力，并新增一个
 * suspend fun downloadAndInstall(...) 方法，用于：
 *   - 下载远程 APK 到 app 的 external files dir
 *   - 下载过程中回调进度 percent (0..100)
 *   - 下载完成后调用 MyService.silentInstallApk(localPath,...)
 *
 * 这样 AppRepository 只需要把后端返回的 fileUrl 交给这个方法即可。
 */
object XcServiceManager {

    @Volatile
    private var service: MyService? = null
    private lateinit var appContext: Context

    // 用于下载/安装的协程作用域（内部使用）
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun init(context: Context) {
        appContext = context.applicationContext
        coroutineScope.launch {
            if (service == null) {
                service = MyService(appContext)
            }
        }
    }

    /**
     * 异步静默安装 APK（保持原样，兼容现有调用）
     */
    fun installApk(
        apkPath: String,
        packageName: String,
        openAfter: Boolean
    ) {
        coroutineScope.launch {
            val s = service
            if (s == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(appContext, "服务正在初始化，请稍后重试", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            s.silentInstallApk(apkPath, packageName, openAfter)
        }
    }

    /**
     * 异步静默卸载 APK（保持原样）
     */
    fun uninstallApk(packageName: String) {
        coroutineScope.launch {
            val s = service
            if (s == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(appContext, "服务正在初始化，请稍后重试", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            s.silentUnInstallApk(packageName)
        }
    }

    /**
     * 新增：下载远程 APK，并在完成后调用静默安装。
     *
     * @param apkUrl 远程文件 URL（由后端返回的 fileUrl）
     * @param packageName 用于通知 / 安装时的包名
     * @param openAfter 安装完成后是否打开
     * @param progressCallback 回调下载进度 0..100（content-length 未返回时，会尽量发送 -1/100）
     *
     * 返回：true 表示下载 + 安装流程已触发（并且没有显式报错），false 表示失败。
     *
     * 注意：该方法会在 IO dispatcher 执行，支持协程取消（会抛出 CancellationException）。
     */


    // 在 downloadAndInstall 内增加日志
    suspend fun downloadAndInstall(
        apkUrl: String,
        packageName: String,
        openAfter: Boolean,
        progressCallback: ((Int) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val TAG = "XCSVC"
        try {
            Log.d(TAG, "start download: apkUrl=$apkUrl pkg=$packageName openAfter=$openAfter")
            val client = OkHttpClient()
            val req = Request.Builder().url(apkUrl).get().build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) {
                Log.e(TAG, "download failed http=${resp.code}")
                resp.close()
                return@withContext false
            }
            val body = resp.body ?: run {
                Log.e(TAG, "download failed: body == null")
                resp.close()
                return@withContext false
            }
            val contentLength = body.contentLength()
            Log.d(TAG, "contentLength = $contentLength")
            val input: InputStream = body.byteStream()

            val downloadsDir: File? = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            val targetDir = downloadsDir ?: appContext.filesDir
            if (!targetDir.exists()) targetDir.mkdirs()
            val targetFile = File(targetDir, "${packageName}_${System.currentTimeMillis()}.apk")

            Log.d(TAG, "writing to ${targetFile.absolutePath}")
            BufferedOutputStream(FileOutputStream(targetFile)).use { out ->
                val buffer = ByteArray(8 * 1024)
                var read: Int
                var totalRead = 0L
                while (true) {
                    if (!coroutineContext.isActive) {
                        Log.w(TAG, "download cancelled by coroutineContext")
                        throw CancellationException("下载已取消")
                    }
                    read = input.read(buffer)
                    if (read == -1) break
                    out.write(buffer, 0, read)
                    totalRead += read
                    if (contentLength > 0) {
                        val percent = ((totalRead * 100) / contentLength).toInt().coerceIn(0, 100)
                        Log.d(TAG, "progress: $percent% ($totalRead/$contentLength)")
                        try { progressCallback?.invoke(percent) } catch (_: Exception) {}
                    } else {
                        // content-length unknown
                        Log.d(TAG, "downloaded bytes: $totalRead (content-length unknown)")
                        try { progressCallback?.invoke(50) } catch (_: Exception) {}
                    }
                }
                out.flush()
            }
            resp.close()
            Log.d(TAG, "download complete: ${targetFile.absolutePath}")

            val s = service
            if (s == null) {
                Log.e(TAG, "MyService is null, cannot install")
                return@withContext false
            }

            Log.d(TAG, "calling silentInstallApk on MyService: ${targetFile.absolutePath}")
            withContext(Dispatchers.IO) {
                s.silentInstallApk(targetFile.absolutePath, packageName, openAfter)
            }
            Log.d(TAG, "silentInstallApk finished for $packageName")
            return@withContext true

        } catch (ce: CancellationException) {
            Log.w("XCSVC", "download cancelled: ${ce.message}")
            return@withContext false
        } catch (e: Exception) {
            Log.e("XCSVC", "download/install exception", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(appContext, "下载或安装发生异常: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            return@withContext false
        }
    }

}
