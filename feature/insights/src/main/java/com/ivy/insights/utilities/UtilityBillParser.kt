package com.ivy.insights.utilities

import com.ivy.data.db.entity.UtilityBillEntity

/**
 * Reads utility bills from the provider's SMS: SEWA (Sharjah) for now. Another provider
 * (e.g. DEWA) is another sender in [senders] plus a parser for its bill text.
 */
object UtilityBillParser {
    const val SEWA = "SEWA"

    /** Senders whose texts are read; matched loosely, as some phones prefix the name. */
    val senders: List<String> = listOf(SEWA)

    fun parse(sender: String, body: String): UtilityBillEntity? = when {
        sender.contains(SEWA, ignoreCase = true) -> parseSewa(body)
        else -> null
    }

    /**
     * "Your Utility services bill for the account 0200… billed on 2026-09-17 - 2026-10-17
     * Sharjah Municipality: 18.70  Gas: 20.00  Electricity: 281.45  Water: 61.30  VAT: 16.55
     * Others: 0.00  Arrears: 0.00   Total Outstanding: 398.00  Due Date : 2026-11-04 …"
     *
     * Only amounts: SEWA's texts don't say how many kWh or gallons were used.
     */
    private fun parseSewa(body: String): UtilityBillEntity? {
        val (account, start, end) = SewaBill.find(body)?.destructured ?: return null
        fun amount(label: String): Double? =
            Regex("""\b$label\s*:\s*(?:AED\s*)?([\d,]+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
                .find(body)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()

        val electricity = amount("Electricity") ?: 0.0
        val water = amount("Water") ?: 0.0
        val gas = amount("Gas") ?: 0.0
        val municipality = amount("""(?:Sharjah\s+)?Municipality""") ?: 0.0
        val vat = amount("VAT") ?: 0.0
        val others = amount("Others") ?: 0.0
        val arrears = amount("Arrears") ?: 0.0
        return UtilityBillEntity(
            id = "$SEWA:$account:$end",
            provider = SEWA,
            account = account,
            periodStart = start,
            periodEnd = end,
            electricity = electricity,
            water = water,
            gas = gas,
            municipality = municipality,
            vat = vat,
            others = others,
            arrears = arrears,
            total = amount("""Total\s+Outstanding""")
                ?: (electricity + water + gas + municipality + vat + others + arrears),
            dueDate = DueDate.find(body)?.groupValues?.get(1),
            currency = "AED",
        )
    }

    private val SewaBill = Regex(
        """bill for the account\s+(\d+)\s+billed on\s+(\d{4}-\d{2}-\d{2})\s*-\s*(\d{4}-\d{2}-\d{2})""",
        RegexOption.IGNORE_CASE
    )
    private val DueDate = Regex("""Due\s+Date\s*:\s*(\d{4}-\d{2}-\d{2})""", RegexOption.IGNORE_CASE)
}
