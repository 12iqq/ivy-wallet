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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ivy.desktop.data.BackupException
import com.ivy.desktop.data.BackupReader
import com.ivy.desktop.data.DesktopSettings
import com.ivy.desktop.data.DriveFolder
import com.ivy.desktop.data.LedgerBuilder
import com.ivy.desktop.google.GoogleConfig
import com.ivy.desktop.google.GoogleDrive
import com.ivy.desktop.google.GoogleException
import com.ivy.desktop.reports.Format
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    Highlights("Highlights"),
    Transactions("Transactions"),
    Recurring("Recurring charges"),
}

/** What the buttons about the backup do */
@Immutable
class BackupActions(
    val chooseFile: () -> Unit,
    /** Null when the app was built without its registration with Google: then there's no Google Drive */
    val connectDrive: (() -> Unit)?,
    val disconnectDrive: () -> Unit,
    val cancelConnect: () -> Unit,
)

/** The whole window: pages on the left, the chosen page on the right. */
@Composable
fun DesktopApp(
    settings: DesktopSettings,
    modifier: Modifier = Modifier,
) {
    val google = remember { GoogleConfig.load()?.let(::GoogleDrive) }
    // a file chosen with "Choose another file"; without one, the newest backup in the ivywallet-uae folder
    var chosen by remember { mutableStateOf(settings.backupFile()) }
    // the backup chosen with "Connect Google Drive", which comes before both
    var drive by remember { mutableStateOf(google?.let { settings.driveConnection() }) }
    var state by remember { mutableStateOf<LoadState>(LoadState.Loading) }
    var page by remember { mutableStateOf(Page.Overview) }
    var connecting by remember { mutableStateOf<Job?>(null) }
    // why connecting Google Drive didn't work
    var notice by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val disconnect: () -> Unit = {
        val connection = drive
        if (connection != null && google != null) {
            scope.launch(Dispatchers.IO) { google.backup(connection).disconnect() }
        }
        settings.setDriveConnection(null)
        state = LoadState.Loading
        drive = null
    }
    val actions = BackupActions(
        chooseFile = {
            val shown = ((state as? LoadState.Ready)?.place as? BackupPlace.Computer)?.file ?: chosen
            chooseBackupFile(shown)?.let { file ->
                if (drive != null) disconnect()
                settings.setBackupFile(file)
                state = LoadState.Loading
                chosen = file
            }
        },
        connectDrive = google?.let { googleDrive ->
            {
                notice = null
                connecting = scope.launch {
                    try {
                        googleDrive.connect()?.let { connection ->
                            settings.setDriveConnection(connection)
                            state = LoadState.Loading
                            drive = connection
                        }
                    } catch (e: GoogleException) {
                        notice = "Couldn't connect Google Drive. ${e.message}."
                    } finally {
                        connecting = null
                    }
                }
            }
        },
        disconnectDrive = disconnect,
        cancelConnect = { connecting?.cancel() },
    )

    // reads the backup, and again whenever there's a newer one
    LaunchedEffect(chosen, drive) {
        val connection = drive
        if (connection != null && google != null) {
            val source = DriveSource(google.backup(connection), connection.fileName, ZoneId.systemDefault())
            while (true) {
                val shown = state
                state = withContext(Dispatchers.IO) { source.next(shown, Instant.now()) }
                delay(CheckDriveEvery.toMillis())
            }
        } else {
            watchFiles(chosen, current = { state }, show = { state = it })
        }
    }

    val today = remember(state) { LocalDate.now() }
    AppContent(
        page = page,
        state = if (connecting != null) LoadState.Connecting else state,
        today = today,
        actions = actions,
        onPage = { page = it },
        modifier = modifier,
        notice = notice,
    )
}

/** Reads the [chosen] file, or the newest backup in the ivywallet-uae folder, and again whenever it changes */
private suspend fun watchFiles(chosen: File?, current: () -> LoadState, show: (LoadState) -> Unit) {
    var loaded: Pair<File, Long>? = null
    while (true) {
        val found = withContext(Dispatchers.IO) {
            (chosen?.takeIf { it.isFile } ?: DriveFolder.findBackup())?.let { it to it.lastModified() }
        }
        if (found == null) {
            if (current() !is LoadState.Ready) show(LoadState.NoFile)
        } else if (found != loaded) {
            val result = withContext(Dispatchers.IO) { load(found.first) }
            if (result is LoadState.Ready) {
                show(result)
                loaded = found
            } else if ((current() as? LoadState.Ready)?.place != BackupPlace.Computer(found.first)) {
                show(result)
            }
            // otherwise keep showing the last good copy: Google Drive may still be writing the new one
        }
        delay(CheckEvery.toMillis())
    }
}

