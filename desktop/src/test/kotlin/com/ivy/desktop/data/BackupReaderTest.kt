package com.ivy.desktop.data

import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class BackupReaderTest {

    // the shape the phone writes: fields with their default value are left out
    private val json = """
        {
          "accounts": [{"name": "Bank", "color": -16711936, "id": "a1"}],
          "categories": [{"name": "Food", "color": -65536, "id": "c1"}],
          "transactions": [
            {"accountId": "a1", "type": "EXPENSE", "amount": 25.5, "title": "Bakery",
             "dateTime": 1790000000000, "categoryId": "c1", "id": "t1", "isSynced": true},
            {"accountId": "a1", "type": "EXPENSE", "amount": 300.0, "dueDate": 1795000000000, "id": "t2"}
          ],
          "plannedPaymentRules": [{"accountId": "a1", "type": "EXPENSE", "amount": 300.0, "oneTime": false,
            "title": "Rent", "intervalN": 1, "intervalType": "MONTH", "startDate": 1795000000000, "id": "r1"}],
          "settings": [{"theme": "AUTO", "currency": "AED", "bufferAmount": 0.0, "name": "", "id": "s1"}],
          "sharedPrefs": {"lock_app": "false"}
        }
    """.trimIndent()

    @Test
    fun `reads the zip the phone saves, written in UTF-16`() {
        val backup = BackupReader.parse(zip("data123.json", json.toByteArray(Charsets.UTF_16)))

        assertEquals("AED", backup.settings?.single()?.currency)
        assertEquals("Bank", backup.accounts?.single()?.name)
        assertNull(backup.accounts?.single()?.currency)
        val bakery = backup.transactions!!.first()
        assertEquals(TransactionId("t1"), bakery.id)
        assertEquals(TransactionType.EXPENSE, bakery.type)
        assertEquals(CategoryId("c1"), bakery.categoryId)
        assertEquals(1_790_000_000_000, bakery.dateTime)
        assertNull(backup.transactions!![1].dateTime)
        assertEquals("Rent", backup.plannedPaymentRules?.single()?.title)
    }

    @Test
    fun `reads a JSON file on its own, in UTF-8`() {
        val backup = BackupReader.parse(json.toByteArray(Charsets.UTF_8))
        assertEquals(2, backup.transactions?.size)
    }

    @Test
    fun `anything else isn't a backup`() {
        assertFailsWith<BackupException> { BackupReader.parse("hello".toByteArray()) }
        assertFailsWith<BackupException> { BackupReader.parse(zip("notes.txt", "hello".toByteArray())) }
    }

    private fun zip(name: String, content: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(content)
            zip.closeEntry()
        }
        return out.toByteArray()
    }
}
