package com.example.storechat.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DownloadDao {
    @Query("SELECT * FROM download_tasks ORDER BY createTime DESC")
    suspend fun getAllTasks(): List<DownloadEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(task: DownloadEntity)

    @Query("DELETE FROM download_tasks WHERE taskKey = :key")
    suspend fun deleteTask(key: String)
}