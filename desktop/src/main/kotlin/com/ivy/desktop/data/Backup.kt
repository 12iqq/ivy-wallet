package com.ivy.desktop.data

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class AccountId(val value: String)

@Serializable
@JvmInline
value class CategoryId(val value: String)

@Serializable
@JvmInline
value class TransactionId(val value: String)

@Serializable
@JvmInline
value class RuleId(val value: String)

@Serializable
@JvmInline
value class TagId(val value: String)

@Serializable
enum class TransactionType { INCOME, EXPENSE, TRANSFER }

/**
 * The phone app's backup, as far as the desktop app reads it. The backup leaves out fields that
 * have their default value, so most fields are nullable here.
 */
@Serializable
data class Backup(
    val accounts: List<BackupAccount>?,
    val categories: List<BackupCategory>?,
    val transactions: List<BackupTransaction>?,
    val plannedPaymentRules: List<BackupRule>?,
    val settings: List<BackupSettings>?,
    val tags: List<BackupTag>?,
    /** Which tags each transaction has */
    val tagAssociations: List<BackupTagLink>?,
)

@Serializable
data class BackupAccount(
    val id: AccountId,
    val name: String,
    /** null: the base currency */
    val currency: String?,
    val color: Int?,
    val orderNum: Double?,
    val includeInBalance: Boolean?,
    /** "CREDIT_CARD" for a credit card */
    val accountType: String?,
    val creditLimit: Double?,
    val paymentDueDay: Int?,
    val isDeleted: Boolean?,
)

@Serializable
data class BackupCategory(
    val id: CategoryId,
    val name: String,
    val color: Int?,
    val orderNum: Double?,
    val isDeleted: Boolean?,
)

@Serializable
data class BackupTransaction(
    val id: TransactionId,
    val accountId: AccountId,
    val type: TransactionType,
    val amount: Double,
    val toAccountId: AccountId?,
    val toAmount: Double?,
    val title: String?,
    val description: String?,
    /** The shop as the bank's SMS named it, for transactions added from SMS */
    val bankDetails: String?,
    /** Epoch milliseconds. null for a planned payment that isn't paid yet. */
    val dateTime: Long?,
    val categoryId: CategoryId?,
    val recurringRuleId: RuleId?,
    val isDeleted: Boolean?,
)

@Serializable
data class BackupTag(
    val id: TagId,
    val name: String,
    val isDeleted: Boolean?,
)

@Serializable
data class BackupTagLink(
    val tagId: TagId,
    /** The transaction with the tag */
    val associatedId: TransactionId,
    val isDeleted: Boolean?,
)

/** A planned payment rule: only used to tell which recurring charges are already planned. */
@Serializable
data class BackupRule(
    val id: RuleId,
    val title: String?,
    val type: TransactionType,
    val oneTime: Boolean?,
    val isDeleted: Boolean?,
)

@Serializable
data class BackupSettings(
    /** The base currency */
    val currency: String,
)
