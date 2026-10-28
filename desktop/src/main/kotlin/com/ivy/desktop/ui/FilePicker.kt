package com.ivy.desktop.ui

import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/** The system's own file picker, showing backup files (.zip, or a .json taken out of one). */
internal fun chooseBackupFile(current: File?): File? {
    val dialog = FileDialog(null as Frame?, "Choose your Ivy Wallet backup", FileDialog.LOAD)
    current?.parentFile?.let { dialog.directory = it.absolutePath }
    dialog.setFilenameFilter { _, name ->
        name.endsWith(".zip", ignoreCase = true) || name.endsWith(".json", ignoreCase = true)
    }
    // Windows ignores the filter above and filters by this pattern instead
    if (System.getProperty("os.name").orEmpty().startsWith("Windows")) dialog.file = "*.zip;*.json"
    dialog.isVisible = true
    val name = dialog.file ?: return null
    return File(dialog.directory, name)
}
