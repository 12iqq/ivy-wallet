package com.ivy.desktop.google

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection.HTTP_FORBIDDEN
import java.net.HttpURLConnection.HTTP_NOT_FOUND
import java.net.HttpURLConnection.HTTP_OK
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeParseException

/** Why talking to Google didn't work */
enum class GoogleProblem {
    /** No internet, or Google didn't answer */
    Offline,

    /** The app may not open Google Drive anymore: disconnected, or access removed in the Google account */
    AccessRemoved,

    /** The backup isn't in Google Drive anymore, or the app may not open it */
    FileGone,

    /** Google said no for another reason */
    Refused,
}

class GoogleException(val problem: GoogleProblem, message: String, cause: Throwable? = null) :
    Exception(message, cause)

/** Where Google's services are. Tests point these at a server on this computer. */
class GoogleEndpoints(
    val signIn: String,
    val token: String,
    val revoke: String,
    val drive: String,
) {
    companion object {
        val Google = GoogleEndpoints(
            signIn = "https://accounts.google.com/o/oauth2/v2/auth",
            token = "https://oauth2.googleapis.com/token",
            revoke = "https://oauth2.googleapis.com/revoke",
            drive = "https://www.googleapis.com/drive/v3",
        )
    }
}

/** Lets the app use Google Drive for about an hour */
class AccessToken(val value: String, val expiresAt: Instant)

/** What signing in gives: an access token, and a refresh token that gets new ones for as long as it's allowed */
class Tokens(val access: AccessToken, val refresh: String)

@JvmInline
value class DriveFileId(val value: String)

/** A file in Google Drive, as much as the app needs to know about it */
data class DriveFile(
    val id: DriveFileId,
    val name: String,
    val modifiedAt: Instant,
    /** Changes with what's in the file. Google Drive has one for files it stores, like the backup. */
    val checksum: String?,
)

/**
 * The parts of Google's services the app uses: signing in, with access only to the files chosen in the app (the
 * drive.file scope), and reading those files.
 */
