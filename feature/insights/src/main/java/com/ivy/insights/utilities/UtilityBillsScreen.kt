package com.ivy.insights.utilities

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.insights.ReportCard
import com.ivy.insights.ReportChip
import com.ivy.insights.StackedBar
import com.ivy.insights.StackedBars
import com.ivy.legacy.utils.format
import com.ivy.legacy.utils.onScreenStart
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.ivy.ui.R
import com.ivy.wallet.ui.theme.Blue
import com.ivy.wallet.ui.theme.Gray
import com.ivy.wallet.ui.theme.Green
import com.ivy.wallet.ui.theme.Ivy
import com.ivy.wallet.ui.theme.Orange
import com.ivy.wallet.ui.theme.Red
import com.ivy.wallet.ui.theme.components.IvyButton
import com.ivy.wallet.ui.theme.components.IvySwitch
import com.ivy.wallet.ui.theme.components.IvyToolbar
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.abs

@Composable
fun BoxWithConstraintsScope.UtilityBillsScreenImpl() {
    val viewModel: UtilityBillsViewModel = screenScopedViewModel()
    val state = viewModel.uiState()
    onScreenStart { viewModel.onEvent(UtilityBillsEvent.Refresh) }
    UtilityBillsUi(state = state, onEvent = viewModel::onEvent)
}

@Suppress("LongMethod")
@Composable
private fun UtilityBillsUi(state: UtilityBillsState, onEvent: (UtilityBillsEvent) -> Unit) {
    val nav = navigation()
    val context = LocalContext.current
    // true once Android refused, e.g. the "restricted setting" for apps installed outside the Play Store
    var permissionRefused by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionRefused = !granted
        onEvent(UtilityBillsEvent.PermissionChanged)
    }

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
                text = stringResource(R.string.utility_bills),
                style = UI.typo.h2.style(fontWeight = FontWeight.Black)
            )
            Text(
                modifier = Modifier.padding(start = 32.dp, end = 24.dp, top = 4.dp),
                text = stringResource(R.string.utility_bills_desc),
                style = UI.typo.b2.style(color = UI.colors.gray, fontWeight = FontWeight.Medium)
            )
            Spacer(Modifier.height(16.dp))
            TrackRow(checked = state.enabled) { enabled ->
                onEvent(UtilityBillsEvent.SetEnabled(enabled))
                if (enabled && !state.hasPermission) permissionLauncher.launch(Manifest.permission.READ_SMS)
            }
        }

        if (state.enabled && !state.hasPermission) {
            item {
                Spacer(Modifier.height(12.dp))
                InfoCard(
                    text = stringResource(
                        if (permissionRefused) R.string.sms_permission_restricted else R.string.utility_sms_permission
                    )
                ) {
                    if (permissionRefused) {
                        IvyButton(
                            text = stringResource(R.string.open_app_settings),
                            iconStart = R.drawable.ic_settings,
                        ) { context.openAppDetailsSettings() }
                    } else {
                        IvyButton(
                            text = stringResource(R.string.grant_permission),
                            iconStart = R.drawable.ic_check,
                        ) { permissionLauncher.launch(Manifest.permission.READ_SMS) }
                    }
                }
            }
        }

        val report = state.report
        if (state.enabled && !state.loading) {
            if (state.accounts.size > 1) {
                item {
                    AccountChips(
                        accounts = state.accounts,
                        selected = state.selectedAccount,
                        onSelect = { onEvent(UtilityBillsEvent.SelectAccount(it)) }
                    )
                }
            }
            if (report == null) {
                if (state.hasPermission) {
                    item {
                        Spacer(Modifier.height(12.dp))
                        InfoCard(text = stringResource(R.string.utility_no_bills)) {}
                    }
                }
            } else {
                item {
                    ReportCard(title = stringResource(R.string.utility_latest_bill)) {
                        LatestBill(bill = report.bills.first(), previous = report.bills.getOrNull(1))
                    }
                }
                item {
                    ReportCard(title = stringResource(R.string.utility_bill_by_bill)) {
                        BillBars(bills = report.bills)
                    }
                }
                item {
                    ReportCard(title = stringResource(R.string.utility_averages)) {
                        Averages(report = report)
                    }
                }
                item {
                    ReportCard(title = stringResource(R.string.utility_all_bills)) {
                        BillList(bills = report.bills)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(96.dp)) }
    }
}

