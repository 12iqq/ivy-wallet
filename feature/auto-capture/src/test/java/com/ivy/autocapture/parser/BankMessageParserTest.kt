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
            "Your Cr.Card XXX1234 was used for AED47.25 on 06/10/2026 14:22:11 at CARREFOUR,DUBAI-AE. " +
                "Avl Cr. limit is AED 9,850.00. For any queries call 600 50 2030"
        )!!

        result.bankId shouldBe "adcb"
        result.direction shouldBe Direction.Debit
        result.amount shouldBe 47.25
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
        // paid from another account
        result.isTransfer shouldBe true
    }

    @Test
    fun `Tabby pay-later orders aren't Tabby Cash spending`() {
        // the instalments are captured from the card that pays them
        listOf(
            "Your purchase of AED 300.00 at Noon was successful. Your first payment of AED 75.00 is paid.",
            "Order of 600.00 AED from Amazon.ae is confirmed. Track your upcoming payments with the Tabby app",
            "Your AED 120.00 purchase at Amazon.ae is confirmed. Track your upcoming payments with the Tabby app",
            "Complete your AED 50 purchase at Dubai Pay on the Tabby app: https://example.com",
            "Your Amazon.ae payment of AED 53.00 will be charged to your card tomorrow.",
            "We've received your payment of AED 130.00",
            "To complete your Tabby Cash Card payment at Vox Cinemas for AED 98.00, confirm it in the Tabby app",
        ).forEach { parse("Tabby", it) shouldBe null }
    }

    @Test
    fun `Tabby Cash card spend`() {
        val result = parse(
            "Tabby",
            "You spent AED 58.40 at TAP*Keeta. Your available Tabby Cash balance is now AED 412.60."
        )!!

        result.bankId shouldBe "tabby"
        result.direction shouldBe Direction.Debit
        result.amount shouldBe 58.4
        result.merchant shouldBe "TAP*Keeta"
        result.isTransfer shouldBe false
    }

    @Test
    fun `Tabby Cash top-up is a transfer from the card`() {
        val result = parse(
            "Tabby",
            "AED 500 was added to your account using card ending 1234. Your new balance is AED 836.10."
        )!!

        result.direction shouldBe Direction.Credit
        result.amount shouldBe 500.0
        result.isTransfer shouldBe true
        result.otherLast4 shouldBe "1234"
        // the card is where the money came from, not this account
        result.last4 shouldBe null
    }

    @Test
    fun `Tabby Cash money sent and received`() {
        val received = parse("Tabby", "You received 264.80 AED from JOHN SMITH. Your new balance is AED 264.80.")!!
        received.direction shouldBe Direction.Credit
        received.amount shouldBe 264.8
        received.merchant shouldBe "JOHN SMITH"

        val sent = parse("Tabby", "You sent AED 61.50 to JOHN SMITH.  \nYour new balance is AED 702.45.")!!
        sent.direction shouldBe Direction.Debit
        sent.amount shouldBe 61.5
    }

    @Test
    fun `DIB card purchase names the shop, not the account`() {
        val old = parse(
            "DIB-SMS",
            "POS Purchase of AED:   39.00 from a/c no.  012-345-6789***-01 on 01-NOV-2022 14:40 from KFC SAHARA. " +
                "Available balance is AED 10,245.30"
        )!!
        old.direction shouldBe Direction.Debit
        old.amount shouldBe 39.0
        old.merchant shouldBe "KFC SAHARA"
        old.isCreditCard shouldBe false

        val online = parse(
            "DIB-SMS",
            "Online Purchase of AED 27.15 from a/c no. 012-345-6789***-01 on 08-JUL-2026 17:19 from Amazon Now. " +
                "Available balance is AED 4,120.35"
        )!!
        online.amount shouldBe 27.15
        online.merchant shouldBe "Amazon Now"

        val foreign = parse(
            "DIB-SMS",
            "Online Purchase of USD 25.00 from a/c no. 012-345-6789***-01 on 13-APR-2026 06:21 from EXAMPLE.ORG. " +
                "Available balance is AED 30,480.55"
        )!!
        foreign.currency shouldBe "USD"
        foreign.merchant shouldBe "EXAMPLE.ORG"
    }

    @Test
    fun `DIB account debits use the bank's description`() {
        val bill = parse(
            "DIB-SMS",
            "AED 437.00 has been drawn from A/C No. 012-345-678XXXX-01 as SEWA PAYMENT-001UPSW22177. " +
                "Available balance is  6,512.40"
        )!!
        bill.direction shouldBe Direction.Debit
        bill.amount shouldBe 437.0
        bill.merchant shouldBe "SEWA PAYMENT"

        parse(
            "DIB-SMS",
            "AED 395.20 has been drawn from A/C No. 012-345-678XXXX-01 as ETISALAT PAYMENT-MBA : 012345678. " +
                "Available balance is  8,104.65"
        )!!.merchant shouldBe "ETISALAT PAYMENT"

        parse(
            "DIB-SMS",
            "AED 5,500.00 has been drawn from A/C No. 012-345-678XXXX-01 as CHQ:100004-I/W CLEARING C. " +
                "Available balance is  12,310.75"
        )!!.merchant shouldBe "I/W CLEARING C"

        val atm = parse(
            "DIB-SMS",
            "AED 700.00 has been withdrawn from your A/C no. 012-345-6789***-01 on 01-JUL-2026 " +
                "as ATM CASH WITHDRAWAL. Available balance is  5,870.20"
        )!!
        atm.direction shouldBe Direction.Debit
        atm.merchant shouldBe "ATM CASH WITHDRAWAL"
    }

    @Test
    fun `DIB account credits and salary`() {
        val deposit = parse(
            "DIB-SMS",
            "AED 4,500.00 has been credited to your a/c 012-345-678XXXX-01 as CDM CASH DEPOSIT. " +
                "Available balance is  34,905.15"
        )!!
        deposit.direction shouldBe Direction.Credit
        deposit.amount shouldBe 4500.0
        deposit.merchant shouldBe "CDM CASH DEPOSIT"

        val salary = parse(
            "DIB-SMS",
            "Salary amount of AED  6,000.00 credited to your A/C no. 012-345-6789***-01 on 01-MAR-2023. " +
                "Available balance is  18,215.60"
        )!!
        salary.direction shouldBe Direction.Credit
        salary.amount shouldBe 6000.0
        salary.merchant shouldBe "Salary"
    }

    @Test
    fun `paying a credit card from DIB is a transfer`() {
        val result = parse(
            "DIB-SMS",
            "AED 1,234.56 has been debited from A/C No. 012-345-678XXXX-01 for Card Payment. " +
                "Available balance is AED 2,150.40"
        )!!

        result.direction shouldBe Direction.Debit
        result.amount shouldBe 1234.56
        result.merchant shouldBe "Card Payment"
        result.isTransfer shouldBe true
    }

    @Test
    fun `DIB reversal names the shop`() {
        val result = parse(
            "DIB-SMS",
            "A transaction to the value of AED 1.00 made at Smart Dubai Governme on your DIB card ending with XX1234 " +
                "has been reversed."
        )!!

        result.direction shouldBe Direction.Credit
        result.merchant shouldBe "Smart Dubai Governme"
        result.last4 shouldBe "1234"
    }

    @Test
    fun `ADCB credit card purchase drops the city`() {
        val result = parse(
            "ADCBAlert",
            "Credit Card XX1234 was used for AED46.00 on 27/08/2026 23:29:18 at Vox Cinemas, Dubai-AE. " +
                "Available limit  AED3908.00"
        )!!

        result.merchant shouldBe "Vox Cinemas"
        result.last4 shouldBe "1234"
        result.amount shouldBe 46.0

        parse(
            "ADCBAlert",
            "Congratulations on your first 3D secure transaction. Your Credit Card xxx 1234 was used for AED 46.00 " +
                "on 27/08/2026 23:22:21 at Vox Cinemas,Dubai- AE. Available credit limit is now AED 3954.00."
        )!!.merchant shouldBe "Vox Cinemas"
    }

    @Test
    fun `ADCB bill paid from the card, reversal and card payment`() {
        val bill = parse(
            "ADCBAlert",
            "AED612.00 debited from Acc/Cr.Card XXX1234 for SEWA on 02-09-2026 13:28:59 through ADCB Mobile App." +
                "Avl.Limit is AED 2875.30"
        )!!
        bill.direction shouldBe Direction.Debit
        bill.amount shouldBe 612.0
        bill.merchant shouldBe "SEWA"
        bill.last4 shouldBe "1234"

        val reversal = parse(
            "ADCBAlert",
            "An amount of AED46.00 has been reversed to your Credit Card XXX1234 on 28/08/2026 00:00:43 " +
                "by Vox Cinemas,Dubai-AE."
        )!!
        reversal.direction shouldBe Direction.Credit
        reversal.merchant shouldBe "Vox Cinemas"

        val payment = parse(
            "ADCBAlert",
            "Your payment of AED 1234.56 against Credit Card no. XXX1234 was received at 03:32 PM " +
                "on 08/09/2026. Thank you."
        )!!
        payment.direction shouldBe Direction.Credit
        payment.amount shouldBe 1234.56
        payment.last4 shouldBe "1234"
        payment.isTransfer shouldBe true
    }

    @Test
    fun `card statements and approvals are ignored`() {
        parse(
            "ADCBAlert",
            "Cr.Card XXX1234 Billing alert: Total due to avoid fin. charges: AED1234.56. Due date Sep 30 2026; " +
                "Pay min. AED100.00 by due date to avoid AED250.00 late fees."
        ) shouldBe null
        parse("DIB-SMS", "Your card payment of AED 500.00 is due by 25/10/2026.") shouldBe null
        parse(
            "EI SMS",
            "A payment on your card needs your confirmation. " +
                "Please access the EI+ app and proceed to the Activities section."
        ) shouldBe null
    }

    @Test
    fun `Emirates Islamic purchase`() {
        val result = parse(
            "EI SMS",
            "Credit Card Purchase\rCard Ending: : 1234\rAt: AMAZON RETAIL, Dubai\rAmount: AED 29.99\r" +
                "Date: 03/10/2026, 20:32\rAvailable Limit: AED 12,480.25\r"
        )!!

        result.bankId shouldBe "eib"
        result.direction shouldBe Direction.Debit
        result.amount shouldBe 29.99
        result.merchant shouldBe "AMAZON RETAIL"
        result.last4 shouldBe "1234"
        result.isCreditCard shouldBe true
    }

    @Test
    fun `Emirates Islamic card payment received`() {
        val result = parse(
            "EI SMS",
            "This is to confirm receipt of your payment of AED 345.67 towards your Credit Card starting with 512345  " +
                "on 15/10/2026. Available limit is   AED 12,600.00."
        )!!

        result.direction shouldBe Direction.Credit
        result.amount shouldBe 345.67
        result.isTransfer shouldBe true
        result.last4 shouldBe null
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
            "Your Cr.Card XXX1234 was used for AED47.25 on 06/10/2026 at CARREFOUR",
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
        router.bankFor("ADCBAlert", allBanks)?.id shouldBe "adcb"
        router.bankFor("adib", allBanks)?.id shouldBe "adib"
        router.bankFor("DIB-SMS", allBanks)?.id shouldBe "dib"
        router.bankFor("EI SMS", allBanks)?.id shouldBe "eib"
    }

    @Test
    fun `thank you for a bill payment is still a debit`() {
        val parsed = GenericMessageParser.parse(
            UaeBanks.DIB,
            "Thank you. Your payment of AED 300.00 to DEWA from account XXXX1234 has been processed."
        )!!
        parsed.direction shouldBe Direction.Debit
        parsed.amount shouldBe 300.0
    }

    @Test
    fun `thank you for your card payment is a credit`() {
        val parsed = GenericMessageParser.parse(
            UaeBanks.EIB,
            "Thank you for your payment of AED 1,500.00 towards your credit card ending 4321."
        )!!
        parsed.direction shouldBe Direction.Credit
    }
}
