package com.ivy.autocapture.parser

/**
 * Banks & cards known to the auto-capture. Add new ones here.
 *
 * Sender ids are matched case-insensitively ignoring spaces/dashes, so
 * "Emirates NBD", "EmiratesNBD" and "EMIRATESNBD" are all the same.
 * Users can also add their own sender ids per bank in Settings.
 */
const val GoogleWalletPackage = "com.google.android.apps.walletnfcrel"

/** Named groups for "AED 1,234.56" in bank patterns. */
private const val AMT = """(?<currency>[A-Z]{3})\s*(?<amount>[\d,]+(?:\.\d+)?)"""

@Suppress("MaxLineLength", "MaximumLineLength")
object UaeBanks {
    val ADCB = BankProfile(
        id = "adcb",
        displayName = "ADCB",
        senders = listOf("ADCB", "ADCBAlerts", "com.adcb.bank"),
        patterns = listOf(
            // "Credit Card XX1234 was used for AED45.00 on 06/10/2026 14:22:11 at Vox Cinemas, Dubai-AE."
            // "Your Cr.Card XXX1234 was used for AED45.00 on 06/10/2026 14:22:11 at CARREFOUR,DUBAI-AE."
            MessagePattern(
                regex = Regex(
                    """(?i)(?:Cr\.?|Credit)\s*Card\s+(?:no\.?\s*)?[X*]*\s?(?<last4>\d{4})\s+was used for\s+$AMT""" +
                        """.*?\bat\s+(?<merchant>.+?)(?=,|\s*-\s?AE\b|\.\s|\.?$)"""
                ),
                direction = Direction.Debit,
                isCreditCard = true,
            ),
            // "AED612.00 debited from Acc/Cr.Card XXX1234 for SEWA on 02-09-2026 13:28:59 through ADCB Mobile App."
            MessagePattern(
                regex = Regex(
                    """(?i)$AMT\s+debited from Acc/Cr\.?\s*Card\s+[X*]*(?<last4>\d{4})\s+for\s+(?<merchant>.+?)\s+on\s+\d"""
                ),
                direction = Direction.Debit,
                isCreditCard = true,
            ),
            // "An amount of AED46.00 has been reversed to your Credit Card XXX1234 on 28/08/2026 00:00:43 by Vox Cinemas,Dubai-AE."
            MessagePattern(
                regex = Regex(
                    """(?i)amount of\s+$AMT\s+has been reversed to your (?:Cr\.?|Credit)\s*Card\s+[X*]*(?<last4>\d{4})""" +
                        """.*?\bby\s+(?<merchant>.+?)(?=,|\s*-\s?AE\b|\.\s|\.?$)"""
                ),
                direction = Direction.Credit,
                isCreditCard = true,
            ),
            // "Your payment of AED 1234.56 against Credit Card no. XXX1234 was received at 03:32 PM on 08/09/2026."
            MessagePattern(
                regex = Regex(
                    """(?i)payment of\s+$AMT\s+against (?:Cr\.?|Credit)\s*Card\s+(?:no\.?\s*)?[X*]*(?<last4>\d{4})\s+was received"""
                ),
                direction = Direction.Credit,
                isCreditCard = true,
                isTransfer = true,
            ),
        ),
    )
    val DIB = BankProfile(
        id = "dib",
        displayName = "Dubai Islamic Bank",
        senders = listOf("DIB", "DIBAlerts", "DIB-SMS", "DubaiIslamic", "ae.dib.mobile"),
        patterns = listOf(
            // "POS Purchase of AED: 39.00 from a/c no. 012-345-6789***-01 on 01-NOV-2022 14:40 from KFC SAHARA. Available ..."
            MessagePattern(
                regex = Regex(
                    """(?i)Purchase of\s+(?<currency>[A-Z]{3}):?\s*(?<amount>[\d,]+(?:\.\d+)?)\s+from a/c no\.?\s*[\dX*\-]+""" +
                        """\s+on\s+\S+\s+[\d:]+\s+from\s+(?<merchant>.+?)\.\s+Available"""
                ),
                direction = Direction.Debit,
                isCreditCard = false,
            ),
            // paying a credit card: "AED 1,234.56 has been debited from A/C No. 012-345-678XXXX-01 for Card Payment."
            MessagePattern(
                regex = Regex(
                    """(?i)$AMT\s+has been debited from A/C No\.?\s*[\dX*\-]+\s+for\s+(?<merchant>Card Payment)"""
                ),
                direction = Direction.Debit,
                isCreditCard = false,
                isTransfer = true,
            ),
            // "AED 2,500.00 has been drawn from A/C No. 012-345-678XXXX-01 as OWN ACCOUNT TRNSFER."
            MessagePattern(
                regex = Regex(
                    """(?i)$AMT\s+has been drawn from A/C No\.?\s*[\dX*\-]+\s+as\s+(?<merchant>OWN ACCOUNT[^.]*)"""
                ),
                direction = Direction.Debit,
                isCreditCard = false,
                isTransfer = true,
            ),
            // "AED 437.00 has been drawn from A/C No. 012-345-678XXXX-01 as SEWA PAYMENT-001UPSW22177. Available ..."
            // "AED 700.00 has been withdrawn from your A/C no. 012-345-6789***-01 on 01-JUL-2026 as ATM CASH WITHDRAWAL."
            MessagePattern(
                regex = Regex(
                    """(?i)$AMT\s+has been (?:drawn|withdrawn|debited) from (?:your )?A/C (?:No\.?)?\s*[\dX*\-]+""" +
                        """(?:\s+on\s+\S+)?\s+(?:as|for)\s+(?<merchant>.+?)\.?\s+Available"""
                ),
                direction = Direction.Debit,
                isCreditCard = false,
            ),
            // "AED 4,500.00 has been credited to your a/c 012-345-678XXXX-01 as CDM CASH DEPOSIT. Available ..."
            MessagePattern(
                regex = Regex(
                    """(?i)$AMT\s+has been credited to your a/c\s*[\dX*\-]+\s+as\s+(?<merchant>.+?)\.?\s+Available"""
                ),
                direction = Direction.Credit,
                isCreditCard = false,
            ),
            // "Salary amount of AED  6,000.00 credited to your A/C no. 012-345-6789***-01 on 01-MAR-2023."
            MessagePattern(
                regex = Regex("""(?i)(?<merchant>Salary) amount of\s+$AMT\s+credited"""),
                direction = Direction.Credit,
                isCreditCard = false,
            ),
        ),
    )
    val EIB = BankProfile(
        id = "eib",
        displayName = "Emirates Islamic",
        senders = listOf("EIB", "EI SMS", "EmiratesIsl", "EmiratesIslamic", "EIBank", "ae.emiratesislamic"),
        patterns = listOf(
            // "Credit Card Purchase\nCard Ending: : 1234\nAt: AMAZON RETAIL, Dubai\nAmount: AED 29.99\nDate: ..."
            MessagePattern(
                regex = Regex(
                    """(?i)Card Ending:\s*:?\s*(?<last4>\d{4})\s+At:\s*(?<merchant>.+?)\s+Amount:\s*$AMT"""
                ),
                direction = Direction.Debit,
            ),
            // "This is to confirm receipt of your payment of AED 345.67 towards your Credit Card starting with 512345"
            MessagePattern(
                regex = Regex("""(?i)receipt of your payment of\s+$AMT\s+towards your Credit Card"""),
                direction = Direction.Credit,
                isCreditCard = true,
                isTransfer = true,
            ),
        ),
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
        // Only Tabby Cash moves money in the account. Pay-later orders, reminders and requests
        // to confirm aren't (the instalments show up on the card that pays them).
        patternsOnly = true,
        patterns = listOf(
            // "You spent AED 58.40 at TAP*Keeta. Your available Tabby Cash balance is now AED 412.60."
            MessagePattern(
                regex = Regex("""(?i)You spent\s+$AMT\s+at\s+(?<merchant>.+?)\.\s+Your available"""),
                direction = Direction.Debit,
                isCreditCard = false,
            ),
            // a top-up: "AED 500 was added to your account using card ending 1234. Your new balance is AED 836.10."
            MessagePattern(
                regex = Regex("""(?i)$AMT\s+was added to your account using card ending\s+(?<other>\d{4})"""),
                direction = Direction.Credit,
                isCreditCard = false,
                isTransfer = true,
            ),
            // "You received 264.80 AED from JOHN SMITH. Your new balance is AED 264.80."
            MessagePattern(
                regex = Regex(
                    """(?i)You received\s+(?<amount>[\d,]+(?:\.\d+)?)\s*(?<currency>[A-Z]{3})\s+from\s+(?<merchant>.+?)\.\s+Your new balance"""
                ),
                direction = Direction.Credit,
                isCreditCard = false,
            ),
            // "You sent AED 61.50 to JOHN SMITH. Your new balance is AED 702.45."
            MessagePattern(
                regex = Regex("""(?i)You sent\s+$AMT\s+to\s+(?<merchant>.+?)\.\s+Your new balance"""),
                direction = Direction.Debit,
                isCreditCard = false,
            ),
        ),
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

    /** Tap-to-pay notifications from the Google Wallet app (not a bank, so not in [all]). */
    val GOOGLE_WALLET = BankProfile(
        id = "google_wallet",
        displayName = "Google Wallet",
        senders = listOf(GoogleWalletPackage),
    )

    val all: List<BankProfile> = listOf(
        DIB, ADCB, EIB, TABBY,
        ENBD, LIV, FAB, MASHREQ, RAKBANK, SIB, ADIB, CBD, WIO,
        AJMAN, NBF, CBI, HSBC, CITI, TAMARA,
    )

    fun byId(id: String): BankProfile? = (all + OTHER + GOOGLE_WALLET).firstOrNull { it.id == id }
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