/** Reports card: the latest bill, or a way in to turn tracking on. */
@Composable
fun UtilityBillsCard(card: UtilityCard, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    ReportCard(title = stringResource(R.string.utility_bills), modifier = modifier) {
        val latest = card.latest
        when {
            !card.enabled -> Text(
                text = stringResource(R.string.utility_bills_off),
                style = UI.typo.b2.style(color = UI.colors.gray, fontWeight = FontWeight.Medium)
            )

            latest == null -> Text(
                text = stringResource(R.string.utility_no_bills),
                style = UI.typo.b2.style(color = UI.colors.gray, fontWeight = FontWeight.Medium)
            )

            else -> {
                Text(
                    text = period(latest),
                    style = UI.typo.c.style(color = UI.colors.gray, fontWeight = FontWeight.Bold)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${latest.total.format(latest.currency)} ${latest.currency}",
                    style = UI.typo.nB1.style(fontWeight = FontWeight.ExtraBold)
                )
                Spacer(Modifier.height(8.dp))
                val currency = latest.currency
                PartRow(ElectricityColor, stringResource(R.string.utility_electricity), latest.electricity, currency)
                PartRow(WaterColor, stringResource(R.string.utility_water), latest.water, currency)
                PartRow(GasColor, stringResource(R.string.utility_gas), latest.gas, currency)
            }
        }
        Spacer(Modifier.height(12.dp))
        ReportChip(
            text = stringResource(if (card.enabled) R.string.utility_see_all else R.string.utility_set_up),
            selected = false,
            onClick = onOpen
        )
    }
}

@Composable
private fun TrackRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
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
            Text(
                text = stringResource(R.string.track_utility_bills),
                style = UI.typo.b2.style(fontWeight = FontWeight.Bold)
            )
            Text(
                text = stringResource(R.string.track_utility_bills_desc),
                style = UI.typo.c.style(color = UI.colors.gray, fontWeight = FontWeight.Medium)
            )
        }
        Spacer(Modifier.width(12.dp))
        IvySwitch(enabled = checked) { onCheckedChange(it) }
    }
}

@Composable
private fun InfoCard(text: String, action: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .border(2.dp, UI.colors.medium, UI.shapes.r4)
            .padding(20.dp)
    ) {
        Text(text = text, style = UI.typo.b2.style(fontWeight = FontWeight.Medium))
        Spacer(Modifier.height(12.dp))
        action()
    }
}

@Composable
private fun AccountChips(accounts: ImmutableList<String>, selected: String?, onSelect: (String) -> Unit) {
    Row(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
        accounts.forEach { account ->
            ReportChip(
                text = stringResource(R.string.utility_account, account.takeLast(AccountDigits)),
                selected = account == selected,
            ) { onSelect(account) }
            Spacer(Modifier.width(8.dp))
        }
    }
}

@Composable
private fun ColumnScope.LatestBill(bill: UtilityBill, previous: UtilityBill?) {
    Text(
        text = stringResource(R.string.utility_period_days, period(bill), bill.days),
        style = UI.typo.c.style(color = UI.colors.gray, fontWeight = FontWeight.Bold)
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = "${bill.total.format(bill.currency)} ${bill.currency}",
        style = UI.typo.nH2.style(fontWeight = FontWeight.ExtraBold)
    )
    bill.due?.let {
        Text(
            text = stringResource(R.string.utility_due, it.format(DateTimeFormatter.ofPattern("d MMM yyyy"))),
            style = UI.typo.c.style(color = UI.colors.gray, fontWeight = FontWeight.SemiBold)
        )
    }
    Spacer(Modifier.height(12.dp))
    val currency = bill.currency
    PartRow(
        color = ElectricityColor,
        name = stringResource(R.string.utility_electricity),
        amount = bill.electricity,
        currency = currency,
        previous = previous?.electricity
    )
    PartRow(WaterColor, stringResource(R.string.utility_water), bill.water, currency, previous?.water)
    PartRow(GasColor, stringResource(R.string.utility_gas), bill.gas, currency, previous?.gas)
    PartRow(RestColor, stringResource(R.string.utility_municipality), bill.municipality, currency)
    PartRow(RestColor, stringResource(R.string.utility_vat), bill.vat, currency)
    if (bill.others > 0) PartRow(RestColor, stringResource(R.string.utility_others), bill.others, currency)
    if (bill.arrears > 0) PartRow(RestColor, stringResource(R.string.utility_arrears), bill.arrears, currency)
}

