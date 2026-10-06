package com.ivy.wallet.domain.deprecated.logic.model

import androidx.compose.ui.graphics.Color

data class CreateAccountData(
    val name: String,
    val currency: String,
    val color: Color,
    val icon: String?,
    val balance: Double,
    val includeBalance: Boolean = true,
    val isCreditCard: Boolean = false,
    val creditLimit: Double? = null,
    val statementDay: Int? = null,
    val paymentDueDay: Int? = null,
)
