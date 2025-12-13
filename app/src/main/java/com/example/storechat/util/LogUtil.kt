package com.example.storechat.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogUtil {
    private const val TAG = "LogUtil"
    private const val MAX_LOG_SIZE = 10 * 1024 * 1024 // 10MB
    private var logFile: File? = null

    fun init(context: Context) {
        try {
            val logDir = File(context.getExternalFilesDir(null), "logs")
            if (!logDir.exists()) {
                logDir.mkdirs()
            }
            logFile = File(logDir, "app_log.txt")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize log file", e)
        }
    }

    fun d(tag: String, message: String) {
        Log.d(tag, message)
        writeLogToFile("DEBUG", tag, message)
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
        writeLogToFile("INFO", tag, message)
    }

    fun w(tag: String, message: String) {
        Log.w(tag, message)
        writeLogToFile("WARN", tag, message)
    }

    fun e(tag: String, message: String) {
        Log.e(tag, message)
        writeLogToFile("ERROR", tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable) {
        Log.e(tag, message, throwable)
        writeLogToFile("ERROR", tag, "$message\n${throwable.stackTraceToString()}")
    }

    private fun writeLogToFile(level: String, tag: String, message: String) {
        try {
            logFile?.let { file ->
                // Check file size and clear if too large
                if (file.exists() && file.length() > MAX_LOG_SIZE) {
                    file.writeText("")
                }

                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
                val logEntry = "[$timestamp] [$level] [$tag] $message\n"
                
                FileWriter(file, true).use { writer ->
                    writer.append(logEntry)
                }
            }
        } catch (e: IOException) {
            // Silent fail to avoid recursive logging
        } catch (e: Exception) {
            // Silent fail to avoid recursive logging
        }
    }
}