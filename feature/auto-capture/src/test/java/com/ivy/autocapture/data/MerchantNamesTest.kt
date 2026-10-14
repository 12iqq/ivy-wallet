package com.ivy.autocapture.data

import io.kotest.matchers.shouldBe
import org.junit.Test

class MerchantNamesTest {

    @Test
    fun `key recognises the same shop despite location and company type`() {
        MerchantNames.key("GALADARI ICE CREAM CO LLC") shouldBe "galadari ice cream"
        MerchantNames.key("Galadari Ice Cream - Dubai") shouldBe "galadari ice cream"
        MerchantNames.key("CARREFOUR MOE DUBAI AE") shouldBe "carrefour moe"
        MerchantNames.key("CARREFOUR MOE ABU DHABI") shouldBe "carrefour moe"
    }

    @Test
    fun `key ignores punctuation and store numbers`() {
        MerchantNames.key("ENOC 1023") shouldBe "enoc"
        MerchantNames.key("AMAZON.AE") shouldBe "amazon"
        MerchantNames.key("AMAZON NOW,DUBAI,AE") shouldBe "amazon now"
    }

    @Test
    fun `different shops of the same brand keep different keys`() {
        (MerchantNames.key("AMAZON NOW") == MerchantNames.key("AMAZON.AE")) shouldBe false
    }

    @Test
    fun `no key without latin letters`() {
        MerchantNames.key("١٢٣") shouldBe null
        MerchantNames.key(null) shouldBe null
        MerchantNames.key("") shouldBe null
    }

    @Test
    fun `tidy makes a readable title`() {
        MerchantNames.tidy("GALADARI ICE CREAM CO LLC") shouldBe "Galadari Ice Cream"
        MerchantNames.tidy("AMAZON NOW,DUBAI,AE") shouldBe "Amazon Now"
        MerchantNames.tidy("BAB DIJAL CAFETERIA") shouldBe "Bab Dijal Cafeteria"
        MerchantNames.tidy("BASKIN-ROBBINS DUBAI MALL") shouldBe "Baskin-Robbins Dubai Mall"
        MerchantNames.tidy("ENOC 1023") shouldBe "Enoc"
    }

    @Test
    fun `tidy keeps a short brand on its own in capitals`() {
        MerchantNames.tidy("KFC DUBAI") shouldBe "KFC"
        MerchantNames.tidy("CARREFOUR MOE DUBAI AE") shouldBe "Carrefour Moe"
    }

    @Test
    fun `tidy keeps names the bank already wrote in mixed case`() {
        MerchantNames.tidy("Amazon.ae") shouldBe "Amazon.ae"
        MerchantNames.tidy("Talabat, Dubai") shouldBe "Talabat"
    }

    @Test
    fun `tidy never returns an empty title`() {
        MerchantNames.tidy("7ELEVEN") shouldBe "7Eleven"
        MerchantNames.tidy("1234") shouldBe "1234"
    }
}
