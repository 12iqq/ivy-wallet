package com.ivy.domain.utilities

import com.ivy.data.db.entity.UtilityBillEntity
import java.time.LocalDate

/** The provider confirming money it received for one or more of its [accounts]. */
data class UtilityPayment(
    val provider: String,
    val accounts: List<String>,
    val amount: Double,
    val date: LocalDate,
)

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

    /** "Thank You! We have received AED 475.00 for account(s) 0200… on 2026-10-03." */
    fun parsePayment(sender: String, body: String): UtilityPayment? {
        // every text passes through here: only the provider's own are searched
        val match = if (sender.contains(SEWA, ignoreCase = true)) SewaReceipt.find(body) else null
        val (amount, accounts, date) = match?.destructured ?: return null
        val value = amount.replace(",", "").toDoubleOrNull()
        val day = runCatching { LocalDate.parse(date) }.getOrNull()
        return if (value == null || day == null) {
            null
        } else {
            UtilityPayment(
                provider = SEWA,
                accounts = accounts.split(',', ' ').map { it.trim() }.filter { it.isNotEmpty() },
                amount = value,
                date = day,
            )
        }
    }

    /** The provider a bank's text names as the shop: "SEWA" in "SEWA PAYMENT". */
    fun providerIn(text: String?): String? = senders.firstOrNull { text?.contains(it, ignoreCase = true) == true }

    /** Account numbers as the provider writes them, with or without the leading zeros. */
    fun sameAccount(a: String, b: String): Boolean = a.trimStart('0') == b.trimStart('0')

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
    private val SewaReceipt = Regex(
        """received\s+AED\s*([\d,]+(?:\.\d+)?)\s+for\s+account\(s\)\s*([\d,\s]+?)\s+on\s+(\d{4}-\d{2}-\d{2})""",
        RegexOption.IGNORE_CASE
    )
}
