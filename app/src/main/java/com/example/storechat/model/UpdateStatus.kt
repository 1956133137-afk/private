package com.example.storechat.model

sealed class UpdateStatus {
    object LATEST : UpdateStatus()
    data class NEW_VERSION(val latestVersion: String) : UpdateStatus()
}
