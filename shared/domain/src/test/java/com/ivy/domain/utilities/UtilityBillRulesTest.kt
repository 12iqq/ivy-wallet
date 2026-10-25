package com.ivy.domain.utilities

import com.ivy.data.db.entity.UtilityBillEntity
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Test
import java.time.LocalDate

class UtilityBillRulesTest {

    private val today = LocalDate.of(2026, 10, 25)

    private fun bill(
        end: String,
        due: String?,
        planned: Boolean = false,
        account: String = "020000123456",
    ): UtilityBillEntity = UtilityBillEntity(
        id = "SEWA:$account:$end",
        provider = "SEWA",
        account = account,
        periodStart = LocalDate.parse(end).minusMonths(1).toString(),
        periodEnd = end,
        electricity = 281.45,
        water = 61.30,
        gas = 20.0,
        municipality = 18.70,
        vat = 16.55,
        others = 0.0,
        arrears = 0.0,
        total = 398.0,
        dueDate = due,
        currency = "AED",
        planned = planned,
    )

    @Test
    fun `a bill's planned payment keeps its ids`() {
        UtilityBillRules.ruleId("SEWA:1:2026-10-17") shouldBe UtilityBillRules.ruleId("SEWA:1:2026-10-17")
        UtilityBillRules.ruleId("SEWA:1:2026-10-17") shouldNotBe UtilityBillRules.ruleId("SEWA:1:2026-09-16")
        UtilityBillRules.paymentId("SEWA:1:2026-10-17") shouldNotBe UtilityBillRules.ruleId("SEWA:1:2026-10-17")
    }

    @Test
    fun `only bills still to pay get planned, and only once`() {
        UtilityBillRules.needsPlan(bill("2026-10-17", "2026-11-04"), today) shouldBe true
        UtilityBillRules.needsPlan(bill("2026-10-17", "2026-10-25"), today) shouldBe true
        // past its due date: paid already, or late, but not a new bill
        UtilityBillRules.needsPlan(bill("2026-09-16", "2026-10-05"), today) shouldBe false
        UtilityBillRules.needsPlan(bill("2026-10-17", "2026-11-04", planned = true), today) shouldBe false
        UtilityBillRules.needsPlan(bill("2026-10-17", null), today) shouldBe false
    }

    @Test
    fun `a payment settles the latest bill billed before it`() {
        val september = bill("2026-09-16", "2026-10-05", planned = true)
        val october = bill("2026-10-17", "2026-11-04", planned = true)
        // SEWA sometimes leaves out the leading zero
        val payment = UtilityPayment("SEWA", listOf("20000123456"), 408.0, LocalDate.of(2026, 10, 28))

        UtilityBillRules.billPaidBy(listOf(september, october), payment) shouldBe october
        UtilityBillRules.billPaidBy(listOf(september, october), payment.copy(date = LocalDate.of(2026, 10, 3))) shouldBe
            september
        UtilityBillRules.billPaidBy(listOf(september, october), payment.copy(date = LocalDate.of(2026, 9, 1))) shouldBe
            null
        UtilityBillRules.billPaidBy(listOf(october), payment.copy(accounts = listOf("020000999999"))) shouldBe null
        UtilityBillRules.billPaidBy(listOf(october.copy(planned = false)), payment) shouldBe null
    }

    @Test
    fun `the planned payment describes the bill`() {
        UtilityBillRules.description(bill("2026-10-17", "2026-11-04")) shouldBe
            "Electricity 281.45 · Water 61.30 · Gas 20.00 · 17 Sep – 17 Oct"
    }

    @Test
    fun `amounts match to the fils`() {
        UtilityBillRules.sameAmount(408.0, 408.004) shouldBe true
        UtilityBillRules.sameAmount(408.0, 398.0) shouldBe false
    }
}
