package com.ivy.insights.recurring

import io.kotest.matchers.shouldBe
import org.junit.Test
import java.time.LocalDate

class RecurringChargesTest {

    private val today = LocalDate.of(2026, 10, 26)

    private fun charge(name: String, amount: Double, date: LocalDate, planned: Boolean = false): Charge = Charge(
        name = name,
        amount = amount,
        accountAmount = amount,
        date = date,
        categoryId = "subscriptions",
        accountId = "bank",
        planned = planned,
    )

    /** One charge a month, back from [last], [count] times. */
    private fun monthly(name: String, amount: Double, last: LocalDate, count: Int): List<Charge> =
        (0 until count).map { charge(name, amount, last.minusMonths(it.toLong())) }

    @Test
    fun `a monthly subscription`() {
        val found = RecurringCharges.find(monthly("Streaming", 39.0, LocalDate.of(2026, 10, 12), 8), today).single()

        found.name shouldBe "Streaming"
        found.cadence shouldBe Cadence.Monthly
        found.amount shouldBe 39.0
        found.varies shouldBe false
        found.previousAmount shouldBe null
        found.perMonth shouldBe 39.0
        found.lastDate shouldBe LocalDate.of(2026, 10, 12)
        found.nextDate shouldBe LocalDate.of(2026, 11, 12)
        found.active shouldBe true
        found.planned shouldBe false
    }

    @Test
    fun `a few days early or late is still on time`() {
        val dates = listOf(
            LocalDate.of(2026, 10, 3),
            LocalDate.of(2026, 9, 1),
            LocalDate.of(2026, 8, 5),
            LocalDate.of(2026, 7, 2),
            LocalDate.of(2026, 6, 3),
        )
        RecurringCharges.find(dates.map { charge("Internet", 155.0, it) }, today).single()
            .cadence shouldBe Cadence.Monthly
    }

    @Test
    fun `a shop you visit often isn't recurring`() {
        // about every 10 days, same name and amount
        val visits = (0 until 20).map { charge("Bakery", 12.0, LocalDate.of(2026, 10, 20).minusDays(it * 10L)) }

        RecurringCharges.find(visits, today) shouldBe emptyList()
    }

    @Test
    fun `a price change is shown`() {
        val charges = monthly("Music", 21.99, LocalDate.of(2026, 10, 15), 2) +
            monthly("Music", 19.99, LocalDate.of(2026, 8, 15), 4)

        val found = RecurringCharges.find(charges, today).single()
        found.amount shouldBe 21.99
        found.previousAmount shouldBe 19.99
        found.varies shouldBe false
    }

    @Test
    fun `a bill that varies gets its usual amount`() {
        val amounts = listOf(410.0, 520.0, 380.0, 450.0, 600.0, 430.0)
        val charges = amounts.mapIndexed { i, amount ->
            charge("Water", amount, LocalDate.of(2026, 10, 4).minusMonths(i.toLong()))
        }

        val found = RecurringCharges.find(charges, today).single()
        found.varies shouldBe true
        found.amount shouldBe 440.0
        found.previousAmount shouldBe null
    }

    @Test
    fun `every three months and yearly`() {
        val quarterly = (0 until 4).map { charge("Data plan", 195.0, LocalDate.of(2026, 9, 4).minusMonths(it * 3L)) }
        val yearly = listOf(
            charge("Cloud storage", 59.99, LocalDate.of(2026, 4, 20)),
            charge("Cloud storage", 59.99, LocalDate.of(2025, 4, 18)),
        )

        val found = RecurringCharges.find(quarterly + yearly, today).associateBy { it.name }
        found.getValue("Data plan").cadence shouldBe Cadence.Quarterly
        found.getValue("Data plan").perMonth shouldBe 65.0
        found.getValue("Cloud storage").cadence shouldBe Cadence.Yearly
        found.getValue("Cloud storage").nextDate shouldBe LocalDate.of(2027, 4, 20)
    }

    @Test
    fun `twice a year apart at different prices isn't a subscription`() {
        val visits = listOf(
            charge("Cafe", 210.0, LocalDate.of(2025, 11, 9)),
            charge("Cafe", 140.0, LocalDate.of(2024, 11, 10)),
        )

        RecurringCharges.find(visits, today) shouldBe emptyList()
    }

    @Test
    fun `a stopped charge comes after the active ones, one stopped long ago isn't listed`() {
        val stopped = monthly("Gym", 250.0, LocalDate.of(2026, 6, 1), 6)
        val longAgo = monthly("Old phone plan", 99.0, LocalDate.of(2025, 6, 1), 6)
        val active = monthly("Streaming", 39.0, LocalDate.of(2026, 10, 12), 6)

        val found = RecurringCharges.find(stopped + longAgo + active, today)
        found.map { it.name } shouldBe listOf("Streaming", "Gym")
        found.map { it.active } shouldBe listOf(true, false)
    }

    @Test
    fun `planned when paid as a planned payment or one has its name`() {
        val paidAsPlanned = monthly("Rent", 5000.0, LocalDate.of(2026, 10, 1), 6)
            .mapIndexed { i, rent -> if (i == 0) rent.copy(planned = true) else rent }
        val sameName = monthly("Phone", 120.0, LocalDate.of(2026, 10, 9), 6)

        val found = RecurringCharges.find(paidAsPlanned + sameName, today, plannedNames = setOf("phone"))
        found.map { it.planned } shouldBe listOf(true, true)
    }

    @Test
    fun `names match whatever their case and spacing, balance corrections aren't charges`() {
        val charges = listOf("Streaming", "streaming ", "STREAMING", "Streaming", "streaming").mapIndexed { i, name ->
            charge(name, 39.0, LocalDate.of(2026, 10, 12).minusMonths(i.toLong()))
        } + monthly("Adjust balance", 10.0, LocalDate.of(2026, 10, 1), 6)

        val found = RecurringCharges.find(charges, today).single()
        found.key shouldBe "streaming"
        found.charges.size shouldBe 5
    }
}
