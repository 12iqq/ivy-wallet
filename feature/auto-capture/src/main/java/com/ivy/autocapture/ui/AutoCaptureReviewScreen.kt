package com.ivy.autocapture.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.ivy.base.model.TransactionType
import com.ivy.data.model.Category
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.legacy.datamodel.Account
import com.ivy.legacy.utils.format
import com.ivy.legacy.utils.navigationBarInset
import com.ivy.legacy.utils.onScreenStart
import com.ivy.legacy.utils.toDensityDp
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.ivy.ui.R
import com.ivy.wallet.ui.theme.Green
import com.ivy.wallet.ui.theme.GradientGreen
import com.ivy.wallet.ui.theme.Orange
import com.ivy.wallet.ui.theme.Red
import com.ivy.wallet.ui.theme.components.ActionsRow
import com.ivy.wallet.ui.theme.components.CloseButton
import com.ivy.wallet.ui.theme.components.IvyButton
import com.ivy.wallet.ui.theme.components.IvyDividerDot
import com.ivy.wallet.ui.theme.components.IvyOutlinedButton
import com.ivy.wallet.ui.theme.components.IvyOutlinedTextField
import com.ivy.wallet.ui.theme.findContrastTextColor
import com.ivy.wallet.ui.theme.gradientCutBackgroundTop
import com.ivy.wallet.ui.theme.modal.IvyModal
import com.ivy.wallet.ui.theme.modal.ModalSet
import com.ivy.wallet.ui.theme.modal.ModalTitle
import com.ivy.wallet.ui.theme.modal.edit.ChooseCategoryModal
import com.ivy.wallet.ui.theme.toComposeColor
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

@Composable
fun BoxWithConstraintsScope.AutoCaptureReviewScreenImpl() {
    val viewModel: AutoCaptureReviewViewModel = screenScopedViewModel()
    val state = viewModel.uiState()
    onScreenStart { viewModel.onEvent(AutoCaptureReviewEvent.Refresh) }
    UI(state = state, onEvent = viewModel::onEvent)
}

