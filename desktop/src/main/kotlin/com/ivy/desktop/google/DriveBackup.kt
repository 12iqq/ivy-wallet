package com.ivy.desktop.google

import java.time.Clock
import java.time.Duration

/** The backup in Google Drive, and what's in it when it changed since the last look */
class DriveCheck(val file: DriveFile, val contents: ByteArray?)

/**
 * The backup chosen with "Connect Google Drive". [check] looks at it in Google Drive, and downloads it when it's
 * new. Each look needs an access token, which the refresh token gets again whenever the last one runs out.
 */
class DriveBackup(
    private val api: GoogleApi,
    private val connection: DriveConnection,
    private val clock: Clock = Clock.systemUTC(),
) {
    private var token: AccessToken? = null

    /** @param shown the backup shown now, if any: it's only downloaded again when it changed since */
    fun check(shown: DriveFile?): DriveCheck {
        val file = withToken { api.file(connection.file, it) }
        val changed = shown == null || file.modifiedAt != shown.modifiedAt || file.checksum != shown.checksum
        return DriveCheck(file, if (changed) withToken { api.download(connection.file, it) } else null)
    }

    /** Disconnects the app from Google Drive, even when this computer is offline */
    fun disconnect() {
        try {
            api.revoke(connection.refreshToken)
        } catch (ignored: GoogleException) {
            // then the access stays until it's removed in the Google account, or isn't used for six months
        }
    }

    /**
     * Calls Google with the access token, getting a new one first when there's none or it runs out soon, and once
     * more when Google says it doesn't work anymore.
     */
    private fun <T> withToken(call: (AccessToken) -> T): T {
        val current = token?.takeIf { it.expiresAt > clock.instant().plus(Margin) } ?: newToken()
        return try {
            call(current)
        } catch (e: GoogleException) {
            if (e.problem != GoogleProblem.AccessRemoved) throw e
            call(newToken())
        }
    }

    private fun newToken(): AccessToken = api.refresh(connection.refreshToken).also { token = it }

    private companion object {
        val Margin: Duration = Duration.ofMinutes(1)
    }
}
