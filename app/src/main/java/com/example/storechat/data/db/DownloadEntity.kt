package com.example.storechat.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "download_tasks")
data class DownloadEntity(
    @PrimaryKey
    val taskKey: String, // 对应 "packageName@versionId"

    val appId: String,
    val versionId: Long,
    val packageName: String,
    val name: String,
    val categoryId: Int,

    val downloadUrl: String,
    val savePath: String,
    val progress: Int,
    val status: Int,
    val createTime: Long = System.currentTimeMillis()
)