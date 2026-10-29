package com.ivy.desktop.google

import org.junit.After
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class DriveBackupTest {
    private val google = FakeGoogle()
    private val config = GoogleConfig("client-1", "secret-1")
    private val connection = DriveConnection("refresh-1", DriveFileId("file-1"), "ivywallet-uae.zip")
    private var now = Instant.parse("2026-10-29T08:00:00Z")
    private val clock = object : Clock() {
        override fun instant(): Instant = now
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
    }
    private val backup = DriveBackup(GoogleApi(config, google.endpoints, clock), connection, clock)

    @After
    fun stop() {
        google.close()
    }

    @Test
    fun `the first look signs in with the refresh token and downloads the backup`() {
        val check = backup.check(shown = null)

        assertEquals("ivywallet-uae.zip", check.file.name)
        assertContentEquals("the backup".toByteArray(), check.contents)
        assertEquals("refresh_token", google.tokenForms.single()["grant_type"])
        assertEquals("refresh-1", google.tokenForms.single()["refresh_token"])
    }

    @Test
    fun `the backup is only downloaded again when it changed`() {
        val first = backup.check(shown = null)

        val same = backup.check(shown = first.file)
        google.file = FakeGoogle.DriveFileJson("file-1", "ivywallet-uae.zip", "2026-10-29T06:10:00Z", "c2", false)
        google.contents = "the next backup".toByteArray()
        val next = backup.check(shown = same.file)

        assertNull(same.contents)
        assertContentEquals("the next backup".toByteArray(), next.contents)
        assertEquals(2, google.downloads)
        // the access token was still good
        assertEquals(1, google.tokenForms.size)
    }

    @Test
    fun `a new access token when the last one ran out`() {
        val first = backup.check(shown = null)
        now += Duration.ofHours(1)

        backup.check(shown = first.file)

        assertEquals(2, google.tokenForms.size)
    }

    @Test
    fun `when Google turns down the access token, a new one is tried once`() {
        backup.check(shown = null)
        google.driveFailure = 401 to 1

        backup.check(shown = null)

        assertEquals(2, google.tokenForms.size)
    }

    @Test
    fun `access removed in the Google account`() {
        google.tokenAnswer = 400 to """{"error":"invalid_grant"}"""

        val e = assertFailsWith<GoogleException> { backup.check(shown = null) }
        assertEquals(GoogleProblem.AccessRemoved, e.problem)
    }

    @Test
    fun `disconnecting takes the refresh token back, and doesn't fail offline`() {
        backup.disconnect()
        google.close()
        backup.disconnect()

        assertEquals(listOf("refresh-1"), google.revoked.toList())
    }
}
