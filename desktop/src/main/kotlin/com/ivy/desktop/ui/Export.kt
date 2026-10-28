package com.ivy.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.io.IOException
import java.time.Duration

/**
 * "Export CSV": asks where to save, writes [csv] there and says so for a few seconds. [fileName] is
 * what the save dialog suggests.
 */
@Composable
internal fun ExportButton(
    fileName: String,
    csv: () -> String,
    modifier: Modifier = Modifier,
) {
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(message) {
        if (message != null) {
            delay(MessageFor.toMillis())
            message = null
        }
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        message?.let { Note(it) }
        Hint(text = "Saves what this page shows as a CSV file, which Excel and Google Sheets open.") {
            Chip(
                text = "Export CSV",
                onClick = {
                    val file = chooseCsvFile(safeFileName(fileName)) ?: return@Chip
                    message = try {
                        file.writeText(csv(), Charsets.UTF_8)
                        "Saved ${file.name}"
                    } catch (e: IOException) {
                        "Couldn't save it: ${e.message}"
                    }
                },
            )
        }
    }
}

/** Without the characters Windows doesn't allow in file names. */
internal fun safeFileName(name: String): String = name.replace(Regex("[\\\\/:*?\"<>|]"), "-")

private val MessageFor: Duration = Duration.ofSeconds(6)
