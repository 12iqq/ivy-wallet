package com.ivy.desktop.ui

import com.ivy.desktop.SampleData
import com.ivy.desktop.data.Backup
import com.ivy.desktop.google.DriveBackup
import com.ivy.desktop.google.DriveConnection
import com.ivy.desktop.google.DriveFileId
import com.ivy.desktop.google.FakeGoogle
import com.ivy.desktop.google.GoogleApi
import com.ivy.desktop.google.GoogleConfig
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DriveSourceTest {
    private val google = FakeGoogle().apply {
        contents = Json.encodeToString(Backup.serializer(), SampleData.backup()).toByteArray()
    }
    private val zone = ZoneId.of("Asia/Dubai")
    private val now = Instant.parse("2026-10-29T08:00:00Z")
    private val api = GoogleApi(GoogleConfig("client-1", "secret-1", "key-1", "1234"), google.endpoints)
    private val connection = DriveConnection("refresh-1", DriveFileId("file-1"), "ivywallet-uae.zip")
    private val source = DriveSource(DriveBackup(api, connection), connection.fileName, zone)

    @After
    fun stop() {
        google.close()
    }

    @Test
    fun `shows the backup from Google Drive, saved when Google Drive got it`() {
        val state = assertIs<LoadState.Ready>(source.next(LoadState.Loading, now))

        assertEquals("ivywallet-uae.zip", state.name)
        assertEquals(BackupPlace.GoogleDrive, state.place)
        assertEquals(LocalDateTime.of(2026, 10, 28, 10, 10), state.savedAt)
        assertTrue(state.ledger.transactions.isNotEmpty())
        assertNull(state.problem)
    }

    @Test
    fun `the same backup isn't read again`() {
        val first = source.next(LoadState.Loading, now)

        assertSame(first, source.next(first, now))
        assertEquals(1, google.downloads)
    }

    @Test
    fun `offline, the backup shown stays, with a note`() {
        val shown = assertIs<LoadState.Ready>(source.next(LoadState.Loading, now))
        google.close()

        val state = assertIs<LoadState.Ready>(source.next(shown, now))

        assertSame(shown.ledger, state.ledger)
        assertEquals("Couldn't reach Google Drive, so there may be a newer backup.", state.problem)
    }

    @Test
    fun `offline from the start`() {
        google.close()

        val state = assertIs<LoadState.Failed>(source.next(LoadState.Loading, now))

        assertEquals(BackupPlace.GoogleDrive, state.place)
        assertTrue("Couldn't reach Google Drive" in state.message)
    }

    @Test
    fun `access removed in the Google account`() {
        google.tokenAnswer = 400 to """{"error":"invalid_grant"}"""

        val state = assertIs<LoadState.Failed>(source.next(LoadState.Loading, now))

        assertTrue("Connect it again" in state.message)
    }

    @Test
    fun `the backup deleted from Google Drive`() {
        google.file = FakeGoogle.DriveFileJson("file-1", "ivywallet-uae.zip", "2026-10-28T06:10:00Z", "c1", true)

        val state = assertIs<LoadState.Failed>(source.next(LoadState.Loading, now))

        assertTrue("isn't in your Google Drive anymore" in state.message)
    }

    @Test
    fun `a newer file that isn't a backup leaves the last one shown`() {
        val shown = assertIs<LoadState.Ready>(source.next(LoadState.Loading, now))
        google.file = FakeGoogle.DriveFileJson("file-1", "ivywallet-uae.zip", "2026-10-29T06:10:00Z", "c2", false)
        google.contents = "not a backup".toByteArray()

        val state = assertIs<LoadState.Ready>(source.next(shown, now))

        assertSame(shown.ledger, state.ledger)
        assertTrue(state.problem.orEmpty().startsWith("The newest backup in Google Drive couldn't be read."))
    }
}
