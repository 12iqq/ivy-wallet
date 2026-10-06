package com.ivy.autocapture.parser

import io.kotest.matchers.shouldBe
import org.junit.Test

class WalletNotificationsTest {

    @Test
    fun `tap to pay with card digits`() {
        val parsed = WalletNotifications.parse("Carrefour", "AED 23.50 with Visa ••1234")!!
        parsed.amount shouldBe 23.50
        parsed.currency shouldBe "AED"
        parsed.direction shouldBe Direction.Debit
        parsed.merchant shouldBe "Carrefour"
        parsed.last4 shouldBe "1234"
        parsed.bankId shouldBe "google_wallet"
    }

    @Test
    fun `amount written after the currency symbol style`() {
        val parsed = WalletNotifications.parse("ENOC", "AED 120.00 · Mastercard •••• 9876")!!
        parsed.amount shouldBe 120.0
        parsed.merchant shouldBe "ENOC"
        parsed.last4 shouldBe "9876"
    }

    @Test
    fun `non payment notifications are ignored`() {
        WalletNotifications.parse("Boarding pass", "Your flight is boarding soon") shouldBe null
        WalletNotifications.parse("Offer", null) shouldBe null
    }
}
