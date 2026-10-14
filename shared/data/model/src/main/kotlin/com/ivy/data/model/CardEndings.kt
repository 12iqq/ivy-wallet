package com.ivy.data.model

/**
 * Card / account number endings ("1234") that identify an account in bank messages.
 * Stored as one comma-separated string.
 */
object CardEndings {
    private const val DIGITS = 4

    /** "1234, 98765678 / 12" -> ["1234", "5678"]: keeps the last 4 digits of each number. */
    fun parse(text: String?): List<String> = text.orEmpty()
        .split(',', ';', '/', ' ', '\n')
        .map { part -> part.filter(Char::isDigit) }
        .filter { it.length >= DIGITS }
        .map { it.takeLast(DIGITS) }
        .distinct()

    fun format(endings: List<String>): String? = endings.takeIf { it.isNotEmpty() }?.joinToString(",")
}
