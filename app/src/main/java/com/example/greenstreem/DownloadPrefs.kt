package com.example.greenstreem

import android.os.Environment

object DownloadPrefs {
    const val PREFS_NAME = "iptv_prefs"

    const val KEY_LOCATION = "download_location"
    const val LOCATION_DOWNLOADS = "downloads"
    const val LOCATION_MOVIES = "movies"

    const val KEY_SUBFOLDER = "download_subfolder"
    const val DEFAULT_SUBFOLDER = "GreenStreem"

    const val KEY_SMB_SERVER = "download_smb_server"
    const val KEY_SMB_SHARE = "download_smb_share"
    const val KEY_SMB_FOLDER = "download_smb_folder"
    const val KEY_SMB_USERNAME = "download_smb_username"
    const val KEY_SMB_PASSWORD = "download_smb_password"

    const val KEY_DOWNLOAD_STATUS = "download_status"
    const val KEY_DOWNLOAD_TITLE = "download_title"
    const val KEY_DOWNLOAD_TARGET = "download_target"
    const val KEY_DOWNLOAD_PROGRESS = "download_progress"
    const val KEY_DOWNLOAD_UPDATED_AT = "download_updated_at"
    const val STATUS_IDLE = "idle"
    const val STATUS_RUNNING = "running"
    const val STATUS_DONE = "done"
    const val STATUS_FAILED = "failed"

    fun publicDirectory(location: String): String {
        return when (location) {
            LOCATION_MOVIES -> Environment.DIRECTORY_MOVIES
            else -> Environment.DIRECTORY_DOWNLOADS
        }
    }

    fun locationLabel(location: String): String {
        return when (location) {
            LOCATION_MOVIES -> "Movies folder"
            else -> "Downloads folder"
        }
    }

    fun cleanPathSegment(value: String): String {
        return value
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .trim()
            .take(60)
    }

    fun joinPath(parent: String, child: String): String {
        return listOf(parent.trim('/'), child.trim('/'))
            .filter { it.isNotBlank() }
            .joinToString("/")
    }
}
