package com.ivy.desktop.data

import java.io.File

/**
 * Finds the phone's backup without asking: the newest backup in a folder called [NAME] in Google
 * Drive, which Google Drive for desktop puts on this computer. That's "My Drive" on its own drive
 * letter (G: unless that's taken), or a "My Drive" folder in your user folder when it mirrors files.
 */
object DriveFolder {
    const val NAME = "ivywallet-uae"

    /** The newest .zip or .json in any [NAME] folder found, or null when there's none. */
    fun findBackup(
        roots: List<File> = File.listRoots().orEmpty().toList(),
        home: File = File(System.getProperty("user.home")),
    ): File? = folders(roots, home)
        .flatMap { folder -> folder.listFiles().orEmpty().filter(::isBackup) }
        .maxByOrNull { it.lastModified() }

    /** The [NAME] folders that exist. */
    fun folders(roots: List<File>, home: File): List<File> {
        // "My Drive" has another name in other languages, so every folder at the top of a drive counts
        val onDrives = roots.flatMap { root -> listOf(root) + root.listFiles().orEmpty().filter { it.isDirectory } }
        val inHome = listOf(File(home, "My Drive"), File(home, "Google Drive"), File(home, "Google Drive/My Drive"))
        return (onDrives + inHome)
            .map { File(it, NAME) }
            .filter { it.isDirectory }
            .distinctBy { it.absolutePath }
    }

    private fun isBackup(file: File): Boolean =
        file.isFile && (file.name.endsWith(".zip", ignoreCase = true) || file.name.endsWith(".json", ignoreCase = true))
}
