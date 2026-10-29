package com.ivy.desktop.google

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection.HTTP_BAD_GATEWAY
import java.net.HttpURLConnection.HTTP_BAD_REQUEST
import java.net.HttpURLConnection.HTTP_OK
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** What "Connect Google Drive" gives: the backup chosen, and the refresh token that keeps the app signed in */
class DriveConnection(val refreshToken: String, val file: DriveFileId, val fileName: String)

/**
 * "Connect Google Drive". Opens Google's sign-in in the browser, where the person lets the app open the files they
 * choose in it, then chooses their backup in Google's file picker. Google sends the browser back to a small web
 * server on this computer with the answer: it only takes requests from this computer, at an address made up for
 * this sign-in.
 */
class GoogleConnect(
    private val api: GoogleApi,
    private val openBrowser: (URI) -> Unit,
) {
    /**
     * Waits until the backup is chosen. Null if they said no, closed the picker or took longer than [timeout].
     * @throws GoogleException when Google wouldn't finish the sign-in, or the backup chosen can't be opened
     */
    suspend fun connect(timeout: Duration = SignInTimeout): DriveConnection? {
        val server = HttpServer.create(InetSocketAddress(InetAddress.getByName(Loopback), 0), 0)
        val visit = Visit(server.address.port)
        server.createContext("/signed-in", visit::signedIn)
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

        /** Where Google sends the browser at the end, with a code for the tokens and the file chosen */
        fun signedIn(exchange: HttpExchange) {
            val query = query(exchange.requestURI.rawQuery)
            val code = query["code"]
            val picked = query["picked_file_ids"].orEmpty().split(',').firstOrNull { it.isNotBlank() }
            when {
                query["state"] != state ->
                    respond(exchange, HTTP_BAD_REQUEST, Pages.message("This page wasn't opened by Ivy Wallet UAE."))

                answer.isCompleted -> respond(exchange, HTTP_OK, Pages.message(Answered))
                code == null -> finish(exchange, NotAllowed, connection = null)
                picked == null -> finish(exchange, NoneChosen, connection = null)
                else -> try {
                    val tokens = api.exchange(code, redirect, verifier)
                    // the name to show, and a check that the app may open it
                    val file = api.file(DriveFileId(picked), tokens.access)
                    finish(exchange, opening(file.name), DriveConnection(tokens.refresh, file.id, file.name))
                } catch (e: GoogleException) {
                    respond(exchange, HTTP_BAD_GATEWAY, Pages.message("Couldn't connect Google Drive. ${e.message}."))
                    answer.completeExceptionally(e)
                }
            }
        }

        private fun finish(exchange: HttpExchange, message: String, connection: DriveConnection?) {
            respond(exchange, HTTP_OK, Pages.message(message))
            answer.complete(connection)
        }
    }

    private companion object {
        const val NotAllowed = "Ivy Wallet UAE wasn't allowed to open Google Drive. You can close this tab."
        const val NoneChosen = "No backup was chosen. You can close this tab, and connect Google Drive again from " +
            "Ivy Wallet UAE."
        const val Answered = "Ivy Wallet UAE already has your answer. You can close this tab."

        fun opening(name: String): String =
            "Done. Ivy Wallet UAE is opening $name now, and gets each new backup by itself. You can close this tab."
    }
}

private fun query(raw: String?): Map<String, String> = raw.orEmpty().split('&')
    .filter { it.isNotEmpty() }
    .associate { field ->
        val key = field.substringBefore('=')
        val value = field.substringAfter('=', missingDelimiterValue = "")
        URLDecoder.decode(key, Charsets.UTF_8) to URLDecoder.decode(value, Charsets.UTF_8)
    }

private fun respond(exchange: HttpExchange, status: Int, html: String) {
    exchange.responseHeaders.apply {
        add("Content-Type", "text/html; charset=utf-8")
        // the page's address has the sign-in's code in it
        add("Cache-Control", "no-store")
        add("Referrer-Policy", "no-referrer")
    }
    val body = html.toByteArray(Charsets.UTF_8)
    exchange.sendResponseHeaders(status, body.size.toLong())
    exchange.responseBody.write(body)
    exchange.close()
}

/** Google allows desktop apps to get the sign-in's answer at any port on this address */
private const val Loopback = "127.0.0.1"
private val SignInTimeout = 15.minutes
