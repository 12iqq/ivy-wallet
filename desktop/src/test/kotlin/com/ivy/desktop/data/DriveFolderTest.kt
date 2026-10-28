package com.ivy.desktop.data

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DriveFolderTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun backup(folder: File, name: String, modified: Long): File =
        File(folder, name).apply {
            parentFile.mkdirs()
            writeText("{}")
            setLastModified(modified)
        }

    @Test
    fun `the newest backup in My Drive on its own drive letter`() {
        val g = temp.newFolder("G")
        val folder = File(g, "My Drive/ivywallet-uae")
        backup(folder, "older.zip", 1_000_000)
        val newest = backup(folder, "ivywallet-uae.zip", 2_000_000)
        backup(folder, "notes.txt", 3_000_000)

        assertEquals(newest, DriveFolder.findBackup(roots = listOf(g), home = temp.newFolder("home")))
    }

    @Test
    fun `My Drive in another language, or mirrored into the user folder`() {
        val g = temp.newFolder("G")
        val home = temp.newFolder("home")
        backup(File(g, "Meine Ablage/ivywallet-uae"), "a.zip", 1_000_000)
        val mirrored = backup(File(home, "My Drive/ivywallet-uae"), "b.zip", 2_000_000)

        assertEquals(mirrored, DriveFolder.findBackup(roots = listOf(g), home = home))
        assertEquals(2, DriveFolder.folders(listOf(g), home).size)
    }

    @Test
    fun `no folder, no backup`() {
        assertNull(DriveFolder.findBackup(roots = listOf(temp.newFolder("C")), home = temp.newFolder("home")))
    }
}