/** One line of a bill; with [previous], the change since the bill before (more is red). */
@Composable
private fun PartRow(color: Color, name: String, amount: Double, currency: String, previous: Double? = null) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, UI.shapes.rFull)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            modifier = Modifier.weight(1f),
            text = name,
            style = UI.typo.b2.style(fontWeight = FontWeight.Bold)
        )
        previous?.let { UtilityCalculator.changePercent(amount, it) }?.takeIf { it != 0 }?.let { change ->
            Text(
                text = stringResource(
                    if (change > 0) R.string.utility_change_up else R.string.utility_change_down,
                    abs(change)
                ),
                style = UI.typo.nC.style(color = if (change > 0) Red else Green, fontWeight = FontWeight.Bold)
            )
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text = amount.format(currency),
            style = UI.typo.nB2.style(fontWeight = FontWeight.ExtraBold)
        )
    }
}

@Composable
private fun BillBars(bills: ImmutableList<UtilityBill>) {
    StackedBars(
        bars = bills.take(ChartBills).asReversed().map {
            StackedBar(
                month = YearMonth.from(it.end),
                parts = persistentListOf(it.electricity, it.water, it.gas, it.rest)
            )
        }.toImmutableList(),
        colors = persistentListOf(ElectricityColor, WaterColor, GasColor, RestColor),
        legend = persistentListOf(
            stringResource(R.string.utility_electricity),
            stringResource(R.string.utility_water),
            stringResource(R.string.utility_gas),
            stringResource(R.string.utility_other),
        ),
    )
}

@Composable
private fun ColumnScope.Averages(report: UtilityReport) {
    val bill = report.bills.first()
    AverageRow(stringResource(R.string.utility_average_bill), report.averageCharges, bill.currency, bold = true)
    AverageRow(stringResource(R.string.utility_electricity), report.averageElectricity, bill.currency)
    AverageRow(stringResource(R.string.utility_water), report.averageWater, bill.currency)
    AverageRow(stringResource(R.string.utility_gas), report.averageGas, bill.currency)
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(
            R.string.utility_totals,
            report.thisYear.format(bill.currency),
            report.allTime.format(bill.currency),
            report.bills.size
        ),
        style = UI.typo.c.style(color = UI.colors.gray, fontWeight = FontWeight.SemiBold)
    )
}

@Composable
private fun AverageRow(name: String, amount: Double, currency: String, bold: Boolean = false) {
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            modifier = Modifier.weight(1f),
            text = name,
            style = UI.typo.b2.style(fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Medium)
        )
        Text(
            text = "${amount.format(currency)} $currency",
            style = UI.typo.nB2.style(fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Bold)
        )
    }
}

@Composable
private fun ColumnScope.BillList(bills: ImmutableList<UtilityBill>) {
    val electricity = stringResource(R.string.utility_electricity)
    val water = stringResource(R.string.utility_water)
    val gas = stringResource(R.string.utility_gas)
    bills.forEach { bill ->
        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = period(bill), style = UI.typo.b2.style(fontWeight = FontWeight.Bold))
                Text(
                    text = "$electricity ${bill.electricity.format(bill.currency)} · " +
                        "$water ${bill.water.format(bill.currency)} · $gas ${bill.gas.format(bill.currency)}",
                    style = UI.typo.c.style(color = UI.colors.gray, fontWeight = FontWeight.Medium)
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = bill.total.format(bill.currency),
                style = UI.typo.nB2.style(fontWeight = FontWeight.ExtraBold)
            )
        }
    }
}

/** "17 Sep – 17 Oct 2026" */
private fun period(bill: UtilityBill): String {
    val sameYear = bill.start.year == bill.end.year
    val start = bill.start.format(DateTimeFormatter.ofPattern(if (sameYear) "d MMM" else "d MMM yyyy"))
    return "$start – ${bill.end.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}"
}

private fun Context.openAppDetailsSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

private val ElectricityColor = Orange
private val WaterColor = Blue
private val GasColor = Ivy
private val RestColor = Gray
private const val ChartBills = 12
private const val AccountDigits = 4
