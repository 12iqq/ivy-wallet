package com.ivy.desktop.data

import java.io.File
import java.util.prefs.Preferences

/** What the desktop app remembers between runs: the backup file it reads. */
class DesktopSettings {
    private val prefs: Preferences = Preferences.userRoot().node("com/ivy/desktop")

    fun backupFile(): File? = prefs.get(BACKUP_FILE, null)?.let(::File)

    fun setBackupFile(file: File) {
        prefs.put(BACKUP_FILE, file.absolutePath)
    }

    private companion object {
        const val BACKUP_FILE = "backupFile"
    }
}
