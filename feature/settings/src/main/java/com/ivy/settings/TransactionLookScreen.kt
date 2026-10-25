package com.ivy.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ivy.base.legacy.Theme
import com.ivy.design.l0_system.LocalIvyColors
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.legacy.appDesign
import com.ivy.legacy.ivyWalletCtx
import com.ivy.legacy.ui.component.transaction.TransactionCard
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.ivy.ui.R
import com.ivy.ui.transactionlook.LocalTransactionLook
import com.ivy.ui.transactionlook.TagTextSize
import com.ivy.wallet.ui.theme.Ivy
import com.ivy.wallet.ui.theme.components.IvySwitch
import com.ivy.wallet.ui.theme.components.IvyToolbar

@Composable
fun BoxWithConstraintsScope.TransactionLookScreenImpl() {
    val viewModel: TransactionLookViewModel = screenScopedViewModel()
    val state = viewModel.uiState()
    TransactionLookUi(state = state, onEvent = viewModel::onEvent)
}

@Composable
private fun TransactionLookUi(state: TransactionLookState, onEvent: (TransactionLookEvent) -> Unit) {
    val nav = navigation()
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        item {
            IvyToolbar(onBack = { nav.onBackPressed() }) {}
            Text(
                modifier = Modifier.padding(start = 32.dp, top = 8.dp),
                text = stringResource(R.string.transaction_look),
                style = UI.typo.h2.style(fontWeight = FontWeight.Black)
            )
            Text(
                modifier = Modifier.padding(start = 32.dp, end = 24.dp, top = 4.dp),
                text = stringResource(R.string.transaction_look_desc),
                style = UI.typo.b2.style(color = UI.colors.gray, fontWeight = FontWeight.Medium)
            )
        }

        item {
            Spacer(Modifier.height(16.dp))
            // the cards below follow what's chosen here, in both themes
            CompositionLocalProvider(LocalTransactionLook provides state.look) {
                ThemePreview(theme = Theme.LIGHT, label = stringResource(R.string.light_mode), state = state)
                Spacer(Modifier.height(12.dp))
                ThemePreview(theme = Theme.DARK, label = stringResource(R.string.dark_mode), state = state)
            }
        }

        item {
            Spacer(Modifier.height(24.dp))
            SwitchRow(
                title = stringResource(R.string.color_account_tags),
                description = stringResource(R.string.color_account_tags_desc),
                checked = state.colorAccountTags,
            ) { onEvent(TransactionLookEvent.SetColorAccountTags(it)) }

            SizeChoice(
                title = stringResource(R.string.account_tag_text),
                selected = state.look.accountTagSize,
            ) { onEvent(TransactionLookEvent.SetAccountTagSize(it)) }

            SizeChoice(
                title = stringResource(R.string.category_tag_text),
                selected = state.look.categoryTagSize,
            ) { onEvent(TransactionLookEvent.SetCategoryTagSize(it)) }

            Spacer(Modifier.height(120.dp))
        }
    }
}

/** The sample cards drawn in [theme], whatever the app's theme is. */
@Composable
private fun ThemePreview(theme: Theme, label: String, state: TransactionLookState) {
    val baseData = state.baseData ?: return
    val ivyContext = ivyWalletCtx()
    val colors = remember(theme) { appDesign(ivyContext).colors(theme, isDarkModeEnabled = theme != Theme.LIGHT) }
    CompositionLocalProvider(LocalIvyColors provides colors) {
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(UI.shapes.r4)
                .background(UI.colors.pure, UI.shapes.r4)
        ) {
            Column(modifier = Modifier.padding(bottom = 16.dp)) {
                Text(
                    modifier = Modifier.padding(start = 24.dp, top = 12.dp),
                    text = label,
                    style = UI.typo.c.style(color = UI.colors.gray, fontWeight = FontWeight.Bold)
                )
                state.samples.forEach { transaction ->
                    TransactionCard(
                        baseData = baseData,
                        transaction = transaction,
                        shouldShowAccountSpecificColorInTransactions = state.colorAccountTags,
                        onPayOrGet = {},
                        onClick = {},
                    )
                }
            }
            // only a preview: tapping a tag would open that account or category
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(Unit) { detectTapGestures { } }
            )
        }
    }
}

@Composable
private fun SwitchRow(
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SizeChoice(title: String, selected: TagTextSize, onSelect: (TagTextSize) -> Unit) {
    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp)) {
        Text(
            modifier = Modifier.padding(start = 8.dp),
            text = title,
            style = UI.typo.b2.style(fontWeight = FontWeight.Bold)
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TagTextSize.entries.forEach { size ->
                SizeChip(text = stringResource(size.label()), selected = size == selected) { onSelect(size) }
            }
        }
    }
}

@Composable
private fun SizeChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        modifier = Modifier
            .clip(UI.shapes.rFull)
            .background(if (selected) Ivy else UI.colors.medium, UI.shapes.rFull)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        text = text,
        style = UI.typo.b2.style(
            color = if (selected) UI.colors.pure else UI.colors.pureInverse,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    )
}

private fun TagTextSize.label(): Int = when (this) {
    TagTextSize.Small -> R.string.tag_size_small
    TagTextSize.Default -> R.string.tag_size_default
    TagTextSize.Large -> R.string.tag_size_large
    TagTextSize.ExtraLarge -> R.string.tag_size_extra_large
}
