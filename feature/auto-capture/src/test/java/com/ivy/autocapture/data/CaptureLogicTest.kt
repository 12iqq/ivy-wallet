package com.ivy.autocapture.data

import com.ivy.base.model.TransactionType
import com.ivy.data.db.entity.CapturedTransactionEntity
import io.kotest.matchers.shouldBe
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID

class CaptureLogicTest {

    private val links = listOf(
        CardLink(bankId = "adcb", last4 = "1234", accountId = "adcb-card"),
        CardLink(bankId = "dib", last4 = null, accountId = "dib-main"),
        CardLink(bankId = "dib", last4 = "9999", accountId = "dib-savings"),
        CardLink(bankId = "eib", last4 = "5555", accountId = "eib-amazon"),
    )

    @Test
    fun `exact card match wins`() {
        CaptureLogic.linkedAccountId(links, "dib", "9999") shouldBe "dib-savings"
    }

    @Test
    fun `falls back to the bank's catch-all link`() {
        CaptureLogic.linkedAccountId(links, "dib", "0000") shouldBe "dib-main"
        CaptureLogic.linkedAccountId(links, "dib", null) shouldBe "dib-main"
    }

    @Test
    fun `single link is used when the message has no card number`() {
        CaptureLogic.linkedAccountId(links, "eib", null) shouldBe "eib-amazon"
    }

    @Test
    fun `different card of a bank without catch-all is not guessed`() {
        CaptureLogic.linkedAccountId(links, "adcb", "4321") shouldBe null
    }

    @Test
    fun `unknown bank has no account`() {
        CaptureLogic.linkedAccountId(links, "enbd", "1234") shouldBe null
    }

    @Test
    fun `sms are received as they arrive by default`() {
        val config = AutoCaptureConfig(mode = AutoCaptureMode.Sms)

        CaptureLogic.receivesSms(config) shouldBe true
        CaptureLogic.checksSmsOnSchedule(config) shouldBe false
    }

    @Test
    fun `checking on a schedule switches the sms receiver and wallet listener off`() {
        val config = AutoCaptureConfig(
            mode = AutoCaptureMode.Both,
            smsCheck = SmsCheck.Every6Hours,
            readWalletNotifications = true,
        )

        CaptureLogic.receivesSms(config) shouldBe false
        CaptureLogic.checksSmsOnSchedule(config) shouldBe true
        CaptureLogic.listensToWallet(config) shouldBe false
    }

    @Test
    fun `nothing runs in the background when auto-add doesn't read sms`() {
        listOf(AutoCaptureMode.Off, AutoCaptureMode.Statement).forEach { mode ->
            val config = AutoCaptureConfig(
                mode = mode,
                smsCheck = SmsCheck.Daily,
                readWalletNotifications = true,
            )

            CaptureLogic.receivesSms(config) shouldBe false
            CaptureLogic.checksSmsOnSchedule(config) shouldBe false
            CaptureLogic.listensToWallet(config) shouldBe false
        }
    }

    @Test
    fun `wallet listener only runs while wallet capture is on`() {
        val config = AutoCaptureConfig(mode = AutoCaptureMode.Sms)

        CaptureLogic.listensToWallet(config) shouldBe false
        CaptureLogic.listensToWallet(config.copy(readWalletNotifications = true)) shouldBe true
    }

    @Test
    fun `account endings move to the bank already linked to that account`() {
        val config = AutoCaptureConfig(
            enabledBankIds = setOf("dib", "adcb"),
            links = listOf(CardLink("dib", null, "dib-main")),
        )

        val moved = CaptureLogic.withAccountEndings(config, mapOf("dib-main" to listOf("4321")))

        moved.links shouldBe listOf(CardLink("dib", null, "dib-main"), CardLink("dib", "4321", "dib-main"))
        moved.accountEndingsMoved shouldBe true
    }

    @Test
    fun `account endings of an unlinked account go to every enabled bank`() {
        val config = AutoCaptureConfig(enabledBankIds = setOf("dib", "adcb"))

        val moved = CaptureLogic.withAccountEndings(config, mapOf("liv" to listOf("1111")))

        moved.links shouldBe listOf(CardLink("adcb", "1111", "liv"), CardLink("dib", "1111", "liv"))
    }

    @Test
    fun `existing links are kept when moving account endings`() {
        val config = AutoCaptureConfig(
            enabledBankIds = setOf("adcb"),
            links = listOf(CardLink("adcb", "1234", "adcb-card")),
        )

        val moved = CaptureLogic.withAccountEndings(config, mapOf("other" to listOf("1234")))

        moved.links shouldBe config.links
        moved.accountEndingsMoved shouldBe true
    }

    @Test
    fun `account endings wait until a bank is enabled`() {
        val config = AutoCaptureConfig()

        CaptureLogic.withAccountEndings(config, mapOf("liv" to listOf("1111"))) shouldBe config
        CaptureLogic.withAccountEndings(config, emptyMap()).accountEndingsMoved shouldBe true
    }

