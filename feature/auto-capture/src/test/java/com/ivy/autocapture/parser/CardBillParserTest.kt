package com.ivy.autocapture.parser

import io.kotest.matchers.shouldBe
import org.junit.Test
import java.time.LocalDate

class CardBillParserTest {

    @Test
    fun `ADCB billing alert`() {
        val bill = CardBillParser.parse(
            "adcb",
            "Cr.Card XXX1234 Billing alert: Total due to avoid fin. charges: AED1234.56. Due date Oct 30 2026; " +
                "Pay min. AED100.00 by due date to avoid AED250.00 late fees."
        )

        bill shouldBe CardBill(
            bankId = "adcb",
            last4 = "1234",
            currency = "AED",
            totalDue = 1234.56,
            minimumDue = 100.0,
            dueDate = LocalDate.of(2026, 10, 30),
        )
    }

    @Test
    fun `other wordings and date formats`() {
        val numeric = CardBillParser.parse(
            "enbd",
            "Your Credit Card ending 5678 statement is ready. Total amount due AED 2,345.00, " +
                "minimum amount due AED 117.25. Payment due date: 05/11/2026."
        )!!
        numeric.last4 shouldBe "5678"
        numeric.totalDue shouldBe 2345.0
        numeric.minimumDue shouldBe 117.25
        numeric.dueDate shouldBe LocalDate.of(2026, 11, 5)

        CardBillParser.parse("fab", "Card XX9012: Total due AED 80.00. Due date 30-Nov-26")!!
            .dueDate shouldBe LocalDate.of(2026, 11, 30)
        CardBillParser.parse("fab", "Card XX9012: Total due AED 80.00, due on September 3, 2026")!!
            .dueDate shouldBe LocalDate.of(2026, 9, 3)
    }

    @Test
    fun `not a bill`() {
        // a purchase, a payment received, a reminder without the total, nothing due
        CardBillParser.parse(
            "adcb",
            "Your Cr.Card XXX1234 was used for AED45.00 on 06/10/2026 14:22:11 at CARREFOUR,DUBAI-AE."
        ) shouldBe null
        CardBillParser.parse(
            "adcb",
            "Your payment of AED 500.00 against Credit Card no. XXX1234 was received at 10:15 AM on 05/10/2026."
        ) shouldBe null
        CardBillParser.parse("dib", "Your card payment of AED 500.00 is due by 25/10/2026.") shouldBe null
        CardBillParser.parse(
            "adcb",
            "Cr.Card XXX1234 Billing alert: Total due to avoid fin. charges: AED0.00. Due date Oct 30 2026"
        ) shouldBe null
        // a loan isn't a card
        CardBillParser.parse("dib", "Total due AED 1,500.00 on your finance. Due date 01/11/2026") shouldBe null
    }
}
