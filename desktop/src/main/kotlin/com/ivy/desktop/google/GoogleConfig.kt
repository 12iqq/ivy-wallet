package com.ivy.desktop.google

import java.util.Properties

/**
 * The app's registration with Google, which CI builds into the app from the GOOGLE_* secrets
 * (docs/Google-Drive-setup.md). An app built without it has no "Connect Google Drive".
 */
class GoogleConfig(
    val clientId: String,
    /** Google gives desktop apps one too, though it can't stay secret in an app people download */
    val clientSecret: String,
    /** For Google's file picker */
    val apiKey: String,
    /** The Google Cloud project's number: a file chosen in the picker with it is one the app may open */
    val projectNumber: String,
) {
    companion object {
        /** Null when the app was built without it. */
        fun load(): GoogleConfig? = GoogleConfig::class.java.getResourceAsStream(Resource)?.use { stream ->
            val properties = Properties().apply { load(stream) }
            fun value(key: String): String = properties.getProperty(key).orEmpty().trim()
            GoogleConfig(
                clientId = value("GOOGLE_CLIENT_ID"),
                clientSecret = value("GOOGLE_CLIENT_SECRET"),
                apiKey = value("GOOGLE_API_KEY"),
                projectNumber = value("GOOGLE_PROJECT_NUMBER"),
            ).takeIf { it.clientId.isNotEmpty() && it.apiKey.isNotEmpty() && it.projectNumber.isNotEmpty() }
        }

        /** Written by the googleConfig task in build.gradle.kts */
        private const val Resource = "/google-drive.properties"
    }
}
