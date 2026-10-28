package com.ivy.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ivy.desktop.data.BackupException
import com.ivy.desktop.data.BackupReader
import com.ivy.desktop.data.DesktopSettings
import com.ivy.desktop.data.Ledger
import com.ivy.desktop.data.LedgerBuilder
import com.ivy.desktop.reports.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

enum class Page(val title: String) {
    Overview("Overview"),
    Reports("Reports"),
    Transactions("Transactions"),
    Recurring("Recurring charges"),
}

sealed interface LoadState {
    data object NoFile : LoadState
    data object Loading : LoadState

    data class Ready(
        val file: File,
        /** When the phone saved this backup */
        val savedAt: LocalDateTime,
        val ledger: Ledger,
    ) : LoadState

    data class Failed(
        val file: File,
        val message: String,
    ) : LoadState
}

/** The whole window: pages on the left, the chosen page on the right. */
@Composable
fun DesktopApp(
    settings: DesktopSettings,
    modifier: Modifier = Modifier,
) {
    var file by remember { mutableStateOf(settings.backupFile()) }
    var state by remember { mutableStateOf<LoadState>(if (file == null) LoadState.NoFile else LoadState.Loading) }
    var page by remember { mutableStateOf(Page.Overview) }
    val chooseFile: () -> Unit = {
        chooseBackupFile(file)?.let { chosen ->
            settings.setBackupFile(chosen)
            state = LoadState.Loading
            file = chosen
        }
    }

    // reads the file, and again whenever the phone's next backup arrives
    LaunchedEffect(file) {
        val current = file ?: return@LaunchedEffect
        var loadedVersion: Long? = null
        while (true) {
            val version = current.lastModified()
            if (version != loadedVersion) {
                val result = withContext(Dispatchers.IO) { load(current) }
                val showing = state as? LoadState.Ready
                if (result is LoadState.Ready) {
                    state = result
                    loadedVersion = version
                } else if (showing?.file != current) {
                    state = result
                }
                // otherwise keep showing the last good copy: Google Drive may still be writing the new one
            }
            delay(CheckEvery.toMillis())
        }
    }

    val today = remember(state) { LocalDate.now() }
    AppContent(
        page = page,
        state = state,
        today = today,
        onPage = { page = it },
        onChooseFile = chooseFile,
        modifier = modifier,
    )
}

/** What the window shows for a [page] and [state]: pages on the left, the page on the right. */
@Composable
internal fun AppContent(
    page: Page,
    state: LoadState,
    today: LocalDate,
    onPage: (Page) -> Unit,
    onChooseFile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Sidebar(page = page, state = state, onPage = onPage, onChooseFile = onChooseFile)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            when (state) {
                LoadState.NoFile -> Welcome(onChooseFile = onChooseFile)
                LoadState.Loading -> Centered { Note("Reading your backup…") }
                is LoadState.Failed -> Centered {
                    Text(text = "Couldn't read ${state.file.name}", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Note(state.message)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onChooseFile) { Text("Choose another file") }
                }

                is LoadState.Ready -> when (page) {
                    Page.Overview -> OverviewPage(state.ledger, today)
                    Page.Reports -> ReportsPage(state.ledger, today)
                    Page.Transactions -> TransactionsPage(state.ledger, today)
                    Page.Recurring -> RecurringPage(state.ledger, today)
                }
            }
        }
    }
}

private fun load(file: File): LoadState = try {
    val ledger = LedgerBuilder.build(BackupReader.read(file), ZoneId.systemDefault(), Instant.now())
    val savedAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(file.lastModified()), ZoneId.systemDefault())
    LoadState.Ready(file, savedAt, ledger)
} catch (e: BackupException) {
    LoadState.Failed(file, e.message ?: "It isn't an Ivy Wallet backup.")
}

@Composable
private fun Sidebar(
    page: Page,
    state: LoadState,
    onPage: (Page) -> Unit,
    onChooseFile: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(SidebarWidth)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "Ivy Wallet UAE",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 24.dp),
        )
        Page.entries.forEach { item ->
            val selected = item == page
            Text(
                text = item.title,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    .clickable { onPage(item) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        BackupInfo(state, onChooseFile)
    }
}

/** Which backup is shown, how old it is, and a way to pick another file. */
@Composable
private fun BackupInfo(state: LoadState, onChooseFile: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 12.dp)) {
        when (state) {
            is LoadState.Ready -> {
                Note(state.file.name)
                val age = Duration.between(state.savedAt, LocalDateTime.now())
                Note(
                    text = "Saved ${Format.dateTime(state.savedAt)}",
                    color = if (age > StaleAfter) Orange else Color.Unspecified,
                )
                if (age > StaleAfter) {
                    Note(
                        text = "That's ${age.toDays()} days ago. Is automatic backup on in the phone app?",
                        color = Orange,
                    )
                }
            }

            is LoadState.Failed -> Note(state.file.name)
            LoadState.Loading, LoadState.NoFile -> Unit
        }
        Spacer(Modifier.height(8.dp))
        Chip(text = "Choose backup file", onClick = onChooseFile)
    }
}

/** First run: how to get the phone's backup onto this computer. */
@Composable
private fun Welcome(onChooseFile: () -> Unit) {
    Centered {
        Text(text = "Your Ivy Wallet reports on your computer", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(24.dp))
        listOf(
            "In the phone app, open Settings → Automatic backup. Choose Daily, and save the backup file " +
                "in your Google Drive.",
            "Install Google Drive for desktop on this computer, so the file is here too.",
            "Choose that file below. When the phone saves a new backup, this app shows it within a minute.",
        ).forEachIndexed { index, step ->
            Row(modifier = Modifier.padding(vertical = 6.dp)) {
                Text(
                    text = "${index + 1}.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(28.dp),
                )
                Text(text = step, style = MaterialTheme.typography.bodyLarge)
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onChooseFile) { Text("Choose backup file") }
        Spacer(Modifier.height(16.dp))
        Note("The app only reads the file. Nothing is sent anywhere.")
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(PagePadding),
        contentAlignment = Alignment.Center,
    ) {
        Column(modifier = Modifier.widthIn(max = WelcomeWidth)) {
            content()
        }
    }
}

private val SidebarWidth = 230.dp
private val WelcomeWidth = 640.dp
private val CheckEvery: Duration = Duration.ofSeconds(30)
private val StaleAfter: Duration = Duration.ofDays(2)