@Suppress("LongMethod")
@Composable
private fun BoxWithConstraintsScope.UI(
    state: AutoCaptureReviewState,
    onEvent: (AutoCaptureReviewEvent) -> Unit,
) {
    val nav = navigation()
    var accountPickerFor by remember { mutableStateOf<UUID?>(null) }
    var categoryPickerFor by remember { mutableStateOf<ReviewItem?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        item {
            Spacer(Modifier.height(32.dp))
            Text(
                modifier = Modifier.padding(start = 24.dp),
                text = stringResource(R.string.review_transactions),
                style = UI.typo.h2.style(fontWeight = FontWeight.ExtraBold)
            )
            if (state.items.isNotEmpty()) {
                Text(
                    modifier = Modifier.padding(start = 24.dp, top = 4.dp),
                    text = stringResource(R.string.n_to_review, state.items.size),
                    style = UI.typo.b2.style(color = UI.colors.gray, fontWeight = FontWeight.SemiBold)
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        if (state.items.isEmpty()) {
            item {
                Text(
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 48.dp),
                    text = stringResource(R.string.review_transactions_empty),
                    style = UI.typo.b2.style(color = UI.colors.gray, fontWeight = FontWeight.Medium)
                )
            }
        }

        items(state.items, key = { it.captured.id }) { item ->
            Spacer(Modifier.height(12.dp))
            CapturedCard(
                item = item,
                onChooseAccount = { accountPickerFor = item.captured.id },
                onChooseCategory = { categoryPickerFor = item },
                onTitleChange = { onEvent(AutoCaptureReviewEvent.SetTitle(item.captured.id, it)) },
                onAdd = { onEvent(AutoCaptureReviewEvent.Add(item)) },
                onSkip = { onEvent(AutoCaptureReviewEvent.Skip(item)) },
            )
        }

        item { Spacer(Modifier.height(150.dp)) } // scroll hack
    }

    ActionsRow(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .gradientCutBackgroundTop(UI.colors.pure, LocalDensity.current)
            .padding(bottom = navigationBarInset().toDensityDp())
            .padding(bottom = 24.dp)
    ) {
        Spacer(Modifier.width(20.dp))
        CloseButton { nav.back() }
        Spacer(Modifier.weight(1f))
        if (state.items.any { it.account != null && !it.possibleDuplicate }) {
            IvyButton(
                text = stringResource(R.string.add_all),
                iconStart = R.drawable.ic_check,
                backgroundGradient = GradientGreen,
            ) {
                onEvent(AutoCaptureReviewEvent.AddAll)
            }
        }
        Spacer(Modifier.width(20.dp))
    }

    AccountPickerModal(
        visible = accountPickerFor != null,
        accounts = state.accounts,
        dismiss = { accountPickerFor = null },
    ) { account ->
        accountPickerFor?.let { onEvent(AutoCaptureReviewEvent.SetAccount(it, account)) }
        accountPickerFor = null
    }

    ChooseCategoryModal(
        visible = categoryPickerFor != null,
        initialCategory = categoryPickerFor?.category,
        categories = state.categories,
        showCategoryModal = { },
        onCategoryChanged = { category ->
            categoryPickerFor?.let {
                onEvent(AutoCaptureReviewEvent.SetCategory(it.captured.id, category))
            }
        },
        dismiss = { categoryPickerFor = null }
    )
}

@Suppress("LongMethod", "LongParameterList")
@Composable
private fun CapturedCard(
    item: ReviewItem,
    onChooseAccount: () -> Unit,
    onChooseCategory: () -> Unit,
    onTitleChange: (String) -> Unit,
    onAdd: () -> Unit,
    onSkip: () -> Unit,
) {
    val captured = item.captured
    var showRaw by remember(captured.id) { mutableStateOf(false) }
    var title by remember(captured.id) { mutableStateOf(TextFieldValue(item.title.orEmpty())) }

    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(UI.shapes.r4)
            .border(2.dp, UI.colors.medium, UI.shapes.r4)
            .clickable { showRaw = !showRaw }
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = item.bankName,
                style = UI.typo.c.style(color = UI.colors.gray, fontWeight = FontWeight.Bold)
            )
            captured.last4?.let {
                Spacer(Modifier.width(6.dp))
                IvyDividerDot()
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "··$it",
                    style = UI.typo.c.style(color = UI.colors.gray, fontWeight = FontWeight.Bold)
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = captured.receivedAt.atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("d MMM, HH:mm")),
                style = UI.typo.c.style(color = UI.colors.gray, fontWeight = FontWeight.SemiBold)
            )
        }

        Spacer(Modifier.height(8.dp))

        val isExpense = captured.type == TransactionType.EXPENSE
        Text(
            text = "${if (isExpense) "-" else "+"}${captured.amount.format(captured.currency)} ${captured.currency}",
            style = UI.typo.nH2.style(
                color = if (isExpense) UI.colors.pureInverse else Green,
                fontWeight = FontWeight.ExtraBold
            )
        )

        Spacer(Modifier.height(12.dp))

        IvyOutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = title,
            hint = stringResource(if (isExpense) R.string.expense_title else R.string.income_title),
        ) {
            title = it
            onTitleChange(it.text)
        }

        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Chip(
                text = item.account?.name ?: stringResource(R.string.choose_account),
                color = item.account?.color?.toComposeColor(),
                onClick = onChooseAccount,
            )
            Spacer(Modifier.width(8.dp))
            Chip(
                text = item.category?.name?.value ?: stringResource(R.string.add_category),
                color = item.category?.color?.value?.toComposeColor(),
                onClick = onChooseCategory,
            )
        }

        if (item.possibleDuplicate) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.possible_duplicate),
                style = UI.typo.c.style(color = Orange, fontWeight = FontWeight.Bold)
            )
        }

        if (showRaw) {
            Spacer(Modifier.height(10.dp))
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(UI.colors.medium, UI.shapes.r4)
                    .padding(12.dp),
                text = captured.rawText,
                style = UI.typo.c.style(fontWeight = FontWeight.Normal)
            )
        }

        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            IvyOutlinedButton(
                text = stringResource(R.string.skip),
                iconStart = R.drawable.ic_remove,
                iconTint = Red,
                textColor = Red,
            ) { onSkip() }
            Spacer(Modifier.weight(1f))
            IvyButton(
                text = stringResource(R.string.add),
                iconStart = R.drawable.ic_check,
                enabled = item.account != null,
            ) { onAdd() }
        }
    }
}

@Composable
private fun Chip(
    text: String,
    color: Color?,
    onClick: () -> Unit,
) {
    val background = color ?: UI.colors.medium
    Text(
        modifier = Modifier
            .clip(UI.shapes.rFull)
            .background(background, UI.shapes.rFull)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        text = text,
        style = UI.typo.c.style(
            color = if (color != null) findContrastTextColor(color) else UI.colors.pureInverse,
            fontWeight = FontWeight.ExtraBold
        )
    )
}

@Composable
private fun BoxWithConstraintsScope.AccountPickerModal(
    visible: Boolean,
    accounts: List<Account>,
    dismiss: () -> Unit,
    onAccountSelected: (Account) -> Unit,
) {
    IvyModal(
        id = remember(visible) { UUID.randomUUID() },
        visible = visible,
        dismiss = dismiss,
        PrimaryAction = { ModalSet { dismiss() } }
    ) {
        Spacer(Modifier.height(32.dp))
        ModalTitle(text = stringResource(R.string.choose_account))
        Spacer(Modifier.height(16.dp))
        accounts.forEach { account ->
            val color = account.color.toComposeColor()
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .fillMaxWidth()
                    .clip(UI.shapes.r4)
                    .background(color, UI.shapes.r4)
                    .clickable { onAccountSelected(account) }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = account.name,
                    style = UI.typo.b2.style(
                        color = findContrastTextColor(color),
                        fontWeight = FontWeight.ExtraBold
                    )
                )
                if (account.isCreditCard) {
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = stringResource(R.string.credit_card),
                        style = UI.typo.c.style(color = findContrastTextColor(color))
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
