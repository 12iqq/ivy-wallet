package com.ivy.desktop.ui

import com.ivy.desktop.data.Ledger
import java.io.File
import java.time.LocalDateTime

/** Where the backup shown is */
sealed interface BackupPlace {
    /** A file on this computer: one chosen, or the newest in the ivywallet-uae folder */
    data class Computer(val file: File) : BackupPlace

    /** The file chosen with "Connect Google Drive" */
    data object GoogleDrive : BackupPlace
}

sealed interface LoadState {
    data object NoFile : LoadState
    data object Loading : LoadState

    /** "Connect Google Drive" waits for the person to sign in and choose their backup in the browser */
    data object Connecting : LoadState

    data class Ready(
        /** The backup's file name */
        val name: String,
        val place: BackupPlace,
        /** When the phone saved this backup */
        val savedAt: LocalDateTime,
        val ledger: Ledger,
        /** Why this may not be the newest backup, like Google Drive not answering */
        val problem: String?,
    ) : LoadState

    data class Failed(
        val name: String,
        val place: BackupPlace,
        val message: String,
    ) : LoadState
}
