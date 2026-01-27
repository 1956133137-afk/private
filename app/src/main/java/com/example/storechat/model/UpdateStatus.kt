package com.example.storechat.model

sealed class UpdateStatus {
    object LATEST : UpdateStatus()
    data class NEW_VERSION(
        val latestVersion: String,
        val latestVersionCode: Long,
        val downloadUrl: String?,
        val description: String?
    ) : UpdateStatus()
}
