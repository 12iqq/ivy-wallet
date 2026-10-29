package com.ivy.desktop.google

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection.HTTP_BAD_GATEWAY
import java.net.HttpURLConnection.HTTP_BAD_METHOD
import java.net.HttpURLConnection.HTTP_BAD_REQUEST
import java.net.HttpURLConnection.HTTP_CONFLICT
import java.net.HttpURLConnection.HTTP_NO_CONTENT
import java.net.HttpURLConnection.HTTP_OK
import java.net.HttpURLConnection.HTTP_SEE_OTHER
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** What "Connect Google Drive" gives: the backup chosen, and the refresh token that keeps the app signed in */
class DriveConnection(val refreshToken: String, val file: DriveFileId, val fileName: String)

/**
 * "Connect Google Drive". Opens Google's sign-in in the browser, where the person lets the app open the files they
 * choose in it, then a page with Google's file picker, to choose their backup. A small web server on this computer
 * gets both answers: it only takes requests from this computer, at addresses made up for this sign-in.
 */
class GoogleConnect(
    private val config: GoogleConfig,
    private val api: GoogleApi,
    private val openBrowser: (URI) -> Unit,
) {
    /**
     * Waits until the backup is chosen. Null if they said no, closed the picker or took longer than [timeout].
     * @throws GoogleException when Google wouldn't finish the sign-in
     */
    suspend fun connect(timeout: Duration = SignInTimeout): DriveConnection? {
        val server = HttpServer.create(InetSocketAddress(InetAddress.getByName(Loopback), 0), 0)
        val visit = Visit(server.address.port)
        visit.listen(server)
        server.start()
        try {
            openBrowser(URI(api.signInUrl(visit.redirect, visit.state, Pkce.challenge(visit.verifier))))
            return withTimeoutOrNull(timeout) { visit.answer.await() }
        } finally {
            server.stop(0)
        }
    }

    /** One sign-in, from opening the browser to the backup chosen */
    private inner class Visit(port: Int) {
        val state = Pkce.verifier()
        val verifier = Pkce.verifier()
        val redirect = "http://$Loopback:$port/signed-in"
        val answer = CompletableDeferred<DriveConnection?>()
        private val tokens = AtomicReference<Tokens?>()

        fun listen(server: HttpServer) {
            server.createContext("/signed-in", ::signedIn)
            server.createContext("/choose/$state", ::choose)
            server.createContext("/chosen/$state", ::chosen)
        }

        /** Where Google sends the browser after the sign-in, with a code for the tokens */
        private fun signedIn(exchange: HttpExchange) {
            val query = query(exchange.requestURI.rawQuery)
            val code = query["code"]
            when {
                query["state"] != state ->
                    respond(exchange, HTTP_BAD_REQUEST, Pages.message("This page wasn't opened by Ivy Wallet UAE."))

                code == null -> {
                    respond(exchange, HTTP_OK, Pages.message(NotAllowed))
                    answer.complete(null)
                }

                else -> try {
                    tokens.set(api.exchange(code, redirect, verifier))
                    exchange.responseHeaders.add("Location", "/choose/$state")
                    respond(exchange, HTTP_SEE_OTHER, null)
                } catch (e: GoogleException) {
                    respond(exchange, HTTP_BAD_GATEWAY, Pages.message("Couldn't finish signing in. ${e.message}."))
                    answer.completeExceptionally(e)
                }
            }
        }

        /** The page with Google's file picker */
        private fun choose(exchange: HttpExchange) {
            val access = tokens.get()?.access
            if (access == null) {
                respond(exchange, HTTP_CONFLICT, Pages.message("Sign in with Google first, from Ivy Wallet UAE."))
            } else {
                respond(exchange, HTTP_OK, Pages.picker(config, access, "/chosen/$state"))
            }
        }

        /** What the picker page sends back: the file chosen, or nothing when the picker was closed */
        private fun chosen(exchange: HttpExchange) {
            if (exchange.requestMethod != "POST") {
                respond(exchange, HTTP_BAD_METHOD, null)
                return
            }
            val picked = try {
                json.decodeFromString(Picked.serializer(), exchange.requestBody.readBytes().decodeToString())
            } catch (ignored: SerializationException) {
                // not a file: the picker was closed
                null
            }
            respond(exchange, HTTP_NO_CONTENT, null)
            val id = picked?.id
            val refresh = tokens.get()?.refresh
            answer.complete(
                if (id != null && refresh != null) {
                    DriveConnection(refresh, DriveFileId(id), picked.name ?: "Your backup")
                } else {
                    null
                },
            )
        }
    }

    @Serializable
    private class Picked(val id: String?, val name: String?)

    private companion object {
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

        const val NotAllowed = "Ivy Wallet UAE wasn't allowed to open Google Drive. You can close this tab."
    }
}

private fun query(raw: String?): Map<String, String> = raw.orEmpty().split('&')
    .filter { it.isNotEmpty() }
    .associate { field ->
        val key = field.substringBefore('=')
        val value = field.substringAfter('=', missingDelimiterValue = "")
        URLDecoder.decode(key, Charsets.UTF_8) to URLDecoder.decode(value, Charsets.UTF_8)
    }

private fun respond(exchange: HttpExchange, status: Int, html: String?) {
    exchange.responseHeaders.apply {
        if (html != null) add("Content-Type", "text/html; charset=utf-8")
        // the picker page has the access token in it
        add("Cache-Control", "no-store")
        add("Referrer-Policy", "no-referrer")
    }
    val body = html?.toByteArray(Charsets.UTF_8)
    exchange.sendResponseHeaders(status, body?.size?.toLong() ?: NoBody)
    body?.let(exchange.responseBody::write)
    exchange.close()
}

/** Google allows desktop apps to get the sign-in's answer at any port on this address */
private const val Loopback = "127.0.0.1"
private const val NoBody = -1L
private val SignInTimeout = 15.minutes