class GoogleApi(
    private val config: GoogleConfig,
    private val endpoints: GoogleEndpoints = GoogleEndpoints.Google,
    private val clock: Clock = Clock.systemUTC(),
    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Timeout)
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build(),
) {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    /** Google's sign-in page, which sends the person back to [redirect] with a code for [exchange] */
    fun signInUrl(redirect: String, state: String, challenge: String): String = endpoints.signIn + "?" + form(
        "client_id" to config.clientId,
        "redirect_uri" to redirect,
        "response_type" to "code",
        "scope" to DriveFileScope,
        "code_challenge" to challenge,
        "code_challenge_method" to "S256",
        "state" to state,
        // asks for a refresh token too, so the app stays signed in
        "access_type" to "offline",
        "prompt" to "consent",
    )

    fun exchange(code: String, redirect: String, verifier: String): Tokens {
        val response = token(
            "grant_type" to "authorization_code",
            "code" to code,
            "redirect_uri" to redirect,
            "code_verifier" to verifier,
        )
        val refresh = response.refreshToken
            ?: throw GoogleException(GoogleProblem.Refused, "Google didn't let Ivy Wallet UAE stay signed in")
        return Tokens(accessToken(response), refresh)
    }

    fun refresh(refreshToken: String): AccessToken =
        accessToken(token("grant_type" to "refresh_token", "refresh_token" to refreshToken))

    /** Takes away the app's access, as if it was removed in the Google account */
    fun revoke(token: String) {
        val request = HttpRequest.newBuilder(URI(endpoints.revoke))
            .timeout(Timeout)
            .header("Content-Type", FormType)
            .POST(HttpRequest.BodyPublishers.ofString(form("token" to token)))
            .build()
        send(request)
    }

    fun file(id: DriveFileId, token: AccessToken): DriveFile {
        val fields = form("fields" to "id,name,modifiedTime,md5Checksum,trashed", "supportsAllDrives" to "true")
        val file = parse(FileResponse.serializer(), get("${endpoints.drive}/files/${encode(id.value)}?$fields", token))
        if (file.trashed == true) {
            throw GoogleException(GoogleProblem.FileGone, "${file.name} is in the bin in Google Drive")
        }
        val modifiedAt = try {
            Instant.parse(file.modifiedTime)
        } catch (e: DateTimeParseException) {
            throw GoogleException(GoogleProblem.Refused, "Google Drive gave an odd date for ${file.name}", e)
        }
        return DriveFile(DriveFileId(file.id), file.name, modifiedAt, file.md5Checksum)
    }

    fun download(id: DriveFileId, token: AccessToken): ByteArray {
        val media = form("alt" to "media", "supportsAllDrives" to "true")
        return get("${endpoints.drive}/files/${encode(id.value)}?$media", token)
    }

    private fun token(vararg fields: Pair<String, String>): TokenResponse {
        val client = listOf("client_id" to config.clientId) +
            listOfNotNull(config.clientSecret.takeIf { it.isNotEmpty() }?.let { "client_secret" to it })
        val request = HttpRequest.newBuilder(URI(endpoints.token))
            .timeout(Timeout)
            .header("Content-Type", FormType)
            .POST(HttpRequest.BodyPublishers.ofString(form(client + fields)))
            .build()
        val response = send(request)
        if (response.statusCode() != HTTP_OK) {
            val error = runCatching { parse(TokenError.serializer(), response.body()).error }.getOrNull()
            // invalid_grant: signed out, or the access was removed, so signing in again is the only way back
            val problem = if (error == "invalid_grant") GoogleProblem.AccessRemoved else GoogleProblem.Refused
            throw GoogleException(problem, "Google said no to signing in (${response.statusCode()} $error)")
        }
        return parse(TokenResponse.serializer(), response.body())
    }

    private fun accessToken(response: TokenResponse) =
        AccessToken(response.accessToken, clock.instant().plusSeconds(response.expiresIn))

    private fun get(url: String, token: AccessToken): ByteArray {
        val request = HttpRequest.newBuilder(URI(url))
            .timeout(DownloadTimeout)
            .header("Authorization", "Bearer ${token.value}")
            .GET()
            .build()
        val response = send(request)
        if (response.statusCode() == HTTP_OK) return response.body()
        val problem = when (response.statusCode()) {
            HTTP_UNAUTHORIZED -> GoogleProblem.AccessRemoved
            HTTP_NOT_FOUND -> GoogleProblem.FileGone
            else -> GoogleProblem.Refused
        }
        val detail = if (response.statusCode() == HTTP_FORBIDDEN) "not allowed" else "${response.statusCode()}"
        throw GoogleException(problem, "Google Drive said no ($detail)")
    }

    private fun send(request: HttpRequest): HttpResponse<ByteArray> = try {
        http.send(request, HttpResponse.BodyHandlers.ofByteArray())
    } catch (e: IOException) {
        throw GoogleException(GoogleProblem.Offline, "Couldn't reach Google", e)
    }

    private fun <T> parse(serializer: KSerializer<T>, body: ByteArray): T = try {
        json.decodeFromString(serializer, body.decodeToString())
    } catch (e: SerializationException) {
        throw GoogleException(GoogleProblem.Refused, "Google gave an answer Ivy Wallet UAE doesn't understand", e)
    } catch (e: IllegalArgumentException) {
        throw GoogleException(GoogleProblem.Refused, "Google gave an answer Ivy Wallet UAE doesn't understand", e)
    }

    @Serializable
    private class TokenResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("expires_in") val expiresIn: Long,
        @SerialName("refresh_token") val refreshToken: String?,
    )

    @Serializable
    private class TokenError(val error: String?)

    @Serializable
    private class FileResponse(
        val id: String,
        val name: String,
        val modifiedTime: String,
        val md5Checksum: String?,
        val trashed: Boolean?,
    )

    companion object {
        /** Only the files chosen in the app: the backup, and nothing else in Google Drive */
        const val DriveFileScope = "https://www.googleapis.com/auth/drive.file"
    }
}

internal fun form(vararg fields: Pair<String, String>): String = form(fields.asList())

internal fun form(fields: List<Pair<String, String>>): String =
    fields.joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" }

private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8)

private const val FormType = "application/x-www-form-urlencoded"
private val Timeout: Duration = Duration.ofSeconds(30)

// the backup can be a few megabytes on a slow connection
private val DownloadTimeout: Duration = Duration.ofMinutes(5)
