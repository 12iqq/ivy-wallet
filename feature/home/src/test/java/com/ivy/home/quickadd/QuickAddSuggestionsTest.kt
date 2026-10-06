package com.ivy.home.quickadd

import io.kotest.matchers.shouldBe
import org.junit.Test

class QuickAddSuggestionsTest {

    private fun rec(
        title: String,
        amount: Double,
        daysAgo: Long,
        category: String? = "groceries",
        account: String = "dib",
    ) = QuickAddRecord(title, category, account, isIncome = false, amount = amount, daysAgo = daysAgo)

    @Test
    fun `needs at least three uses`() {
        val records = listOf(rec("Emarat", 10.0, 1), rec("Emarat", 12.0, 2))
        QuickAddSuggestions.suggest(records) shouldBe emptyList()
    }

    @Test
    fun `groups case-insensitively and uses the median amount`() {
        val records = listOf(
            rec("Emarat", 10.0, 1),
            rec("emarat ", 12.0, 5),
            rec("EMARAT", 50.0, 9),
        )
        val template = QuickAddSuggestions.suggest(records).single()
        template.title shouldBe "Emarat"
        template.typicalAmount shouldBe 12.0
        template.uses shouldBe 3
    }

    @Test
    fun `same title in a different category is a different shortcut`() {
        val records = List(3) { rec("Emarat", 10.0, it.toLong(), category = "groceries") } +
            List(3) { rec("Emarat", 60.0, it.toLong(), category = "fuel") }
        QuickAddSuggestions.suggest(records).size shouldBe 2
    }

    @Test
    fun `recent habits rank above old ones`() {
        val old = List(6) { rec("Old cafe", 15.0, 300L + it) }
        val recent = List(4) { rec("Talabat", 40.0, it.toLong()) }
        QuickAddSuggestions.suggest(old + recent).first().title shouldBe "Talabat"
    }

    @Test
    fun `respects the limit`() {
        val records = (1..20).flatMap { i -> List(3) { rec("Shop $i", 5.0, it.toLong()) } }
        QuickAddSuggestions.suggest(records, limit = 5).size shouldBe 5
    }
}
