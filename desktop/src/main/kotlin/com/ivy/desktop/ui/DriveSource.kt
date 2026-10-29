package com.ivy.desktop.ui

import com.ivy.desktop.data.BackupException
import com.ivy.desktop.data.BackupReader
import com.ivy.desktop.data.LedgerBuilder
import com.ivy.desktop.google.DriveBackup
import com.ivy.desktop.google.DriveFile
import com.ivy.desktop.google.GoogleException
import com.ivy.desktop.google.GoogleProblem
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Shows the backup chosen in Google Drive, and the next one whenever the phone saves it there */
internal class DriveSource(
    private val backup: DriveBackup,
    /** The backup's name when it was chosen, for when Google Drive can't be reached */
    private val fileName: String,
    private val zone: ZoneId,
) {
    private var shown: DriveFile? = null

    /** What to show after another look at Google Drive, given what's shown now */
    fun next(current: LoadState, now: Instant): LoadState = try {
        val check = backup.check(shown)
        val contents = check.contents
        when {
            contents != null -> read(check.file, contents, current, now)
            current is LoadState.Ready && current.problem != null -> current.copy(problem = null)
            else -> current
        }
    } catch (e: GoogleException) {
        problem(e, current as? LoadState.Ready)
    }

    private fun read(file: DriveFile, contents: ByteArray, current: LoadState, now: Instant): LoadState {
        // not again until it changes, even when it can't be read
        shown = file
        return try {
            val ledger = LedgerBuilder.build(BackupReader.parse(contents), zone, now)
            val savedAt = LocalDateTime.ofInstant(file.modifiedAt, zone)
            LoadState.Ready(file.name, BackupPlace.GoogleDrive, savedAt, ledger, problem = null)
        } catch (e: BackupException) {
            val message = e.message ?: "It isn't an Ivy Wallet backup."
            if (current is LoadState.Ready) {
                current.copy(problem = "The newest backup in Google Drive couldn't be read. $message.")
            } else {
                LoadState.Failed(file.name, BackupPlace.GoogleDrive, message)
            }
        }
    }

    private fun problem(e: GoogleException, ready: LoadState.Ready?): LoadState = when (e.problem) {
        GoogleProblem.Offline -> ready?.copy(problem = "Couldn't reach Google Drive, so there may be a newer backup.")
            ?: failed("Couldn't reach Google Drive. Is this computer online? It tries again every minute.")

        GoogleProblem.AccessRemoved ->
            failed("Ivy Wallet UAE may not open your Google Drive anymore. Connect it again to see your backup.")

        GoogleProblem.FileGone ->
            failed("$fileName isn't in your Google Drive anymore, or it's in the bin. Choose your backup again.")

        GoogleProblem.Refused -> ready?.copy(problem = "Google Drive didn't send the newest backup. ${e.message}.")
            ?: failed("Google Drive didn't send your backup. ${e.message}.")
    }

    private fun failed(message: String): LoadState = LoadState.Failed(fileName, BackupPlace.GoogleDrive, message)
}
