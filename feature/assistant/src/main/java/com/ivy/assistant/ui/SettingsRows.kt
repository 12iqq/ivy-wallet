package com.ivy.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.ivy.ui.R
import com.ivy.wallet.ui.theme.components.IvySwitch

@Composable
internal fun SectionTitle(text: String) {
    Column {
        Spacer(Modifier.height(32.dp))
        Text(
            modifier = Modifier.padding(start = 32.dp, end = 24.dp),
            text = text,
            style = UI.typo.b2.style(color = UI.colors.gray, fontWeight = FontWeight.Bold)
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
internal fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(UI.shapes.r4)
            .background(UI.colors.medium, UI.shapes.r4)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = UI.typo.b2.style(fontWeight = FontWeight.Bold))
            Text(
                text = description,
                style = UI.typo.c.style(color = UI.colors.gray, fontWeight = FontWeight.Medium)
            )
        }
        Spacer(Modifier.width(12.dp))
        IvySwitch(enabled = checked) { onCheckedChange(it) }
    }
}

@Composable
internal fun DaysBeforeRow(days: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .border(2.dp, UI.colors.medium, UI.shapes.r4)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = stringResource(R.string.remind_days_before, days),
            style = UI.typo.b2.style(fontWeight = FontWeight.SemiBold)
        )
        StepButton("−", enabled = days > NotificationSettingsViewModel.MIN_DAYS_BEFORE) { onChange(days - 1) }
        Spacer(Modifier.width(8.dp))
        StepButton("+", enabled = days < NotificationSettingsViewModel.MAX_DAYS_BEFORE) { onChange(days + 1) }
    }
}

@Composable
private fun StepButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        modifier = Modifier
            .clip(UI.shapes.rFull)
            .background(UI.colors.medium, UI.shapes.rFull)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        text = text,
        style = UI.typo.b1.style(
            color = if (enabled) UI.colors.pureInverse else UI.colors.gray,
            fontWeight = FontWeight.Bold
        )
    )
}
