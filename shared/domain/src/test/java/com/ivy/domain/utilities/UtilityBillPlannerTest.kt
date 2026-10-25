package com.ivy.domain.utilities

import com.ivy.base.legacy.SharedPrefs
import com.ivy.base.model.TransactionType
import com.ivy.data.db.dao.fake.FakeAccountDao
import com.ivy.data.db.dao.fake.FakePlannedPaymentDao
import com.ivy.data.db.dao.fake.FakeTransactionDao
import com.ivy.data.db.dao.fake.FakeUtilityBillDao
import com.ivy.data.db.dao.write.CapturedTransactionDao
import com.ivy.data.db.entity.AccountEntity
import com.ivy.data.db.entity.CapturedTransactionEntity
import com.ivy.data.db.entity.TransactionEntity
import com.ivy.data.db.entity.UtilityBillEntity
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

class UtilityBillPlannerTest {

    private val prefs = mockk<SharedPrefs> {
        every { getBoolean(SharedPrefs.TRACK_UTILITY_BILLS, any()) } returns true
        every { getBoolean(SharedPrefs.PLAN_UTILITY_BILLS, any()) } returns true
        every { getString(SharedPrefs.LAST_SELECTED_ACCOUNT_ID, any()) } returns null
    }
    private val bills = FakeUtilityBillDao()
    private val transactions = FakeTransactionDao()
    private val rules = FakePlannedPaymentDao()
    private val accounts = FakeAccountDao()
    private val captured = FakeCapturedDao()
    private val planner = UtilityBillPlanner(prefs, bills, transactions, transactions, rules, accounts, captured)

    private val bank = AccountEntity(name = "Bank", color = 0, orderNum = 0.0)
    private val card = AccountEntity(name = "Card", color = 0, orderNum = 1.0)
    private val today = LocalDate.of(2026, 10, 25)
    private val october = UtilityBillEntity(
        id = "SEWA:020000123456:2026-10-17",
        provider = "SEWA",
        account = "020000123456",
        periodStart = "2026-09-17",
        periodEnd = "2026-10-17",
        electricity = 281.45,
        water = 61.30,
        gas = 20.0,
        municipality = 18.70,
        vat = 16.55,
        others = 0.0,
        arrears = 0.0,
        total = 398.0,
        dueDate = "2026-11-04",
        currency = "AED",
    )
    private val ruleId = UtilityBillRules.ruleId(october.id)
    private val due = LocalDate.of(2026, 11, 4).atTime(LocalTime.NOON).atZone(ZoneId.systemDefault()).toInstant()
    private val paidAt = at(2026, 11, 2, 10, 0)

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Instant =
        LocalDateTime.of(year, month, day, hour, minute).toInstant(ZoneOffset.UTC)

    private fun confirmation(amount: Double = 408.0, date: LocalDate = LocalDate.of(2026, 11, 2)): UtilityPayment =
        UtilityPayment(provider = "SEWA", accounts = listOf("020000123456"), amount = amount, date = date)

    private suspend fun plannedOctober() {
        accounts.save(bank)
        accounts.save(card)
        bills.saveAll(listOf(october))
        planner.planDueBills(today)
    }

    @Test
    fun `a new bill becomes a planned payment like your last SEWA payment`() = runTest {
        val billsCategory = UUID.randomUUID()
        transactions.save(
            TransactionEntity(
                accountId = card.id,
                type = TransactionType.EXPENSE,
                amount = 475.0,
                title = "Sewa - Home",
                bankDetails = "SEWA",
                categoryId = billsCategory,
                dateTime = at(2026, 10, 3, 10, 53),
            )
        )
        plannedOctober()

        val pending = transactions.findAllByRecurringRuleId(ruleId).single()
        pending.dateTime shouldBe null
        pending.dueDate shouldBe due
        pending.amount shouldBe 398.0
        pending.title shouldBe "Sewa - Home"
        pending.categoryId shouldBe billsCategory
        pending.accountId shouldBe card.id
        rules.findById(ruleId)?.oneTime shouldBe true
        bills.findById(october.id)?.planned shouldBe true
    }

    @Test
    fun `a bill is planned once, even when read again`() = runTest {
        plannedOctober()
        bills.saveAll(listOf(october.copy(planned = true)))
        planner.planDueBills(today)

        transactions.findAllByRecurringRuleId(ruleId).size shouldBe 1
    }

    @Test
    fun `without an earlier payment it's named after the provider, on the first account`() = runTest {
        plannedOctober()

        val pending = transactions.findAllByRecurringRuleId(ruleId).single()
        pending.title shouldBe "SEWA"
        pending.accountId shouldBe bank.id
    }

    @Test
    fun `bills past their due date aren't planned`() = runTest {
        plannedOctober()
        val september = october.copy(
            id = "SEWA:020000123456:2026-09-16",
            periodEnd = "2026-09-16",
            dueDate = "2026-10-05",
        )
        bills.saveAll(listOf(september))
        planner.planDueBills(today)

        transactions.findAllByRecurringRuleId(UtilityBillRules.ruleId(september.id)) shouldBe emptyList()
    }

