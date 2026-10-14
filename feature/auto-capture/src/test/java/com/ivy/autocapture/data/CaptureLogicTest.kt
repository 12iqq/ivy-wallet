package com.ivy.autocapture.data

import io.kotest.matchers.shouldBe
import org.junit.Test

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
    fun `merchant key ignores case, punctuation and branch numbers`() {
        CaptureLogic.merchantKey("CARREFOUR, DUBAI MALL 123") shouldBe "carrefour dubai mall"
        CaptureLogic.merchantKey("Carrefour Dubai-Mall") shouldBe "carrefour dubai mall"
    }

    @Test
    fun `same message gives the same fingerprint`() {
        CaptureLogic.fingerprint("ADCB", "text ") shouldBe CaptureLogic.fingerprint("adcb", "text")
    }
}
