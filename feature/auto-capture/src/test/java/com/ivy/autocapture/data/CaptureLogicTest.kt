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
    fun `merchant key ignores case, punctuation and branch numbers`() {
        CaptureLogic.merchantKey("CARREFOUR, DUBAI MALL 123") shouldBe "carrefour dubai mall"
        CaptureLogic.merchantKey("Carrefour Dubai-Mall") shouldBe "carrefour dubai mall"
    }

    @Test
    fun `same message gives the same fingerprint`() {
        CaptureLogic.fingerprint("ADCB", "text ") shouldBe CaptureLogic.fingerprint("adcb", "text")
    }
}
