package com.ivy.desktop.data

import com.ivy.desktop.google.DriveConnection
import com.ivy.desktop.google.DriveFileId
import java.io.File
import java.util.prefs.Preferences

/** What the desktop app remembers between runs: the backup it reads. */
class DesktopSettings {
    private val prefs: Preferences = Preferences.userRoot().node("com/ivy/desktop")

    fun backupFile(): File? = prefs.get(BACKUP_FILE, null)?.let(::File)

    fun setBackupFile(file: File) {
        prefs.put(BACKUP_FILE, file.absolutePath)
    }

    /** The backup chosen with "Connect Google Drive", and the refresh token that keeps the app signed in */
    fun driveConnection(): DriveConnection? {
        val token = prefs.get(DRIVE_TOKEN, null)
        val file = prefs.get(DRIVE_FILE, null)
        return if (token != null && file != null) {
            DriveConnection(token, DriveFileId(file), prefs.get(DRIVE_FILE_NAME, null) ?: "Your backup")
        } else {
            null
        }
    }

    fun setDriveConnection(connection: DriveConnection?) {
        if (connection == null) {
            listOf(DRIVE_TOKEN, DRIVE_FILE, DRIVE_FILE_NAME).forEach(prefs::remove)
        } else {
            prefs.put(DRIVE_TOKEN, connection.refreshToken)
            prefs.put(DRIVE_FILE, connection.file.value)
            prefs.put(DRIVE_FILE_NAME, connection.fileName)
        }
        prefs.flush()
    }

    private companion object {
        const val BACKUP_FILE = "backupFile"
        const val DRIVE_TOKEN = "googleDriveToken"
        const val DRIVE_FILE = "googleDriveFile"
        const val DRIVE_FILE_NAME = "googleDriveFileName"
    }
}
