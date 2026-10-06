package com.ivy.autocapture.parser

/**
 * Banks & cards known to the auto-capture. Add new ones here.
 *
 * Sender ids are matched case-insensitively ignoring spaces/dashes, so
 * "Emirates NBD", "EmiratesNBD" and "EMIRATESNBD" are all the same.
 * Users can also add their own sender ids per bank in Settings.
 */
@Suppress("MaxLineLength", "MaximumLineLength")
object UaeBanks {
    val ADCB = BankProfile(
        id = "adcb",
        displayName = "ADCB",
        senders = listOf("ADCB", "ADCBAlerts", "com.adcb.bank"),
        patterns = listOf(
            // "Your Cr.Card XXX1234 was used for AED45.00 on 06/10/2026 14:22:11 at CARREFOUR,DUBAI-AE."
            MessagePattern(
                regex = Regex(
                    """(?i)Cr\.?\s*Card\s+[X*]*(?<last4>\d{4})\s+was used for\s+(?<currency>[A-Z]{3})\s*(?<amount>[\d,]+(?:\.\d+)?)""" +
                        """.*?\bat\s+(?<merchant>[^,.]+)"""
                ),
                direction = Direction.Debit,
                isCreditCard = true,
            ),
        ),
    )
    val DIB = BankProfile(
        id = "dib",
        displayName = "Dubai Islamic Bank",
        senders = listOf("DIB", "DIBAlerts", "DubaiIslamic", "ae.dib.mobile"),
    )
    val EIB = BankProfile(
        id = "eib",
        displayName = "Emirates Islamic",
        senders = listOf("EIB", "EmiratesIsl", "EmiratesIslamic", "EIBank", "ae.emiratesislamic"),
    )
    val ENBD = BankProfile(
        id = "enbd",
        displayName = "Emirates NBD",
        senders = listOf("EmiratesNBD", "ENBD", "com.emiratesnbd.android"),
    )
    val LIV = BankProfile(
        id = "liv",
        displayName = "Liv.",
        senders = listOf("Liv", "LivBank", "ae.liv"),
    )
    val FAB = BankProfile(
        id = "fab",
        displayName = "First Abu Dhabi Bank",
        senders = listOf("FAB", "FABBank", "FABAlerts", "com.fab.personalbanking"),
    )
    val MASHREQ = BankProfile(
        id = "mashreq",
        displayName = "Mashreq / Neo",
        senders = listOf("Mashreq", "MashreqNeo", "Neo", "com.mashreq.mobilebanking"),
    )
    val RAKBANK = BankProfile(
        id = "rakbank",
        displayName = "RAKBANK",
        senders = listOf("RAKBANK", "RAKBNK", "com.rakbank.mobile"),
    )
    val SIB = BankProfile(
        id = "sib",
        displayName = "Sharjah Islamic Bank",
        senders = listOf("SIB", "SIBank", "SharjahIsl"),
    )
    val ADIB = BankProfile(
        id = "adib",
        displayName = "Abu Dhabi Islamic Bank",
        senders = listOf("ADIB", "ADIBAlerts", "com.adib.mobile"),
    )
    val CBD = BankProfile(
        id = "cbd",
        displayName = "Commercial Bank of Dubai",
        senders = listOf("CBD", "CBDBank"),
    )
    val WIO = BankProfile(
        id = "wio",
        displayName = "Wio Bank",
        senders = listOf("Wio", "WioBank", "io.wio.retail"),
    )
    val AJMAN = BankProfile(
        id = "ajman",
        displayName = "Ajman Bank",
        senders = listOf("AjmanBank"),
    )
    val NBF = BankProfile(
        id = "nbf",
        displayName = "National Bank of Fujairah",
        senders = listOf("NBF", "NBFBank"),
    )
    val CBI = BankProfile(
        id = "cbi",
        displayName = "Commercial Bank International",
        senders = listOf("CBI", "CBIBank"),
    )
    val HSBC = BankProfile(
        id = "hsbc",
        displayName = "HSBC UAE",
        senders = listOf("HSBC", "HSBCUAE"),
    )
    val CITI = BankProfile(
        id = "citi",
        displayName = "Citibank UAE",
        senders = listOf("Citibank", "Citi"),
    )
    val TABBY = BankProfile(
        id = "tabby",
        displayName = "Tabby",
        senders = listOf("tabby", "Tabby", "ai.tabby.android"),
        // installment reminders aren't new purchases
        ignorePhrases = listOf("reminder", "upcoming payment", "due tomorrow", "due today"),
    )
    val TAMARA = BankProfile(
        id = "tamara",
        displayName = "Tamara",
        senders = listOf("tamara", "co.tamara.app"),
        ignorePhrases = listOf("reminder", "upcoming payment", "due tomorrow", "due today"),
    )

    /** Fallback for any sender the user maps manually. */
    val OTHER = BankProfile(
        id = "other",
        displayName = "Other bank",
        senders = emptyList(),
    )

    val all: List<BankProfile> = listOf(
        DIB, ADCB, EIB, TABBY,
        ENBD, LIV, FAB, MASHREQ, RAKBANK, SIB, ADIB, CBD, WIO,
        AJMAN, NBF, CBI, HSBC, CITI, TAMARA,
    )

    fun byId(id: String): BankProfile? = (all + OTHER).firstOrNull { it.id == id }
}

/**
 * Finds the bank for a message and parses it.
 *
 * @param enabledBankIds only these banks are considered
 * @param extraSenders user-added sender ids per bank id
 */
@Suppress("MagicNumber")
class BankMessageRouter(
    private val banks: List<BankProfile> = UaeBanks.all,
) {
    fun bankFor(
        sender: String,
        enabledBankIds: Set<String>,
        extraSenders: Map<String, List<String>> = emptyMap(),
    ): BankProfile? {
        val normalized = sender.normalizeSender()
        if (normalized.isEmpty()) return null
        val candidates = banks.filter { it.id in enabledBankIds } +
            listOfNotNull(UaeBanks.OTHER.takeIf { it.id in enabledBankIds })
        // exact match first, then prefix (e.g. "ADCB" vs "ADCBAlerts")
        return candidates.firstOrNull { bank ->
            bank.allSenders(extraSenders).any { it == normalized }
        } ?: candidates.firstOrNull { bank ->
            bank.allSenders(extraSenders).any { it.length >= 3 && normalized.startsWith(it) }
        }
    }

    fun parse(
        sender: String,
        body: String,
        enabledBankIds: Set<String>,
        extraSenders: Map<String, List<String>> = emptyMap(),
    ): ParsedMessage? {
        val bank = bankFor(sender, enabledBankIds, extraSenders) ?: return null
        return GenericMessageParser.parse(bank, body)
    }

    private fun BankProfile.allSenders(extra: Map<String, List<String>>): List<String> =
        (senders + extra[id].orEmpty()).map { it.normalizeSender() }.filter { it.isNotEmpty() }
}

/** "AD-Emirates NBD" -> "EMIRATESNBD" */
fun String.normalizeSender(): String = uppercase()
    .removePrefix("AD-")
    .filter { it.isLetterOrDigit() || it == '.' }
