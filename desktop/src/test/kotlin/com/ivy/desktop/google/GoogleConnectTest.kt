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
    private val api = GoogleApi(GoogleConfig("client-1", "secret-1"), google.endpoints)
    private val browser = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()

    @After
    fun stop() {
        google.close()
    }

    @Test
    fun `sign in, and choose the backup in Google's picker`() {
        val pages = mutableListOf<String>()
        val connection = connect { signIn ->
            // where Google's picker sends the browser
            val answer = "?picked_file_ids=file-1&code=code-1&scope=drive.file&state=${signIn.state}"
            val done = get(signIn.redirect + answer)
            assertEquals(200, done.statusCode())
            pages += done.body()
        }

        assertNotNull(connection)
        assertEquals("refresh-1", connection.refreshToken)
        assertEquals(DriveFileId("file-1"), connection.file)
        assertEquals("ivywallet-uae.zip", connection.fileName)
        assertTrue("opening ivywallet-uae.zip now" in pages.single())
        // the code went to Google with the verifier the sign-in's challenge came from
        val form = google.tokenForms.single()
        assertEquals("code-1", form["code"])
        assertEquals(Pkce.challenge(form.getValue("code_verifier")), lastSignIn?.challenge)
        // and the file was looked at with the access token that gave
        assertEquals(listOf("Bearer access-1"), google.driveAuthorizations.toList())
    }

    @Test
    fun `an answer for another sign-in is ignored`() {
        val connection = connect { signIn ->
            val other = get(signIn.redirect + "?picked_file_ids=file-1&code=code-1&state=someone-else")
            assertEquals(400, other.statusCode())
            get(signIn.redirect + "?picked_file_ids=file-1&code=code-1&state=${signIn.state}")
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
            assertEquals(200, get(signIn.redirect + "?code=code-1&state=${signIn.state}").statusCode())
        }

        assertNull(connection)
        // no backup, so no need to sign in
        assertTrue(google.tokenForms.isEmpty())
    }

    @Test
    fun `no answer in time gives nothing`() {
        val connection = runBlocking {
            GoogleConnect(api) { }.connect(timeout = 300.milliseconds)
        }

        assertNull(connection)
    }

    @Test
    fun `Google not finishing the sign-in is an error`() {
        google.tokenAnswer = 400 to """{"error":"invalid_grant"}"""

        val e = assertFailsWith<GoogleException> {
            connect { signIn -> get(signIn.redirect + "?picked_file_ids=file-1&code=code-1&state=${signIn.state}") }
        }
        assertEquals(GoogleProblem.AccessRemoved, e.problem)
    }

    @Test
    fun `a backup the app can't open is an error`() {
        val e = assertFailsWith<GoogleException> {
            connect { signIn -> get(signIn.redirect + "?picked_file_ids=file-2&code=code-1&state=${signIn.state}") }
        }
        assertEquals(GoogleProblem.FileGone, e.problem)
    }

    /** What the app opened in the browser */
    private class SignIn(url: URI) {
        private val query = FakeGoogle.form(url.rawQuery)
        val redirect: String = query.getValue("redirect_uri")
        val state: String = query.getValue("state")
        val challenge: String = query.getValue("code_challenge")
    }

    private var lastSignIn: SignIn? = null

    /** Connects, with [browser] doing what the person and Google would in the browser the app opened */
    private fun connect(browser: (SignIn) -> Unit): DriveConnection? = runBlocking {
        var failure: Throwable? = null
        var opened: Thread? = null
        val connection = GoogleConnect(api) { url ->
            val signIn = SignIn(url).also { lastSignIn = it }
            opened = thread {
                try {
                    browser(signIn)
                } catch (e: AssertionError) {
                    failure = e
                }
            }
        }.connect(timeout = 1.minutes)
        // until the browser has the last page too
        opened?.join()
        failure?.let { throw it }
        connection
    }

    private fun get(url: String): HttpResponse<String> =
        browser.send(HttpRequest.newBuilder(URI(url)).GET().build(), HttpResponse.BodyHandlers.ofString())
}
