package com.ivy.settings.duplicates

import com.ivy.base.model.TransactionType
import io.kotest.matchers.shouldBe
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

class DuplicateFinderTest {

    private val zone = ZoneId.of("Asia/Dubai")
    private val bank = UUID.randomUUID()
    private val card = UUID.randomUUID()

    private fun entry(
        day: Int,
        hour: Int,
        amount: Double,
        account: UUID = bank,
        type: TransactionType = TransactionType.EXPENSE,
    ): DuplicateEntry = DuplicateEntry(
        id = UUID.randomUUID(),
        type = type,
        accountId = account,
        toAccountId = null,
        amount = amount,
        time = LocalDateTime.of(2026, 10, day, hour, 0).atZone(zone).toInstant(),
    )

    @Test
    fun `same amount, account and day is a possible duplicate`() {
        val first = entry(day = 7, hour = 9, amount = 97.18)
        val second = entry(day = 7, hour = 18, amount = 97.18)

        val nextDay = entry(day = 8, hour = 9, amount = 97.18)
        val groups = DuplicateFinder.groups(listOf(second, first, nextDay), zone, emptySet())

        groups.size shouldBe 1
        groups[0].entries shouldBe listOf(first, second)
        groups[0].day shouldBe LocalDate.of(2026, 10, 7)
    }

    @Test
    fun `another account, amount or kind isn't`() {
        DuplicateFinder.groups(
            listOf(
                entry(day = 7, hour = 9, amount = 50.0),
                entry(day = 7, hour = 10, amount = 50.0, account = card),
                entry(day = 7, hour = 11, amount = 50.5),
                entry(day = 7, hour = 12, amount = 50.0, type = TransactionType.INCOME),
            ),
            zone,
            emptySet()
        ) shouldBe emptyList()
    }

    @Test
    fun `days follow the local clock`() {
        // 23:30 and 00:30 in Dubai are different days, though the same day in UTC
        val late = entry(day = 7, hour = 23, amount = 20.0).let { it.copy(time = it.time.plusSeconds(1800)) }
        val early = late.copy(id = UUID.randomUUID(), time = late.time.plusSeconds(3600))
        late.time.atOffset(ZoneOffset.UTC).toLocalDate() shouldBe early.time.atOffset(ZoneOffset.UTC).toLocalDate()

        DuplicateFinder.groups(listOf(late, early), zone, emptySet()) shouldBe emptyList()
    }

    @Test
    fun `groups marked not duplicates stay hidden`() {
        val first = entry(day = 7, hour = 9, amount = 12.0)
        val second = entry(day = 7, hour = 9, amount = 12.0)
        val key = DuplicateFinder.key(first, zone)

        DuplicateFinder.groups(listOf(first, second), zone, setOf(key)) shouldBe emptyList()
    }

    @Test
    fun `newest groups first`() {
        val older = listOf(entry(day = 3, hour = 9, amount = 5.0), entry(day = 3, hour = 10, amount = 5.0))
        val newer = listOf(entry(day = 9, hour = 9, amount = 8.0), entry(day = 9, hour = 10, amount = 8.0))

        DuplicateFinder.groups(older + newer, zone, emptySet()).map { it.day.dayOfMonth } shouldBe listOf(9, 3)
    }
}