    @Test
    fun `same message gives the same fingerprint`() {
        CaptureLogic.fingerprint("ADCB", "text ") shouldBe CaptureLogic.fingerprint("adcb", "text")
    }

    private val bank = UUID.randomUUID()
    private val card = UUID.randomUUID()
    private val wallet = UUID.randomUUID()
    private val noon = Instant.parse("2026-09-08T08:00:00Z")

    @Suppress("LongParameterList")
    private fun captured(
        type: TransactionType,
        account: UUID?,
        amount: Double,
        at: Instant,
        isTransfer: Boolean = false,
        status: String = CapturedTransactionEntity.STATUS_PENDING,
        toAccount: UUID? = null,
    ) = CapturedTransactionEntity(
        source = CapturedTransactionEntity.SOURCE_SMS,
        sender = "bank",
        rawText = "",
        bankId = "bank",
        type = type,
        amount = amount,
        currency = "AED",
        merchant = null,
        last4 = null,
        isCreditCard = null,
        receivedAt = at,
        accountId = account,
        categoryId = null,
        status = status,
        transactionId = null,
        fingerprint = UUID.randomUUID().toString(),
        toAccountId = toAccount,
        isTransfer = isTransfer,
    )

    @Test
    fun `card payment joins the card's payment received`() {
        val fromBank = captured(TransactionType.EXPENSE, bank, 1234.56, noon, isTransfer = true)
        val atCard = captured(TransactionType.INCOME, card, 1234.56, noon.plusSeconds(360), isTransfer = true)

        CaptureLogic.otherHalf(atCard, listOf(fromBank)) shouldBe fromBank
        CaptureLogic.transferAccounts(atCard, fromBank) shouldBe (bank to card)
        // either order
        CaptureLogic.transferAccounts(fromBank, atCard) shouldBe (bank to card)
    }

    @Test
    fun `card confirmation a day later still joins`() {
        val fromBank = captured(TransactionType.EXPENSE, bank, 345.67, noon, isTransfer = true)
        val atCard = captured(TransactionType.INCOME, card, 345.67, noon.plus(Duration.ofHours(25)), isTransfer = true)

        CaptureLogic.otherHalf(atCard, listOf(fromBank)) shouldBe fromBank
    }

    @Test
    fun `top-up joins the plain card charge for it`() {
        val charge = captured(
            TransactionType.EXPENSE,
            bank,
            500.0,
            noon,
            status = CapturedTransactionEntity.STATUS_ADDED
        )
        val topUp = captured(TransactionType.INCOME, wallet, 500.0, noon.plusSeconds(4000), isTransfer = true)

        CaptureLogic.otherHalf(topUp, listOf(charge)) shouldBe charge
        CaptureLogic.transferAccounts(topUp, charge) shouldBe (bank to wallet)
    }

    @Test
    fun `charge after a top-up already made a transfer is its other end`() {
        val transfer = captured(TransactionType.TRANSFER, bank, 500.0, noon, isTransfer = true, toAccount = wallet)
        val charge = captured(TransactionType.EXPENSE, bank, 500.0, noon.plusSeconds(4000))

        CaptureLogic.otherHalf(charge, listOf(transfer)) shouldBe transfer
        // but a charge of the same amount days later is a new purchase
        CaptureLogic.otherHalf(charge.copy(receivedAt = noon.plus(Duration.ofDays(2))), listOf(transfer)) shouldBe null
    }

    @Test
    fun `ordinary spending and income never join`() {
        val spend = captured(TransactionType.EXPENSE, bank, 50.0, noon)
        val income = captured(TransactionType.INCOME, wallet, 50.0, noon.plusSeconds(60))

        CaptureLogic.otherHalf(income, listOf(spend)) shouldBe null
    }

    @Test
    fun `no join on the same account, a different amount or a handled message`() {
        val atCard = captured(TransactionType.INCOME, card, 100.0, noon, isTransfer = true)

        val sameAccount = captured(TransactionType.EXPENSE, card, 100.0, noon, isTransfer = true)
        CaptureLogic.otherHalf(atCard, listOf(sameAccount)) shouldBe null
        val otherAmount = captured(TransactionType.EXPENSE, bank, 99.0, noon, isTransfer = true)
        CaptureLogic.otherHalf(atCard, listOf(otherAmount)) shouldBe null
        val skipped = captured(
            TransactionType.EXPENSE,
            bank,
            100.0,
            noon,
            isTransfer = true,
            status = CapturedTransactionEntity.STATUS_DISMISSED
        )
        CaptureLogic.otherHalf(atCard, listOf(skipped)) shouldBe null
    }

    @Test
    fun `the closest message wins`() {
        val near = captured(TransactionType.EXPENSE, bank, 500.0, noon.minusSeconds(600), isTransfer = true)
        val far = captured(TransactionType.EXPENSE, bank, 500.0, noon.minus(Duration.ofDays(2)), isTransfer = true)
        val atCard = captured(TransactionType.INCOME, card, 500.0, noon, isTransfer = true)

        CaptureLogic.otherHalf(atCard, listOf(far, near)) shouldBe near
    }
}
