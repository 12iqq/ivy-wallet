package com.ivy.data.model

import io.kotest.matchers.shouldBe
import org.junit.Test

class CardEndingsTest {

    @Test
    fun `keeps the last 4 digits of each number`() {
        CardEndings.parse("1234, 5678 9012") shouldBe listOf("1234", "5678", "9012")
        CardEndings.parse("XXXX1234 / 98765678") shouldBe listOf("1234", "5678")
    }

    @Test
    fun `ignores short or empty parts and duplicates`() {
        CardEndings.parse("12, 1234,1234,,") shouldBe listOf("1234")
        CardEndings.parse(null) shouldBe emptyList()
        CardEndings.parse("") shouldBe emptyList()
    }

    @Test
    fun `formats for storage`() {
        CardEndings.format(listOf("1234", "5678")) shouldBe "1234,5678"
        CardEndings.format(emptyList()) shouldBe null
    }
}
