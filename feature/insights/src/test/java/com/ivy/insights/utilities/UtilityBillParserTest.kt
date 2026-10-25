package com.ivy.insights.utilities

import io.kotest.matchers.shouldBe
import org.junit.Test

class UtilityBillParserTest {

    private val bill = "Dear Customer, Your Utility services bill for the account 020000123456 billed on " +
        "2026-09-17 - 2026-10-17 Sharjah Municipality: 18.70  Gas: 20.00  Electricity: 281.45  " +
        "Water: 61.30  VAT: 16.55  Others: 0.00  Arrears: 0.00   Total Outstanding: 398.00  " +
        "Due Date : 2026-11-04  Click here to view bill https://example.com/b  FOR QUICKPAY, " +
        "click here https://example.com/p"

    @Test
    fun `reads a SEWA bill`() {
        val parsed = UtilityBillParser.parse("SEWA", bill)!!

        parsed.id shouldBe "SEWA:020000123456:2026-10-17"
        parsed.provider shouldBe "SEWA"
        parsed.account shouldBe "020000123456"
        parsed.periodStart shouldBe "2026-09-17"
        parsed.periodEnd shouldBe "2026-10-17"
        parsed.electricity shouldBe 281.45
        parsed.water shouldBe 61.30
        parsed.gas shouldBe 20.0
        parsed.municipality shouldBe 18.70
        parsed.vat shouldBe 16.55
        parsed.others shouldBe 0.0
        parsed.arrears shouldBe 0.0
        parsed.total shouldBe 398.0
        parsed.dueDate shouldBe "2026-11-04"
        parsed.currency shouldBe "AED"
    }

    @Test
    fun `the same bill read twice gets the same id`() {
        UtilityBillParser.parse("SEWA", bill)!!.id shouldBe UtilityBillParser.parse("sewa", bill)!!.id
    }

    @Test
    fun `other SEWA texts are not bills`() {
        listOf(
            "Thank You! We have received AED 475.00 for account(s) 020000123456 on 2026-10-03. " +
                "Get your payment receipt https://example.com/r",
            "Dear Customer, kindly be informed that your Customer Services->Services->Gas Conversion " +
                "Residential with reference 8001000001 is approved, kindly clear the payment of 250.00, " +
                "under account 020000123456.",
            "Dear Customer, Account 020000123456 is liable to pay \"Security Deposit\" amount AED 1750.00",
            "Dear customer, you can apply for Natural Gas Connection now!",
            "Dear Customer, Use verification code 123456 for authentication. - SEWA",
        ).forEach { UtilityBillParser.parse("SEWA", it) shouldBe null }
    }

    @Test
    fun `only the provider's own texts count`() {
        UtilityBillParser.parse("DIB-SMS", bill) shouldBe null
        UtilityBillParser.parse("AD-SEWA", bill)?.account shouldBe "020000123456"
    }

    @Test
    fun `thousands separators and arrears`() {
        val parsed = UtilityBillParser.parse(
            "SEWA",
            "Your Utility services bill for the account 020000123456 billed on 2026-07-17 - 2026-08-17 " +
                "Sharjah Municipality: 21.40  Gas: 18.90  Electricity: 1,204.50  Water: 72.15  VAT: 65.62  " +
                "Others: 1.88  Arrears: 245.00   Total Outstanding: 1,610.00  Due Date : 2026-09-04"
        )!!

        parsed.electricity shouldBe 1204.50
        parsed.others shouldBe 1.88
        parsed.arrears shouldBe 245.0
        parsed.total shouldBe 1610.0
    }

    @Test
    fun `total is worked out when the text leaves it out`() {
        val parsed = UtilityBillParser.parse(
            "SEWA",
            "Your Utility services bill for the account 020000123456 billed on 2026-07-17 - 2026-08-17 " +
                "Sharjah Municipality: 10.00  Gas: 20.00  Electricity: 100.00  Water: 50.00  VAT: 9.00"
        )!!

        parsed.total shouldBe 189.0
        parsed.dueDate shouldBe null
    }
}
