package com.ivy.desktop.data

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream

class BackupException(message: String, cause: Throwable?) : Exception(message, cause)

/** Reads the backup the phone app saves: a .zip with one JSON file in it, or that JSON file on its own. */
object BackupReader {
    private val json = Json {
        ignoreUnknownKeys = true
        // the backup leaves out fields that have their default value
        explicitNulls = false
    }

    fun read(file: File): Backup = try {
        parse(file.readBytes())
    } catch (e: IOException) {
        throw BackupException("Couldn't open ${file.name}", e)
    }

    fun parse(bytes: ByteArray): Backup {
        val data = if (isZip(bytes)) jsonInZip(bytes) else bytes
        return try {
            json.decodeFromString(Backup.serializer(), text(data))
        } catch (e: SerializationException) {
            throw BackupException("This isn't an Ivy Wallet backup", e)
        } catch (e: IllegalArgumentException) {
            throw BackupException("This isn't an Ivy Wallet backup", e)
        }
    }

    private fun isZip(bytes: ByteArray): Boolean =
        bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()

    private fun jsonInZip(bytes: ByteArray): ByteArray {
        ZipInputStream(bytes.inputStream()).use { zip ->
            generateSequence { zip.nextEntry }
                .firstOrNull { !it.isDirectory && it.name.endsWith(".json", ignoreCase = true) }
                ?.let { return zip.readBytes() }
        }
        throw BackupException("There's no backup data in this zip", null)
    }

    /** The phone writes UTF-16 with a byte order mark. A JSON file saved some other way is usually UTF-8. */
    private fun text(bytes: ByteArray): String {
        val utf16 = bytes.size >= 2 && (
            (bytes[0] == BOM_HIGH && bytes[1] == BOM_LOW) || (bytes[0] == BOM_LOW && bytes[1] == BOM_HIGH)
            )
        return if (utf16) String(bytes, Charsets.UTF_16) else String(bytes, Charsets.UTF_8).removePrefix("\uFEFF")
    }

    private const val BOM_HIGH = 0xFE.toByte()
    private const val BOM_LOW = 0xFF.toByte()
}
