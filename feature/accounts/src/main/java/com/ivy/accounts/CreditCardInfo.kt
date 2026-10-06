package com.ivy.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivy.data.model.CreditCardCycle
import com.ivy.data.model.CreditCardDetails
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.legacy.utils.format
import com.ivy.ui.R
import com.ivy.wallet.ui.theme.Green
import com.ivy.wallet.ui.theme.Orange
import com.ivy.wallet.ui.theme.Red
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Owed amount, limit usage bar and next due date of a credit card account. */
@Suppress("MagicNumber")
@Composable
fun CreditCardInfo(
    balance: Double,
    currency: String,
    details: CreditCardDetails?,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
) {
    val owed = (-balance).coerceAtLeast(0.0)
    val limit = details?.creditLimit?.takeIf { it > 0 }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(12.dp))

        Row {
            Text(
                text = stringResource(R.string.owed),
                style = UI.typo.c.style(color = UI.colors.gray, fontWeight = FontWeight.Bold)
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (limit != null) {
                    "${owed.format(currency)} / ${limit.format(currency)} $currency"
                } else {
                    "${owed.format(currency)} $currency"
                },
                style = UI.typo.nC.style(fontWeight = FontWeight.ExtraBold)
            )
        }

        if (limit != null) {
            val used = (owed / limit).coerceIn(0.0, 1.0)
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(UI.colors.medium, UI.shapes.rFull)
            ) {
                if (used > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(used.toFloat())
                            .height(8.dp)
                            .background(usageColor(used), UI.shapes.rFull)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row {
                Text(
                    text = stringResource(R.string.credit_used_percent, (used * 100).toInt()),
                    style = UI.typo.c.style(color = UI.colors.gray, fontWeight = FontWeight.SemiBold)
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(
                        R.string.available_credit,
                        "${(limit - owed).coerceAtLeast(0.0).format(currency)} $currency"
                    ),
                    style = UI.typo.c.style(color = UI.colors.gray, fontWeight = FontWeight.SemiBold)
                )
            }
        }

        val dueDate = details?.paymentDueDay?.let { nextDayOfMonth(today, it) }
        if (dueDate != null && owed > 0) {
            val days = ChronoUnit.DAYS.between(today, dueDate).toInt()
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (days == 0) {
                    stringResource(R.string.due_today)
                } else {
                    stringResource(
                        R.string.due_on_in_days,
                        dueDate.format(DateTimeFormatter.ofPattern("d MMM")),
                        days
                    )
                },
                style = UI.typo.c.style(
                    color = if (days <= 3) Red else UI.colors.pureInverse,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

@Suppress("MagicNumber")
private fun usageColor(used: Double): Color = when {
    used < 0.3 -> Green
    used < 0.7 -> Orange
    else -> Red
}

/** The next date (today or later) falling on [dayOfMonth]; see [CreditCardCycle.nextDayOfMonth]. */
fun nextDayOfMonth(today: LocalDate, dayOfMonth: Int): LocalDate =
    CreditCardCycle.nextDayOfMonth(today, dayOfMonth)
