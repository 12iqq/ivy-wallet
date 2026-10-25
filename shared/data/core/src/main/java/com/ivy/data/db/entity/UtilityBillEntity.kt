package com.ivy.data.db.entity

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A utility bill (SEWA for now) read from the provider's SMS. Dates are ISO `yyyy-MM-dd`.
 * Part of backups, so the history survives a reinstall even after the texts are gone.
 */
@Suppress("DataClassTypedIDs")
@Keep
@Serializable
@Entity(tableName = "utility_bills")
data class UtilityBillEntity(
    /** `provider:account:periodEnd`, so reading the same text again updates instead of duplicating */
    @PrimaryKey
    @SerialName("id")
    val id: String,
    @SerialName("provider")
    val provider: String,
    @SerialName("account")
    val account: String,
    @SerialName("periodStart")
    val periodStart: String,
    @SerialName("periodEnd")
    val periodEnd: String,
    @SerialName("electricity")
    val electricity: Double,
    @SerialName("water")
    val water: Double,
    @SerialName("gas")
    val gas: Double,
    /** Municipality (housing) fee billed with the utilities */
    @SerialName("municipality")
    val municipality: Double,
    @SerialName("vat")
    val vat: Double,
    @SerialName("others")
    val others: Double,
    /** Unpaid amount carried over from earlier bills, included in [total] */
    @SerialName("arrears")
    val arrears: Double,
    /** What the provider asked to pay: this bill plus [arrears] */
    @SerialName("total")
    val total: Double,
    @SerialName("dueDate")
    val dueDate: String?,
    @SerialName("currency")
    val currency: String,
)
