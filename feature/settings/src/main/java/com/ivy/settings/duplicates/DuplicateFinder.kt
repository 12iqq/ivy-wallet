package com.ivy.settings.duplicates

import com.ivy.base.model.TransactionType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.UUID

/** What a transaction needs to be compared with the others. */
@Suppress("DataClassTypedIDs")
data class DuplicateEntry(
    val id: UUID,
    val type: TransactionType,
    val accountId: UUID,
    val toAccountId: UUID?,
    val amount: Double,
    val time: Instant,
)

/** Transactions that look like one entered twice: same kind, account, amount and day. */
data class DuplicateGroup(
    val key: String,
    val day: LocalDate,
    val entries: List<DuplicateEntry>,
)

object DuplicateFinder {
    /** Newest first; groups marked "not duplicates" ([ignored]) are left out. */
    fun groups(entries: List<DuplicateEntry>, zone: ZoneId, ignored: Set<String>): List<DuplicateGroup> = entries
        .groupBy { key(it, zone) }
        .filter { (key, same) -> same.size > 1 && key !in ignored }
        .map { (key, same) ->
            DuplicateGroup(
                key = key,
                day = same.first().time.atZone(zone).toLocalDate(),
                entries = same.sortedBy { it.time },
            )
        }
        .sortedByDescending { it.entries.first().time }

    fun key(entry: DuplicateEntry, zone: ZoneId): String = listOf(
        entry.type.name,
        entry.accountId.toString(),
        entry.toAccountId?.toString().orEmpty(),
        String.format(Locale.ROOT, "%.2f", entry.amount),
        entry.time.atZone(zone).toLocalDate().toString(),
    ).joinToString("|")
}
