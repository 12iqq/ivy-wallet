package com.ivy.autocapture.data

import com.ivy.autocapture.parser.CardBill
import com.ivy.base.legacy.SharedPrefs
import com.ivy.base.model.TransactionType
import com.ivy.data.db.dao.fake.FakeAccountDao
import com.ivy.data.db.dao.fake.FakePlannedPaymentDao
import com.ivy.data.db.dao.fake.FakeSettingsDao
import com.ivy.data.db.dao.fake.FakeTransactionDao
import com.ivy.data.db.entity.AccountEntity
import com.ivy.data.db.entity.TransactionEntity
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class CardBillPlannerTest {

    private val bank = AccountEntity(name = "Bank", color = 0, currency = "AED", orderNum = 1.0)
    private val cash = AccountEntity(name = "Cash", color = 0, currency = "AED", orderNum = 0.0)
    private val card = AccountEntity(
        name = "Card",
        color = 0,
        currency = "AED",
        orderNum = 2.0,
        accountType = AccountEntity.TYPE_CREDIT_CARD,
    )

    private var captureConfig = AutoCaptureConfig(links = listOf(CardLink("adcb", "1234", card.id.toString())))
    private val store = mockk<AutoCaptureSettingsStore> { every { current() } answers { captureConfig } }

    private val savedPrefs = mutableMapOf<String, String?>()
    private val prefs = mockk<SharedPrefs> {
        every { getString(SharedPrefs.LAST_SELECTED_ACCOUNT_ID, any()) } returns null
        every { getString(SharedPrefs.PLANNED_CARD_BILLS, any()) } answers {
            savedPrefs[SharedPrefs.PLANNED_CARD_BILLS]
        }
        val written = slot<String>()
        every { putString(SharedPrefs.PLANNED_CARD_BILLS, capture(written)) } answers {
            savedPrefs[SharedPrefs.PLANNED_CARD_BILLS] = written.captured
        }
    }
    private val transactions = FakeTransactionDao()
    private val rules = FakePlannedPaymentDao()
    private val accounts = FakeAccountDao()
    private val planner = CardBillPlanner(
        settingsStore = store,
        sharedPrefs = prefs,
        transactionDao = transactions,
        transactionWriter = transactions,
        ruleDao = rules,
        ruleWriter = rules,
        accountDao = accounts,
        settingsDao = FakeSettingsDao(),
    )

    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 10, 8)
    private val alertAt = at(2026, 10, 8, 11)
    private val bill = CardBill(
        bankId = "adcb",
        last4 = "1234",
        currency = "AED",
        totalDue = 1234.56,
        minimumDue = 100.0,
        dueDate = LocalDate.of(2026, 10, 30),
    )
    private val due = bill.dueDate.atTime(LocalTime.NOON).atZone(zone).toInstant()
    private val ruleId = CardBillRules.ruleId(card.id, bill.dueDate)

    private fun at(year: Int, month: Int, day: Int, hour: Int): Instant =
        LocalDateTime.of(year, month, day, hour, 0).atZone(zone).toInstant()

    private fun transfer(amount: Double, at: Instant, from: AccountEntity = bank): TransactionEntity =
        TransactionEntity(
            accountId = from.id,
            type = TransactionType.TRANSFER,
            amount = amount,
            toAccountId = card.id,
            toAmount = amount,
            dateTime = at,
        )

    private suspend fun pending(): List<TransactionEntity> =
        transactions.findAllTransfersToAccount(card.id).filter { it.dateTime == null }

    private suspend fun setUp() {
        accounts.save(cash)
        accounts.save(bank)
        accounts.save(card)
    }

    @Test
    fun `a bill becomes a planned transfer from the account that paid the card last time`() = runTest {
        setUp()
        transactions.save(transfer(500.0, at(2026, 9, 10, 9)))

        planner.plan(bill, alertAt, today)

        val planned = pending().single()
        planned.accountId shouldBe bank.id
        planned.toAccountId shouldBe card.id
        planned.amount shouldBe 1234.56
        planned.toAmount shouldBe 1234.56
        planned.dueDate shouldBe due
        planned.recurringRuleId shouldBe ruleId
        planned.title shouldBe CardBillRules.TITLE
        planned.description shouldBe "Total due AED 1,234.56 · Minimum AED 100.00"
        rules.findById(ruleId)!!.let { rule ->
            rule.oneTime shouldBe true
            rule.type shouldBe TransactionType.TRANSFER
            rule.toAccountId shouldBe card.id
            rule.startDate shouldBe due
        }
    }

    @Test
    fun `without a past card payment it's paid from your first regular account`() = runTest {
        setUp()

        planner.plan(bill, alertAt, today)

        pending().single().accountId shouldBe cash.id
    }

    @Test
    fun `paying the card marks the bill paid`() = runTest {
        setUp()
        planner.plan(bill, alertAt, today)

        val payment = transfer(1234.56, at(2026, 10, 12, 14))
        transactions.save(payment)
        planner.onPayment(payment)

        pending() shouldBe emptyList()
        rules.findById(ruleId) shouldBe null
        transactions.findById(payment.id)!!.let {
            it.recurringRuleId shouldBe ruleId
            it.paidForDateTime shouldBe due
        }
    }

    @Test
    fun `a smaller payment lowers what's left`() = runTest {
        setUp()
        planner.plan(bill, alertAt, today)

        val first = transfer(1000.0, at(2026, 10, 12, 14))
        transactions.save(first)
        planner.onPayment(first)

        val left = pending().single()
        left.toAmount shouldBe 234.56
        left.amount shouldBe 234.56
        left.description shouldBe "Total due AED 1,234.56 · Minimum AED 100.00 · Paid AED 1,000.00"
        transactions.findById(first.id)!!.recurringRuleId shouldBe null

        val second = transfer(234.56, at(2026, 10, 20, 9))
        transactions.save(second)
        planner.onPayment(second)

        pending() shouldBe emptyList()
        transactions.findById(second.id)!!.recurringRuleId shouldBe ruleId
    }

    @Test
    fun `paid just before the alert came`() = runTest {
        setUp()
        val payment = transfer(1234.56, alertAt.minusSeconds(360))
        transactions.save(payment)

        planner.plan(bill, alertAt, today)

        pending() shouldBe emptyList()
        transactions.findById(payment.id)!!.recurringRuleId shouldBe ruleId
    }

    @Test
    fun `reading the alert again doesn't plan it twice, even after you deleted it`() = runTest {
        setUp()
        planner.plan(bill, alertAt, today)
        planner.plan(bill, alertAt, today)
        pending().size shouldBe 1

        transactions.deleteById(pending().single().id)
        rules.deleteById(ruleId)
        planner.plan(bill, alertAt, today)

        pending() shouldBe emptyList()
    }

    @Test
    fun `a new statement replaces the unpaid one`() = runTest {
        setUp()
        planner.plan(bill, alertAt, today)
        val next = bill.copy(totalDue = 2000.0, dueDate = LocalDate.of(2026, 11, 30))

        planner.plan(next, at(2026, 11, 8, 11), LocalDate.of(2026, 11, 8))

        pending().single().amount shouldBe 2000.0
        rules.findById(ruleId) shouldBe null
    }

    @Test
    fun `nothing is planned when it's off, past due or the card isn't linked`() = runTest {
        setUp()
        planner.plan(bill, alertAt, LocalDate.of(2026, 10, 31))
        planner.plan(bill.copy(last4 = "9999", bankId = "fab"), alertAt, today)
        captureConfig = captureConfig.copy(planCardBills = false)
        planner.plan(bill, alertAt, today)

        pending() shouldBe emptyList()
    }

    @Test
    fun `a payment to another card or from long before doesn't count`() = runTest {
        setUp()
        val otherCard = AccountEntity(name = "Other card", color = 0, currency = "AED", orderNum = 3.0)
        accounts.save(otherCard)
        planner.plan(bill, alertAt, today)

        val elsewhere = transfer(1234.56, at(2026, 10, 12, 14)).copy(toAccountId = otherCard.id)
        val old = transfer(1234.56, at(2026, 9, 20, 14))
        planner.onPayment(elsewhere)
        planner.onPayment(old)

        pending().size shouldBe 1
    }

    @Test
    fun `the alert is read as a bill`() = runTest {
        setUp()
        val read = planner.onMessage(
            "adcb",
            "Cr.Card XXX1234 Billing alert: Total due to avoid fin. charges: AED1234.56. Due date Oct 30 2099; " +
                "Pay min. AED100.00 by due date to avoid AED250.00 late fees.",
            alertAt,
        )

        read shouldBe true
        pending().single().amount shouldBe 1234.56
        planner.onMessage("adcb", "Your Cr.Card XXX1234 was used for AED45.00 at SHOP", alertAt) shouldBe false
    }
}
