package com.ivy.desktop.google

import org.junit.After
import org.junit.Test
import java.net.ServerSocket
import java.net.URI
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class GoogleApiTest {
    private val google = FakeGoogle()
    private val now = Instant.parse("2026-10-29T08:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val config = GoogleConfig("client-1", "secret-1")
    private val api = GoogleApi(config, google.endpoints, clock)

    @After
    fun stop() {
        google.close()
    }

    @Test
    fun `the sign-in asks only for the file chosen in Google's picker, and for a refresh token`() {
        val url = URI(api.signInUrl("http://127.0.0.1:5000/signed-in", "state-1", "challenge-1"))
        val query = FakeGoogle.form(url.rawQuery)

        assertEquals("${google.base}/auth", url.toString().substringBefore('?'))
        assertEquals("client-1", query["client_id"])
        assertEquals("http://127.0.0.1:5000/signed-in", query["redirect_uri"])
        assertEquals("code", query["response_type"])
        assertEquals("https://www.googleapis.com/auth/drive.file", query["scope"])
        assertEquals("challenge-1", query["code_challenge"])
        assertEquals("S256", query["code_challenge_method"])
        assertEquals("state-1", query["state"])
        assertEquals("offline", query["access_type"])
        assertEquals("consent", query["prompt"])
        // then Google's file picker, for one backup
        assertEquals("true", query["trigger_onepick"])
        assertEquals("false", query["allow_multiple"])
        assertEquals("application/zip,application/x-zip-compressed,application/json", query["mimetypes"])
    }

    @Test
    fun `the code from the sign-in gets the tokens`() {
        val tokens = api.exchange("code-1", "http://127.0.0.1:5000/signed-in", "verifier-1")

        assertEquals("access-1", tokens.access.value)
        assertEquals(now.plusSeconds(3599), tokens.access.expiresAt)
        assertEquals("refresh-1", tokens.refresh)
        assertEquals(
            mapOf(
                "client_id" to "client-1",
                "client_secret" to "secret-1",
                "grant_type" to "authorization_code",
                "code" to "code-1",
                "redirect_uri" to "http://127.0.0.1:5000/signed-in",
                "code_verifier" to "verifier-1",
            ),
            google.tokenForms.single(),
        )
    }

    @Test
    fun `without a client secret, none is sent`() {
        GoogleApi(GoogleConfig("client-1", ""), google.endpoints, clock).refresh("refresh-1")

        assertFalse("client_secret" in google.tokenForms.single())
    }

    @Test
    fun `signing in without a refresh token doesn't work, as the app couldn't stay signed in`() {
        google.tokenAnswer = FakeGoogle.OK to """{"access_token":"access-1","expires_in":3599}"""

        val e = assertFailsWith<GoogleException> { api.exchange("code-1", "http://127.0.0.1/", "verifier-1") }
        assertEquals(GoogleProblem.Refused, e.problem)
    }

    @Test
    fun `a refresh token that was taken back means access was removed`() {
        google.tokenAnswer = 400 to """{"error":"invalid_grant","error_description":"Token has been revoked."}"""

        val e = assertFailsWith<GoogleException> { api.refresh("refresh-1") }
        assertEquals(GoogleProblem.AccessRemoved, e.problem)
        assertEquals("refresh_token", google.tokenForms.single()["grant_type"])
    }

    @Test
    fun `other sign-in problems are refusals`() {
        google.tokenAnswer = 500 to "Server error"

        assertEquals(GoogleProblem.Refused, assertFailsWith<GoogleException> { api.refresh("refresh-1") }.problem)
    }

    @Test
    fun `the file's details and contents, with the access token`() {
        val token = AccessToken("access-1", now.plusSeconds(60))

        val file = api.file(DriveFileId("file-1"), token)
        val contents = api.download(DriveFileId("file-1"), token)

        assertEquals(
            DriveFile(DriveFileId("file-1"), "ivywallet-uae.zip", Instant.parse("2026-10-28T06:10:00Z"), "c1"),
            file,
        )
        assertContentEquals("the backup".toByteArray(), contents)
        assertEquals(listOf("Bearer access-1", "Bearer access-1"), google.driveAuthorizations.toList())
    }

    @Test
    fun `a file in the bin, or not there, is gone`() {
        val token = AccessToken("access-1", now.plusSeconds(60))
        google.file = FakeGoogle.DriveFileJson("file-1", "ivywallet-uae.zip", "2026-10-28T06:10:00Z", "c1", true)

        assertEquals(
            GoogleProblem.FileGone,
            assertFailsWith<GoogleException> { api.file(DriveFileId("file-1"), token) }.problem,
        )
        assertEquals(
            GoogleProblem.FileGone,
            assertFailsWith<GoogleException> { api.download(DriveFileId("file-2"), token) }.problem,
        )
    }

    @Test
    fun `an access token Google doesn't take means access was removed`() {
        google.driveFailure = 401 to 1

        val e = assertFailsWith<GoogleException> {
            api.file(DriveFileId("file-1"), AccessToken("old", now.plusSeconds(60)))
        }
        assertEquals(GoogleProblem.AccessRemoved, e.problem)
    }

    @Test
    fun `without internet, Google can't be reached`() {
        val closedPort = ServerSocket(0).use { it.localPort }
        val offline = GoogleEndpoints(
            signIn = "http://127.0.0.1:$closedPort/auth",
            token = "http://127.0.0.1:$closedPort/token",
            revoke = "http://127.0.0.1:$closedPort/revoke",
            drive = "http://127.0.0.1:$closedPort/drive/v3",
        )

        val e = assertFailsWith<GoogleException> { GoogleApi(config, offline, clock).refresh("refresh-1") }
        assertEquals(GoogleProblem.Offline, e.problem)
    }

    @Test
    fun `revoking sends the token to take back`() {
        api.revoke("refresh-1")

        assertEquals(listOf("refresh-1"), google.revoked.toList())
        assertNull(google.tokenForms.firstOrNull())
    }
}
