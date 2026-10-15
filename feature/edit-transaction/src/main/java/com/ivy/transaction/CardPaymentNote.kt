package com.ivy.transaction

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.legacy.utils.format
import com.ivy.ui.R
import com.ivy.wallet.ui.theme.Ivy
import com.ivy.wallet.ui.theme.White

/**
 * Shown in a transfer to a credit card: what the card owes and what's left after
 * this payment ([paying], in the card's currency), with a shortcut to pay it all.
 */
@Composable
fun CardPaymentNote(
    payoff: CardPayoff,
    paying: Double,
    onPayAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val owed = payoff.owed.money(payoff.currency)
    val left = (payoff.owed - paying).coerceAtLeast(0.0)
    Row(
        modifier = modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth()
            .clip(UI.shapes.r4)
            .background(UI.colors.medium, UI.shapes.r4)
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.card_payment_title),
                style = UI.typo.b2.style(fontWeight = FontWeight.Bold)
            )
            Text(
                text = when {
                    paying <= 0.0 -> stringResource(R.string.card_payment_owed, owed)
                    left > 0.0 -> stringResource(R.string.card_payment_left, owed, left.money(payoff.currency))
                    else -> stringResource(R.string.card_payment_full, owed)
                },
                style = UI.typo.c.style(color = UI.colors.gray, fontWeight = FontWeight.SemiBold)
            )
        }
        if (left > 0.0) {
            Spacer(Modifier.width(12.dp))
            Text(
                modifier = Modifier
                    .clip(UI.shapes.rFull)
                    .background(Ivy, UI.shapes.rFull)
                    .clickable(onClick = onPayAll)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                text = stringResource(R.string.pay_all),
                style = UI.typo.b2.style(color = White, fontWeight = FontWeight.Bold)
            )
        }
    }
}

private fun Double.money(currency: String): String = "${format(currency)} $currency"
