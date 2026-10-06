package com.ivy.autocapture.parser

import io.kotest.matchers.shouldBe
import org.junit.Test

/**
 * Sample texts follow the typical wording of UAE bank alerts. Real messages vary,
 * so when a bank changes its format add the new sample here.
 */
class BankMessageParserTest {

    private val router = BankMessageRouter()
    private val allBanks = (UaeBanks.all + UaeBanks.OTHER).map { it.id }.toSet()

    private fun parse(sender: String, body: String) = router.parse(sender, body, allBanks)

    @Test
    fun `ADCB credit card purchase`() {
        val result = parse(
            "ADCB",
            "Your Cr.Card XXX1234 was used for AED45.50 on 06/10/2026 14:22:11 at CARREFOUR,DUBAI-AE. " +
                "Avl Cr. limit is AED 9,850.00. For any queries call 600 50 2030"
        )!!

        result.bankId shouldBe "adcb"
        result.direction shouldBe Direction.Debit
        result.amount shouldBe 45.50
        result.currency shouldBe "AED"
        result.merchant shouldBe "CARREFOUR"
        result.last4 shouldBe "1234"
        result.isCreditCard shouldBe true
        result.availableBalance shouldBe 9850.0
    }

    @Test
    fun `DIB debit card purchase`() {
        val result = parse(
            "DIB",
            "Purchase of AED 25.00 at TALABAT with Debit Card ending 4321 on 06-Oct-2026 13:05. " +
                "Available balance AED 1,234.56"
        )!!

        result.direction shouldBe Direction.Debit
        result.amount shouldBe 25.0
        result.merchant shouldBe "TALABAT"
        result.last4 shouldBe "4321"
        result.isCreditCard shouldBe false
        result.availableBalance shouldBe 1234.56
    }

    @Test
    fun `DIB salary credited`() {
        val result = parse(
            "DIB",
            "AED 15,000.00 has been credited to your account XXXX5678 on 28/09/2026. " +
                "Available balance is AED 16,234.56"
        )!!

        result.direction shouldBe Direction.Credit
        result.amount shouldBe 15000.0
        result.last4 shouldBe "5678"
    }

    @Test
    fun `EIB credit card purchase`() {
        val result = parse(
            "EIB",
            "Your Emirates Islamic Credit Card ending 9876 has been used for AED 199.00 at AMAZON.AE " +
                "on 06/10/26. Available limit AED 14,801.00"
        )!!

        result.bankId shouldBe "eib"
        result.direction shouldBe Direction.Debit
        result.amount shouldBe 199.0
        result.merchant shouldBe "AMAZON.AE"
        result.last4 shouldBe "9876"
        result.isCreditCard shouldBe true
    }

    @Test
    fun `credit card payment received is a credit`() {
        val result = parse(
            "EIB",
            "Payment of AED 2,000.00 received towards your Credit Card ending 9876. Thank you."
        )!!

        result.direction shouldBe Direction.Credit
        result.amount shouldBe 2000.0
        result.isCreditCard shouldBe true
    }

    @Test
    fun `Tabby purchase`() {
        val result = parse(
            "tabby",
            "Your purchase of AED 300.00 at Noon was successful. Your first payment of AED 75.00 is paid."
        )!!

        result.bankId shouldBe "tabby"
        result.direction shouldBe Direction.Debit
        result.amount shouldBe 300.0
        result.merchant shouldBe "Noon"
    }

    @Test
    fun `ENBD purchase with currency after amount`() {
        val result = parse(
            "EmiratesNBD",
            "Purchase of 120.00 AED with Credit Card ending 1111 at IKEA DUBAI FESTIVAL CITY. " +
                "Avl Cr. Limit is 8,880.00 AED"
        )!!

        result.amount shouldBe 120.0
        result.currency shouldBe "AED"
        result.merchant shouldBe "IKEA DUBAI FESTIVAL CITY"
        result.availableBalance shouldBe 8880.0
    }

    @Test
    fun `foreign currency purchase keeps its currency`() {
        val result = parse(
            "FAB",
            "Your FAB Credit Card XXXX2222 was used for USD 15.99 at NETFLIX.COM on 05/10/2026."
        )!!

        result.amount shouldBe 15.99
        result.currency shouldBe "USD"
        result.merchant shouldBe "NETFLIX.COM"
    }

    @Test
    fun `refund is a credit`() {
        val result = parse(
            "ADCB",
            "A refund of AED 89.00 from NOON has been credited to your Credit Card XXX1234."
        )!!

        result.direction shouldBe Direction.Credit
        result.amount shouldBe 89.0
    }

    @Test
    fun `OTP messages are ignored`() {
        parse(
            "ADCB",
            "123456 is your OTP for the transaction of AED 45.00 at AMAZON. Do not share it with anyone."
        ) shouldBe null
    }

    @Test
    fun `declined transactions are ignored`() {
        parse(
            "DIB",
            "Your transaction of AED 500.00 at XYZ STORE was declined due to insufficient balance."
        ) shouldBe null
    }

    @Test
    fun `payment due reminders are ignored`() {
        parse(
            "EIB",
            "Your Credit Card payment of AED 3,240.00 is due on 25/10/2026. Minimum payment AED 162.00"
        ) shouldBe null
    }

    @Test
    fun `unknown senders are ignored`() {
        parse("AMAZON", "You paid AED 50.00 at Amazon") shouldBe null
    }

    @Test
    fun `disabled banks are ignored`() {
        router.parse(
            "ADCB",
            "Your Cr.Card XXX1234 was used for AED45.50 on 06/10/2026 at CARREFOUR",
            enabledBankIds = setOf("dib")
        ) shouldBe null
    }

    @Test
    fun `user-added sender ids work`() {
        val result = router.parse(
            "MyBankAlerts",
            "Purchase of AED 10.00 at STARBUCKS with card ending 1212",
            enabledBankIds = setOf("other"),
            extraSenders = mapOf("other" to listOf("MyBankAlerts"))
        )!!

        result.bankId shouldBe "other"
        result.amount shouldBe 10.0
        result.merchant shouldBe "STARBUCKS"
    }

    @Test
    fun `sender normalization`() {
        router.bankFor("AD-Emirates NBD", allBanks)?.id shouldBe "enbd"
        router.bankFor("ADCBAlerts", allBanks)?.id shouldBe "adcb"
        router.bankFor("adib", allBanks)?.id shouldBe "adib"
    }
}
