package com.ivy.planned.installments

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.ivy.data.model.Category
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.legacy.datamodel.Account
import com.ivy.legacy.utils.format
import com.ivy.ui.R
import com.ivy.wallet.ui.theme.Ivy
import com.ivy.wallet.ui.theme.components.IvySwitch
import com.ivy.wallet.ui.theme.components.IvyTitleTextField
import com.ivy.wallet.ui.theme.modal.IvyModal
import com.ivy.wallet.ui.theme.modal.ModalAdd
import com.ivy.wallet.ui.theme.modal.edit.AmountModal
import com.ivy.wallet.ui.theme.toComposeColor
import kotlinx.collections.immutable.ImmutableList
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

/** Splits a Tabby / Tamara purchase into planned payments. */
// a modal like the other legacy IvyModals: it positions itself, so no modifier
@Suppress("LongMethod", "CyclomaticComplexMethod", "LongParameterList", "ModifierMissing")
@Composable
fun BoxWithConstraintsScope.InstallmentModal(
    visible: Boolean,
    currency: String,
    accounts: ImmutableList<Account>,
    categories: ImmutableList<Category>,
    dismiss: () -> Unit,
    onCreate: (InstallmentPlanInput) -> Unit,
) {
    val modalId = remember(visible) { UUID.randomUUID() }
    var provider by remember(modalId) { mutableStateOf(InstallmentProvider.Tabby) }
    var merchant by remember(modalId) { mutableStateOf(TextFieldValue("")) }
    var total by remember(modalId) { mutableDoubleStateOf(0.0) }
    var count by remember(modalId) { mutableStateOf(provider.defaultCount) }
    var interval by remember(modalId) { mutableStateOf(provider.defaultInterval) }
    var firstPaidNow by remember(modalId) { mutableStateOf(true) }
    // BNPL is usually paid from a card, so default to the first credit card
    var account by remember(modalId, accounts) {
        mutableStateOf(accounts.firstOrNull { it.isCreditCard } ?: accounts.firstOrNull())
    }
    var category by remember(modalId) { mutableStateOf<Category?>(null) }
    var amountModalVisible by remember { mutableStateOf(false) }
    val amountModalId = remember(modalId) { UUID.randomUUID() }

    IvyModal(
        id = modalId,
        visible = visible,
        dismiss = dismiss,
        PrimaryAction = {
            ModalAdd(enabled = total > 0 && account != null) {
                val chosen = account ?: return@ModalAdd
                onCreate(
                    InstallmentPlanInput(
                        provider = provider,
                        merchant = merchant.text,
                        total = total,
                        count = count,
                        interval = interval,
                        firstDate = LocalDate.now(),
                        firstPaidNow = firstPaidNow,
                        accountId = chosen.id,
                        categoryId = category?.id?.value,
                    )
                )
                dismiss()
            }
        }
    ) {
        Spacer(Modifier.height(32.dp))
        Text(
            modifier = Modifier.padding(start = 32.dp),
            text = stringResource(R.string.split_payment),
            style = UI.typo.b1.style(fontWeight = FontWeight.ExtraBold)
        )

        Spacer(Modifier.height(16.dp))
        ChipRow {
            InstallmentProvider.entries.forEach { option ->
                Chip(text = option.displayName, selected = provider == option) {
                    provider = option
                    count = option.defaultCount
                    interval = option.defaultInterval
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        IvyTitleTextField(
            modifier = Modifier.padding(horizontal = 32.dp),
            dividerModifier = Modifier.padding(horizontal = 24.dp),
            value = merchant,
            hint = stringResource(R.string.where_did_you_buy),
        ) { merchant = it }

        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(UI.shapes.r4)
                .background(UI.colors.medium, UI.shapes.r4)
                .clickable { amountModalVisible = true }
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.total_amount),
                style = UI.typo.b2.style(fontWeight = FontWeight.Bold)
            )
            Text(
                text = if (total > 0) "${total.format(currency)} $currency" else stringResource(R.string.not_set),
                style = UI.typo.nB2.style(fontWeight = FontWeight.ExtraBold)
            )
        }

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.padding(horizontal = 32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.n_payments, count),
                style = UI.typo.b2.style(fontWeight = FontWeight.Bold)
            )
            Chip(text = "−", selected = false) {
                count = (count - 1).coerceAtLeast(InstallmentSchedule.MIN_COUNT)
            }
            Spacer(Modifier.width(8.dp))
            Chip(text = "+", selected = false) {
                count = (count + 1).coerceAtMost(InstallmentSchedule.MAX_COUNT)
            }
        }

        Spacer(Modifier.height(12.dp))
        ChipRow {
            Chip(
                text = stringResource(R.string.installments_monthly),
                selected = interval == InstallmentInterval.Monthly
            ) { interval = InstallmentInterval.Monthly }
            Chip(
                text = stringResource(R.string.every_two_weeks),
                selected = interval == InstallmentInterval.EveryTwoWeeks
            ) { interval = InstallmentInterval.EveryTwoWeeks }
        }

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(UI.shapes.r4)
                .clickable { firstPaidNow = !firstPaidNow }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.first_payment_paid_today),
                style = UI.typo.b2.style(fontWeight = FontWeight.SemiBold)
            )
            IvySwitch(enabled = firstPaidNow) { firstPaidNow = it }
        }

        SectionLabel(stringResource(R.string.account))
        ChipRow {
            accounts.forEach { option ->
                Chip(
                    text = option.name,
                    selected = account?.id == option.id,
                    color = option.color.toComposeColor()
                ) { account = option }
            }
        }

        SectionLabel(stringResource(R.string.category_label))
        ChipRow {
            categories.forEach { option ->
                Chip(
                    text = option.name.value,
                    selected = category?.id == option.id,
                    color = option.color.value.toComposeColor()
                ) { category = if (category?.id == option.id) null else option }
            }
        }

        if (total > 0) {
            val plan = InstallmentSchedule.split(total, count, LocalDate.now(), interval)
            Spacer(Modifier.height(16.dp))
            Text(
                modifier = Modifier.padding(horizontal = 32.dp),
                text = stringResource(
                    R.string.installment_summary,
                    count,
                    "${plan.first().amount.format(currency)} $currency",
                    plan.last().date.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
                ),
                style = UI.typo.c.style(color = UI.colors.gray, fontWeight = FontWeight.SemiBold)
            )
        }

        Spacer(Modifier.height(32.dp))
    }

    AmountModal(
        id = amountModalId,
        visible = amountModalVisible,
        currency = currency,
        initialAmount = total.takeIf { it > 0 },
        dismiss = { amountModalVisible = false },
    ) { total = it }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        modifier = Modifier.padding(start = 32.dp, top = 16.dp, bottom = 8.dp),
        text = text,
        style = UI.typo.c.style(color = UI.colors.gray, fontWeight = FontWeight.Bold)
    )
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
    }
}

@Composable
private fun Chip(
    text: String,
    selected: Boolean,
    color: Color = Ivy,
    onClick: () -> Unit,
) {
    Column {
        Text(
            modifier = Modifier
                .padding(end = 8.dp)
                .clip(UI.shapes.rFull)
                .background(if (selected) color else UI.colors.medium, UI.shapes.rFull)
                .border(2.dp, if (selected) color else UI.colors.medium, UI.shapes.rFull)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            text = text,
            style = UI.typo.b2.style(
                color = if (selected) UI.colors.pure else UI.colors.pureInverse,
                fontWeight = FontWeight.Bold
            )
        )
    }
}