/** What the window shows for a [page] and [state]: pages on the left, the page on the right. */
@Composable
internal fun AppContent(
    page: Page,
    state: LoadState,
    today: LocalDate,
    actions: BackupActions,
    onPage: (Page) -> Unit,
    modifier: Modifier = Modifier,
    notice: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Sidebar(page = page, state = state, actions = actions, notice = notice, onPage = onPage)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            when (state) {
                LoadState.NoFile -> Welcome(actions = actions, notice = notice)
                LoadState.Loading -> Centered { Note("Looking for your backup…") }
                LoadState.Connecting -> ConnectingPage(onCancel = actions.cancelConnect)
                is LoadState.Failed -> FailedPage(state = state, actions = actions)
                is LoadState.Ready -> when (page) {
                    Page.Overview -> OverviewPage(state.ledger, today)
                    Page.Reports -> ReportsPage(state.ledger, today)
                    Page.Highlights -> HighlightsPage(state.ledger, today)
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
    LoadState.Ready(file.name, BackupPlace.Computer(file), savedAt, ledger, problem = null)
} catch (e: BackupException) {
    LoadState.Failed(file.name, BackupPlace.Computer(file), e.message ?: "It isn't an Ivy Wallet backup.")
}

@Composable
private fun Sidebar(
    page: Page,
    state: LoadState,
    actions: BackupActions,
    notice: String?,
    onPage: (Page) -> Unit,
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
        if (state != LoadState.Connecting) BackupInfo(state = state, actions = actions, notice = notice)
    }
}

/** Which backup is shown, where it's from, how old it is, and ways to choose another. */
@Composable
private fun BackupInfo(
    state: LoadState,
    actions: BackupActions,
    notice: String?,
) {
    Column(modifier = Modifier.padding(horizontal = 12.dp)) {
        when (state) {
            is LoadState.Ready -> ShownBackup(state)
            is LoadState.Failed -> Note(state.name)
            LoadState.Loading, LoadState.NoFile, LoadState.Connecting -> Unit
        }
        notice?.let { Note(text = it, color = Orange) }
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val place = (state as? LoadState.Ready)?.place ?: (state as? LoadState.Failed)?.place
            if (place == BackupPlace.GoogleDrive) {
                actions.connectDrive?.let { Chip(text = "Choose another backup", onClick = it) }
                Chip(text = "Disconnect Google Drive", onClick = actions.disconnectDrive)
            } else {
                actions.connectDrive?.let { Chip(text = "Connect Google Drive", onClick = it) }
                Chip(text = "Choose another file", onClick = actions.chooseFile)
            }
        }
    }
}

@Composable
private fun ShownBackup(state: LoadState.Ready) {
    Note(state.name)
    when (val place = state.place) {
        BackupPlace.GoogleDrive -> Note("In your Google Drive")
        is BackupPlace.Computer -> if (place.file.parentFile?.name.equals(DriveFolder.NAME, ignoreCase = true)) {
            Note("Found in your ${DriveFolder.NAME} folder")
        }
    }
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
    state.problem?.let { Note(text = it, color = Orange) }
}

/** First run: how to get the phone's backup onto this computer. */
@Composable
private fun Welcome(
    actions: BackupActions,
    notice: String?,
) {
    Centered {
        Text(text = "Your Ivy Wallet reports on your computer", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(24.dp))
        val connect = actions.connectDrive
        Steps(if (connect != null) DriveSteps else FolderSteps)
        Spacer(Modifier.height(16.dp))
        if (connect != null) {
            Button(onClick = connect) { Text("Connect Google Drive") }
            notice?.let {
                Spacer(Modifier.height(8.dp))
                Note(text = it, color = Orange)
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = actions.chooseFile) { Text("Or choose a backup file on this computer") }
            Spacer(Modifier.height(16.dp))
            Note(
                "Ivy Wallet UAE only sees the backup you choose, nothing else in your Google Drive, and only reads " +
                    "it. Nothing is sent anywhere.",
            )
        } else {
            Note(
                "There's no ${DriveFolder.NAME} folder on this computer yet. This page changes by itself once " +
                    "there is.",
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = actions.chooseFile) { Text("Or choose a backup file yourself") }
            Spacer(Modifier.height(16.dp))
            Note("The app only reads the file. Nothing is sent anywhere.")
        }
    }
}

@Composable
private fun Steps(steps: ImmutableList<String>) {
    steps.forEachIndexed { index, step ->
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
}

/** While the person signs in and chooses their backup in the browser */
@Composable
private fun ConnectingPage(onCancel: () -> Unit) {
    Centered {
        Text(text = "Connect Google Drive", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Your browser opened Google's sign-in. Sign in with the Google account your phone saves its " +
                "backup to, allow Ivy Wallet UAE to see the files you choose, then choose your backup.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(8.dp))
        Note("This page changes by itself when you're done.")
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onCancel) { Text("Cancel") }
    }
}

@Composable
private fun FailedPage(
    state: LoadState.Failed,
    actions: BackupActions,
) {
    Centered {
        val inDrive = state.place == BackupPlace.GoogleDrive
        Text(
            text = if (inDrive) "Couldn't open your backup in Google Drive" else "Couldn't read ${state.name}",
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(8.dp))
        Note(state.message)
        Spacer(Modifier.height(16.dp))
        val connect = actions.connectDrive
        if (inDrive && connect != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = connect) { Text("Connect Google Drive again") }
                OutlinedButton(onClick = actions.chooseFile) { Text("Choose a file on this computer") }
            }
        } else {
            Button(onClick = actions.chooseFile) { Text("Choose another file") }
        }
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

private val DriveSteps = persistentListOf(
    "In the phone app, open Settings → Automatic backup and choose Daily. When it asks where to save, choose " +
        "Google Drive.",
    "Click Connect Google Drive, sign in with the same Google account and choose that backup.",
    "That's all. This app gets each new backup from Google Drive by itself, within a minute of the phone " +
        "saving it.",
)

private val FolderSteps = persistentListOf(
    "In the phone app, open Settings → Automatic backup and choose Daily. When it asks where to save, " +
        "open Google Drive, make a folder called ${DriveFolder.NAME} and save the file in it.",
    "Install Google Drive for desktop on this computer and sign in with the same Google account.",
    "That's all. This app finds the newest backup in that folder by itself, and shows each new one " +
        "within a minute of it reaching this computer.",
)

private val SidebarWidth = 230.dp
private val WelcomeWidth = 640.dp
private val CheckEvery: Duration = Duration.ofSeconds(30)
private val CheckDriveEvery: Duration = Duration.ofMinutes(1)
private val StaleAfter: Duration = Duration.ofDays(2)
