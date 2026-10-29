package com.ivy.desktop.google

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.util.Collections

/** Google's sign-in and Google Drive, played by a server on this computer for the tests */
class FakeGoogle : AutoCloseable {
    private val server = HttpServer.create(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0)
    val base = "http://127.0.0.1:${server.address.port}"
    val endpoints = GoogleEndpoints(
        signIn = "$base/auth",
        token = "$base/token",
        revoke = "$base/revoke",
        drive = "$base/drive/v3",
    )

    /** The forms sent to the token address, oldest first */
    val tokenForms: MutableList<Map<String, String>> = Collections.synchronizedList(mutableListOf())
    val revoked: MutableList<String> = Collections.synchronizedList(mutableListOf())

    /** The Authorization header of each Google Drive request */
    val driveAuthorizations: MutableList<String?> = Collections.synchronizedList(mutableListOf())

    @Volatile
    var tokenAnswer: Pair<Int, String> = OK to """{"access_token":"access-1","expires_in":3599,""" +
        """"refresh_token":"refresh-1","scope":"${GoogleApi.DriveFileScope}","token_type":"Bearer"}"""

    @Volatile
    var file = DriveFileJson(
        id = "file-1",
        name = "ivywallet-uae.zip",
        modifiedTime = "2026-10-28T06:10:00.000Z",
        checksum = "c1",
        trashed = false,
    )

    @Volatile
    var contents: ByteArray = "the backup".toByteArray()

    /** Answers Google Drive requests with this status instead, this many times */
    @Volatile
    var driveFailure: Pair<Int, Int>? = null

    @Volatile
    var downloads = 0

    init {
        server.createContext("/token") { exchange ->
            tokenForms += form(exchange.requestBody.readBytes().decodeToString())
            val (status, body) = tokenAnswer
            reply(exchange, status, body)
        }
        server.createContext("/revoke") { exchange ->
            revoked += form(exchange.requestBody.readBytes().decodeToString()).getValue("token")
            reply(exchange, OK, "")
        }
        server.createContext("/drive/v3/files/") { exchange -> drive(exchange) }
        server.start()
    }

    private fun drive(exchange: HttpExchange) {
        driveAuthorizations += exchange.requestHeaders.getFirst("Authorization")
        val failure = driveFailure
        if (failure != null) {
            val (status, times) = failure
            driveFailure = if (times > 1) status to times - 1 else null
            reply(exchange, status, """{"error":{"code":$status,"message":"no"}}""")
            return
        }
        val id = exchange.requestURI.path.substringAfterLast('/')
        val query = form(exchange.requestURI.rawQuery.orEmpty())
        when {
            id != file.id -> reply(exchange, NOT_FOUND, """{"error":{"code":404,"message":"File not found"}}""")
            query["alt"] == "media" -> {
                downloads++
                reply(exchange, OK, contents)
            }

            else -> reply(exchange, OK, file.json())
        }
    }

    override fun close() {
        server.stop(0)
    }

    class DriveFileJson(
        val id: String,
        val name: String,
        val modifiedTime: String,
        val checksum: String,
        val trashed: Boolean,
    ) {
        fun json(): String = """{"id":"$id","name":"$name","modifiedTime":"$modifiedTime",""" +
            """"md5Checksum":"$checksum","trashed":$trashed,"mimeType":"application/zip"}"""
    }

    companion object {
        const val OK = 200
        const val NOT_FOUND = 404

        fun form(text: String): Map<String, String> = text.split('&').filter { it.isNotEmpty() }.associate {
            URLDecoder.decode(it.substringBefore('='), Charsets.UTF_8) to
                URLDecoder.decode(it.substringAfter('=', ""), Charsets.UTF_8)
        }

        private fun reply(exchange: HttpExchange, status: Int, body: String) {
            reply(exchange, status, body.toByteArray())
        }

        private fun reply(exchange: HttpExchange, status: Int, body: ByteArray) {
            exchange.sendResponseHeaders(status, if (body.isEmpty()) -1 else body.size.toLong())
            if (body.isNotEmpty()) exchange.responseBody.write(body)
            exchange.close()
        }
    }
}
