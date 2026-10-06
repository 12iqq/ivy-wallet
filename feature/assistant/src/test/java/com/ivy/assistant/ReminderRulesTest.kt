package com.ivy.assistant

import com.ivy.assistant.data.BackupFrequency
import com.ivy.assistant.data.ReminderRules
import com.ivy.data.model.CreditCardDetails
import io.kotest.matchers.shouldBe
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class ReminderRulesTest {

    private val card = CreditCardDetails(creditLimit = 10_000.0, statementDay = 1, paymentDueDay = 26)

    private fun reminder(today: LocalDate, owed: Double = 500.0, sent: Set<String> = emptySet()) =
        ReminderRules.cardReminder("acc", owed, card, today, daysBefore = 3, sentKeys = sent)

    @Test
    fun `no reminder when nothing is owed`() {
        reminder(LocalDate.of(2026, 10, 25), owed = 0.0) shouldBe null
    }

    @Test
    fun `no reminder too early`() {
        reminder(LocalDate.of(2026, 10, 20)) shouldBe null
    }

    @Test
    fun `reminds a few days before, then tomorrow, then today`() {
        reminder(LocalDate.of(2026, 10, 23))?.daysLeft shouldBe 3
        reminder(LocalDate.of(2026, 10, 25))?.daysLeft shouldBe 1
        reminder(LocalDate.of(2026, 10, 26))?.daysLeft shouldBe 0
    }

    @Test
    fun `each window fires once even if the job runs every day`() {
        val first = reminder(LocalDate.of(2026, 10, 23))!!
        reminder(LocalDate.of(2026, 10, 24), sent = setOf(first.key)) shouldBe null
    }

    @Test
    fun `budget alert at 80 then 100 percent`() {
        val day = LocalDate.of(2026, 10, 1)
        val almost = ReminderRules.budgetAlert("b", day, 1000.0, 850.0, emptySet())!!
        almost.level shouldBe ReminderRules.BudgetLevel.Almost
        ReminderRules.budgetAlert("b", day, 1000.0, 900.0, almost.coveredKeys) shouldBe null
        val over = ReminderRules.budgetAlert("b", day, 1000.0, 1001.0, almost.coveredKeys)!!
        over.level shouldBe ReminderRules.BudgetLevel.Over
        ReminderRules.budgetAlert("b", day, 1000.0, 1200.0, over.coveredKeys) shouldBe null
    }

    @Test
    fun `jumping straight past 100 percent sends one alert`() {
        val alert = ReminderRules.budgetAlert("b", LocalDate.of(2026, 10, 1), 100.0, 150.0, emptySet())!!
        alert.level shouldBe ReminderRules.BudgetLevel.Over
        alert.coveredKeys.size shouldBe 2
    }

    @Test
    fun `backup due`() {
        val now = Instant.parse("2026-10-06T09:00:00Z")
        ReminderRules.isBackupDue(now, null, BackupFrequency.Off) shouldBe false
        ReminderRules.isBackupDue(now, null, BackupFrequency.Daily) shouldBe true
        ReminderRules.isBackupDue(now, now.minus(23, ChronoUnit.HOURS), BackupFrequency.Daily) shouldBe true
        ReminderRules.isBackupDue(now, now.minus(20, ChronoUnit.HOURS), BackupFrequency.Daily) shouldBe false
        ReminderRules.isBackupDue(now, now.minus(3, ChronoUnit.DAYS), BackupFrequency.Weekly) shouldBe false
    }

    @Test
    fun `old keys are pruned`() {
        val keys = setOf("card:2026-01-01:a:today", "card:2026-10-01:a:today", "weird")
        ReminderRules.pruneKeys(keys, LocalDate.of(2026, 10, 6)) shouldBe
            setOf("card:2026-10-01:a:today", "weird")
    }
}
