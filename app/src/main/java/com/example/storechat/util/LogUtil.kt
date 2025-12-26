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
    private var logDir: File? = null
    private val currentLogFileName = "app_log.txt"
    private val oldLogFileName = "app_log_old.txt"

    fun init(context: Context) {
        try {
            logDir = File(context.getExternalFilesDir(null), "logs")
            if (logDir?.exists() == false) {
                logDir?.mkdirs()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize log directory", e)
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
        logDir ?: return
        try {
            val logFile = File(logDir, currentLogFileName)
            // Rotate if the current log file is too large
            if (logFile.exists() && logFile.length() > MAX_LOG_SIZE) {
                val oldLogFile = File(logDir, oldLogFileName)
                if (oldLogFile.exists()) {
                    oldLogFile.delete()
                }
                logFile.renameTo(oldLogFile)
            }

            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            val logEntry = "[$timestamp] [$level] [$tag] $message\n"
            
            FileWriter(logFile, true).use { writer ->
                writer.append(logEntry)
            }
        } catch (e: IOException) {
            // Silent fail to avoid recursive logging
        } catch (e: Exception) {
            // Silent fail
        }
    }
}