    @Test
    fun `SEWA's confirmation marks the bill paid`() = runTest {
        plannedOctober()
        planner.onPaymentReceived(confirmation(), paidAt)

        val paid = transactions.findAllByRecurringRuleId(ruleId).single()
        paid.dateTime shouldBe paidAt
        paid.amount shouldBe 408.0
        paid.dueDate shouldBe null
        paid.paidForDateTime shouldBe due
        rules.findById(ruleId) shouldBe null
    }

    @Test
    fun `a confirmation from before the bill doesn't pay it`() = runTest {
        plannedOctober()
        planner.onPaymentReceived(confirmation(date = LocalDate.of(2026, 10, 3)), at(2026, 10, 3, 10, 0))

        transactions.findAllByRecurringRuleId(ruleId).single().dateTime shouldBe null
    }

    @Test
    fun `the bank's text that was added first becomes the bill's payment`() = runTest {
        plannedOctober()
        val fromBank = TransactionEntity(
            accountId = card.id,
            type = TransactionType.EXPENSE,
            amount = 408.0,
            title = "Sewa - Home",
            bankDetails = "SEWA",
            dateTime = at(2026, 11, 2, 9, 59),
        )
        transactions.save(fromBank)
        planner.onPaymentReceived(confirmation(), paidAt)

        transactions.findAllByRecurringRuleId(ruleId).map { it.id } shouldBe listOf(fromBank.id)
        transactions.findById(fromBank.id)?.paidForDateTime shouldBe due
    }

    @Test
    fun `the bank's text waiting in review joins the payment instead of being added`() = runTest {
        plannedOctober()
        val text = bankText(amount = 408.0)
        captured.save(text)
        planner.onPaymentReceived(confirmation(), paidAt)

        val paid = transactions.findAllByRecurringRuleId(ruleId).single()
        paid.accountId shouldBe card.id
        captured.findById(text.id)?.status shouldBe CapturedTransactionEntity.STATUS_MERGED
        captured.findById(text.id)?.transactionId shouldBe paid.id
    }

    @Test
    fun `a bank's text after the confirmation is the payment already recorded`() = runTest {
        plannedOctober()
        planner.onPaymentReceived(confirmation(), paidAt)
        val later = paidAt.plusSeconds(60)

        planner.recordedPayment("SEWA", 408.0, later)?.id shouldBe UtilityBillRules.paymentId(october.id)
        // another amount: another home's bill paid the same day
        planner.recordedPayment("SEWA PAYMENT", 371.0, later) shouldBe null
        planner.recordedPayment("CARREFOUR", 408.0, later) shouldBe null
    }

    private fun bankText(amount: Double): CapturedTransactionEntity = CapturedTransactionEntity(
        source = CapturedTransactionEntity.SOURCE_SMS,
        sender = "Bank",
        rawText = "AED$amount debited from Acc/Cr.Card XXX1234 for SEWA",
        bankId = "bank",
        type = TransactionType.EXPENSE,
        amount = amount,
        currency = "AED",
        merchant = "SEWA",
        last4 = "1234",
        isCreditCard = true,
        receivedAt = at(2026, 11, 2, 9, 59),
        accountId = card.id,
        categoryId = null,
        status = CapturedTransactionEntity.STATUS_PENDING,
        transactionId = null,
        fingerprint = "bank-text",
    )

    private class FakeCapturedDao : CapturedTransactionDao {
        private val items = mutableMapOf<UUID, CapturedTransactionEntity>()

        override suspend fun save(value: CapturedTransactionEntity) {
            items[value.id] = value
        }

        override fun pendingFlow(): Flow<List<CapturedTransactionEntity>> = flowOf(pending())

        override suspend fun findPending(): List<CapturedTransactionEntity> = pending()

        override fun pendingCountFlow(): Flow<Int> = flowOf(pending().size)

        override suspend fun findById(id: UUID): CapturedTransactionEntity? = items[id]

        override suspend fun findAdded(): List<CapturedTransactionEntity> = items.values.filter {
            it.transactionId != null
        }

        override suspend fun findReceivedBetween(from: Instant, to: Instant): List<CapturedTransactionEntity> =
            items.values.filter { !it.receivedAt.isBefore(from) && !it.receivedAt.isAfter(to) }

        override suspend fun countByFingerprint(fingerprint: String): Int =
            items.values.count { it.fingerprint == fingerprint }

        override suspend fun countByFingerprintBetween(fingerprint: String, from: Instant, to: Instant): Int =
            findReceivedBetween(from, to).count { it.fingerprint == fingerprint }

        override suspend fun claimPending(id: UUID, status: String): Int {
            val item = items[id]?.takeIf { it.status == CapturedTransactionEntity.STATUS_PENDING } ?: return 0
            items[id] = item.copy(status = status)
            return 1
        }

        override suspend fun updateStatus(id: UUID, status: String, transactionId: UUID?) {
            items[id]?.let { items[id] = it.copy(status = status, transactionId = transactionId) }
        }

        override suspend fun countFromSourceBetween(
            source: String,
            amount: Double,
            currency: String,
            last4: String?,
            from: Instant,
            to: Instant,
        ): Int = 0

        override suspend fun deleteHandledBefore(before: Instant) {
            items.values.removeAll { it.status != CapturedTransactionEntity.STATUS_PENDING && it.receivedAt < before }
        }

        private fun pending(): List<CapturedTransactionEntity> =
            items.values.filter { it.status == CapturedTransactionEntity.STATUS_PENDING }
    }
}
