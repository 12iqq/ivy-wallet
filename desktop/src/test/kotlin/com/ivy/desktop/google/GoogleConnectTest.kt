package com.ivy.desktop.google

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/** "Connect Google Drive", with the test as the browser and [FakeGoogle] as Google */
class GoogleConnectTest {
    private val google = FakeGoogle()
    private val config = GoogleConfig("client-1", "secret-1", "key-1", "1234")
    private val api = GoogleApi(config, google.endpoints)
    private val browser = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()

    @After
    fun stop() {
        google.close()
    }

    @Test
    fun `sign in, then choose the backup in the picker`() {
        val pages = mutableListOf<String>()
        val connection = connect { signIn ->
            val choose = get(signIn.redirect + "?code=code-1&state=${signIn.state}&scope=drive.file")
            assertEquals(303, choose.statusCode())
            val picker = get(signIn.app + choose.headers().firstValue("Location").get())
            pages += picker.body()
            val answer = post(signIn.app + "/chosen/${signIn.state}", """{"id":"file-1","name":"ivywallet-uae.zip"}""")
            assertEquals(204, answer.statusCode())
        }

        assertNotNull(connection)
        assertEquals("refresh-1", connection.refreshToken)
        assertEquals(DriveFileId("file-1"), connection.file)
        assertEquals("ivywallet-uae.zip", connection.fileName)
        // the picker page has what Google's picker needs
        assertTrue(""""token":"access-1"""" in pages.single())
        assertTrue(""""apiKey":"key-1"""" in pages.single())
        assertTrue(""""appId":"1234"""" in pages.single())
        // the code went to Google with the verifier the sign-in's challenge came from
        val form = google.tokenForms.single()
        assertEquals("code-1", form["code"])
        assertEquals(Pkce.challenge(form.getValue("code_verifier")), lastSignIn?.challenge)
    }

    @Test
    fun `an answer for another sign-in is ignored`() {
        val connection = connect { signIn ->
            assertEquals(400, get(signIn.redirect + "?code=code-1&state=someone-else").statusCode())
            get(signIn.redirect + "?code=code-1&state=${signIn.state}")
            post(signIn.app + "/chosen/${signIn.state}", """{"id":"file-1","name":"ivywallet-uae.zip"}""")
        }

        assertEquals(DriveFileId("file-1"), connection?.file)
        assertEquals(1, google.tokenForms.size)
    }

    @Test
    fun `saying no to Google gives nothing`() {
        val connection = connect { signIn ->
            assertEquals(200, get(signIn.redirect + "?error=access_denied&state=${signIn.state}").statusCode())
        }

        assertNull(connection)
        assertTrue(google.tokenForms.isEmpty())
    }

    @Test
    fun `closing the picker gives nothing`() {
        val connection = connect { signIn ->
            get(signIn.redirect + "?code=code-1&state=${signIn.state}")
            post(signIn.app + "/chosen/${signIn.state}", "{}")
        }

        assertNull(connection)
    }

    @Test
    fun `no answer in time gives nothing`() {
        val connection = runBlocking {
            GoogleConnect(config, api) { }.connect(timeout = 300.milliseconds)
        }

        assertNull(connection)
    }

    @Test
    fun `Google not finishing the sign-in is an error`() {
        google.tokenAnswer = 400 to """{"error":"invalid_grant"}"""

        val e = assertFailsWith<GoogleException> {
            connect { signIn -> get(signIn.redirect + "?code=code-1&state=${signIn.state}") }
        }
        assertEquals(GoogleProblem.AccessRemoved, e.problem)
    }

    /** What the app opened in the browser */
    private class SignIn(url: URI) {
        private val query = FakeGoogle.form(url.rawQuery)
        val redirect: String = query.getValue("redirect_uri")
        val state: String = query.getValue("state")
        val challenge: String = query.getValue("code_challenge")

        /** The app's own small web server */
        val app: String = redirect.substringBeforeLast('/')
    }

    private var lastSignIn: SignIn? = null

    /** Connects, with [browser] doing what the person would in the browser the app opened */
    private fun connect(browser: (SignIn) -> Unit): DriveConnection? = runBlocking {
        var failure: Throwable? = null
        val connection = GoogleConnect(config, api) { url ->
            val signIn = SignIn(url).also { lastSignIn = it }
            thread {
                try {
                    browser(signIn)
                } catch (e: AssertionError) {
                    failure = e
                }
            }
        }.connect(timeout = 1.minutes)
        failure?.let { throw it }
        connection
    }

    private fun get(url: String): HttpResponse<String> =
        browser.send(HttpRequest.newBuilder(URI(url)).GET().build(), HttpResponse.BodyHandlers.ofString())

    private fun post(url: String, body: String): HttpResponse<String> = browser.send(
        HttpRequest.newBuilder(URI(url)).POST(HttpRequest.BodyPublishers.ofString(body)).build(),
        HttpResponse.BodyHandlers.ofString(),
    )
}
