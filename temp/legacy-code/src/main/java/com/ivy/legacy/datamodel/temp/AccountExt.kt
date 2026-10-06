package com.ivy.legacy.datamodel.temp

import com.ivy.data.db.entity.AccountEntity
import com.ivy.legacy.datamodel.Account

fun AccountEntity.toLegacyDomain(): Account = Account(
    name = name,
    currency = currency,
    color = color,
    icon = icon,
    orderNum = orderNum,
    includeInBalance = includeInBalance,
    isCreditCard = accountType == AccountEntity.TYPE_CREDIT_CARD,
    creditLimit = creditLimit,
    statementDay = statementDay,
    paymentDueDay = paymentDueDay,
    isSynced = isSynced,
    isDeleted = isDeleted,
    id = id
)
