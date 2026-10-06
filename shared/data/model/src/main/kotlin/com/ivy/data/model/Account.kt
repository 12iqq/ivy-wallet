package com.ivy.data.model

import com.ivy.data.model.primitive.AssetCode
import com.ivy.data.model.primitive.ColorInt
import com.ivy.data.model.primitive.IconAsset
import com.ivy.data.model.primitive.NotBlankTrimmedString
import com.ivy.data.model.sync.Identifiable
import com.ivy.data.model.sync.UniqueId
import java.util.UUID

@JvmInline
value class AccountId(override val value: UUID) : UniqueId

@Suppress("DataClassDefaultValues")
data class Account(
    override val id: AccountId,
    val name: NotBlankTrimmedString,
    val asset: AssetCode,
    val color: ColorInt,
    val icon: IconAsset?,
    val includeInBalance: Boolean,
    override val orderNum: Double,
    val type: AccountType = AccountType.Regular,
    val creditCard: CreditCardDetails? = null,
) : Identifiable<AccountId>, Reorderable {
    val isCreditCard: Boolean
        get() = type == AccountType.CreditCard
}

enum class AccountType {
    Regular,
    CreditCard,
}

/**
 * Extra details of a credit card account. Days are days of the month (1-31);
 * a day past the end of a short month means its last day.
 */
data class CreditCardDetails(
    val creditLimit: Double?,
    val statementDay: Int?,
    val paymentDueDay: Int?,
)
