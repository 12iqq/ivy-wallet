package com.ivy.home.quickadd

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.legacy.utils.format
import com.ivy.wallet.ui.theme.Green
import com.ivy.wallet.ui.theme.toComposeColor
import kotlinx.collections.immutable.ImmutableList

/** One-tap shortcuts learned from the user's history, shown under the home header. */
@Composable
fun QuickAddRow(
    chips: ImmutableList<QuickAddChip>,
    onPick: (QuickAddChip) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (chips.isEmpty()) return
    LazyRow(
        modifier = modifier.padding(vertical = 4.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(chips, key = { it.template.key }) {
            QuickAddChipItem(chip = it, onClick = { onPick(it) })
        }
    }
}

@Composable
private fun QuickAddChipItem(chip: QuickAddChip, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(UI.shapes.rFull)
            .background(UI.colors.medium, UI.shapes.rFull)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(chip.color?.toComposeColor() ?: UI.colors.gray, UI.shapes.rFull)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (chip.template.isIncome) "+ ${chip.template.title}" else chip.template.title,
            style = UI.typo.c.style(
                color = if (chip.template.isIncome) Green else UI.colors.pureInverse,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = chip.template.typicalAmount.format(chip.currency),
            style = UI.typo.nC.style(color = UI.colors.gray, fontWeight = FontWeight.SemiBold)
        )
    }
}
