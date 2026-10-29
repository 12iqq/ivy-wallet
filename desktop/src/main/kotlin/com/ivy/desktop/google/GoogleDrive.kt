package com.ivy.desktop.google

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.IOException
import java.net.URI

/** Google Drive in the app, with its registration with Google */
class GoogleDrive(config: GoogleConfig) {
    private val api = GoogleApi(config)
    private val signIn = GoogleConnect(api, ::browse)

    /** "Connect Google Drive": the backup chosen in the browser, or null if none was */
    suspend fun connect(): DriveConnection? = withContext(Dispatchers.IO) { signIn.connect() }

    fun backup(connection: DriveConnection): DriveBackup = DriveBackup(api, connection)

    private fun browse(uri: URI) {
        try {
            Desktop.getDesktop().browse(uri)
        } catch (e: IOException) {
            throw GoogleException(GoogleProblem.Refused, "Couldn't open the browser", e)
        } catch (e: UnsupportedOperationException) {
            throw GoogleException(GoogleProblem.Refused, "Couldn't open the browser", e)
        }
    }
}
