package com.ivy.wallet.ui.theme.modal.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.domain.legacy.ui.IvyColorPicker
import com.ivy.legacy.IvyWalletPreview
import com.ivy.legacy.datamodel.Account
import com.ivy.legacy.utils.format
import com.ivy.legacy.utils.isNotNullOrBlank
import com.ivy.legacy.utils.onScreenStart
import com.ivy.legacy.utils.selectEndTextFieldValue
import com.ivy.legacy.utils.toLowerCaseLocal
import com.ivy.legacy.utils.toUpperCaseLocal
import com.ivy.ui.R
import com.ivy.wallet.domain.data.IvyCurrency
import com.ivy.wallet.domain.deprecated.logic.model.CreateAccountData
import com.ivy.wallet.ui.theme.Gray
import com.ivy.wallet.ui.theme.Ivy
import com.ivy.wallet.ui.theme.components.IvyCheckboxWithText
import com.ivy.wallet.ui.theme.modal.ChooseIconModal
import com.ivy.wallet.ui.theme.modal.CurrencyModal
import com.ivy.wallet.ui.theme.modal.IvyModal
import com.ivy.wallet.ui.theme.modal.ModalAddSave
import com.ivy.wallet.ui.theme.modal.ModalAmountSection
import com.ivy.wallet.ui.theme.modal.ModalTitle
import java.util.UUID

@Deprecated("Old design system. Use `:ivy-design` and Material3")
data class AccountModalData(
    val account: Account?,
    val baseCurrency: String,
    val balance: Double,
    val adjustBalanceMode: Boolean = false,
    val forceNonZeroBalance: Boolean = false,
    val autoFocusKeyboard: Boolean = true,
    val id: UUID = UUID.randomUUID()
)

@Deprecated("Old design system. Use `:ivy-design` and Material3")
@Suppress("CyclomaticComplexMethod", "LongMethod")
@Composable
fun BoxWithConstraintsScope.AccountModal(
    modal: AccountModalData?,
    onCreateAccount: (CreateAccountData) -> Unit,
    onEditAccount: (Account, balance: Double) -> Unit,
    dismiss: () -> Unit,
) {
    val account = modal?.account
    var nameTextFieldValue by remember(modal) {
        mutableStateOf(selectEndTextFieldValue(account?.name))
    }
    var color by remember(modal) {
        mutableStateOf(account?.color?.let { Color(it) } ?: Ivy)
    }
    var amount by remember(modal) {
        // credit cards show the amount owed as a positive number
        val balance = modal?.balance ?: 0.0
        mutableStateOf(if (account?.isCreditCard == true) -balance else balance)
    }
    var currencyCode by remember(modal) {
        mutableStateOf(account?.currency ?: modal?.baseCurrency ?: "")
    }
    var icon by remember(modal) {
        mutableStateOf(account?.icon)
    }
    var includeInBalance by remember(modal) {
        mutableStateOf(account?.includeInBalance ?: true)
    }
    var isCreditCard by remember(modal) {
        mutableStateOf(account?.isCreditCard ?: false)
    }
    var creditLimit by remember(modal) {
        mutableStateOf(account?.creditLimit)
    }
    var statementDay by remember(modal) {
        mutableStateOf(account?.statementDay)
    }
    var paymentDueDay by remember(modal) {
        mutableStateOf(account?.paymentDueDay)
    }
    var creditLimitModalVisible by remember { mutableStateOf(false) }

    var amountModalVisible by remember { mutableStateOf(false) }
    var currencyModalVisible by remember { mutableStateOf(false) }
    var chooseIconModalVisible by remember(modal) {
        mutableStateOf(false)
    }

    val forceNonZeroBalance = modal?.forceNonZeroBalance ?: false

    IvyModal(
        id = modal?.id,
        visible = modal != null,
        dismiss = dismiss,
        shiftIfKeyboardShown = false,
        PrimaryAction = {
            ModalAddSave(
                item = modal?.account,
                enabled = nameTextFieldValue.text.isNotNullOrBlank() && (!forceNonZeroBalance || amount > 0)
            ) {
                save(
                    account = account,
                    nameTextFieldValue = nameTextFieldValue,
                    currency = currencyCode,
                    color = color,
                    icon = icon,
                    amount = amount.toBalance(isCreditCard),
                    includeInBalance = includeInBalance,
                    card = CardFields(isCreditCard, creditLimit, statementDay, paymentDueDay),

                    onCreateAccount = onCreateAccount,
                    onEditAccount = onEditAccount,
                    dismiss = dismiss
                )
            }
        }
    ) {
        onScreenStart {
            if (modal?.adjustBalanceMode == true) {
                amountModalVisible = true
            }
        }

        Spacer(Modifier.height(32.dp))

        ModalTitle(
            text = if (modal?.account != null) {
                stringResource(
                    R.string.edit_account
                )
            } else {
                stringResource(R.string.new_account)
            },
        )

        Spacer(Modifier.height(24.dp))

        IconNameRow(
            hint = stringResource(R.string.account_name),
            defaultIcon = R.drawable.ic_custom_account_m,
            color = color,
            icon = icon,

            autoFocusKeyboard = modal?.autoFocusKeyboard ?: true,

            nameTextFieldValue = nameTextFieldValue,
            setNameTextFieldValue = { nameTextFieldValue = it },
            showChooseIconModal = {
                chooseIconModalVisible = true
            }
        )

        Spacer(Modifier.height(24.dp))

        IvyColorPicker(
            selectedColor = color,
            onColorSelected = { color = it }
        )

        Spacer(modifier = Modifier.height(40.dp))

        ModalAmountSection(
            Header = {
                Spacer(Modifier.height(16.dp))

                AccountCurrency(
                    currencyCode = currencyCode
                ) {
                    currencyModalVisible = true
                }

                Spacer(modifier = Modifier.height(16.dp))

                IvyCheckboxWithText(
                    modifier = Modifier
                        .padding(start = 16.dp)
                        .align(Alignment.Start),
                    text = stringResource(R.string.include_account),
                    checked = includeInBalance
                ) {
                    includeInBalance = it
                }

                Spacer(modifier = Modifier.height(8.dp))

                IvyCheckboxWithText(
                    modifier = Modifier
                        .padding(start = 16.dp)
                        .align(Alignment.Start)
                        .testTag("account_modal_credit_card"),
                    text = stringResource(R.string.credit_card),
                    checked = isCreditCard
                ) {
                    isCreditCard = it
                }

                if (isCreditCard) {
                    Spacer(modifier = Modifier.height(16.dp))

                    CardSettingRow(
                        label = stringResource(R.string.credit_limit),
                        value = creditLimit?.let { "${it.format(currencyCode)} $currencyCode" }
                            ?: stringResource(R.string.not_set),
                        onClick = { creditLimitModalVisible = true }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    DayOfMonthRow(
                        label = stringResource(R.string.statement_day),
                        day = statementDay,
                        onDayChange = { statementDay = it }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    DayOfMonthRow(
                        label = stringResource(R.string.payment_due_day),
                        day = paymentDueDay,
                        onDayChange = { paymentDueDay = it }
                    )
                }
            },
            label = if (isCreditCard) {
                stringResource(R.string.enter_amount_owed).uppercase()
            } else {
                stringResource(R.string.enter_account_balance).uppercase()
            },
            currency = currencyCode,
            amount = amount,
            amountPaddingTop = 40.dp,
            amountPaddingBottom = 40.dp,
        ) {
            amountModalVisible = true
        }
    }

    val amountModalId = remember(modal, amount) {
        UUID.randomUUID()
    }
    AmountModal(
        id = amountModalId,
        visible = amountModalVisible,
        currency = currencyCode,
        initialAmount = amount,
        showPlusMinus = true,
        dismiss = { amountModalVisible = false }
    ) { newAmount ->
        amount = newAmount

        if (modal?.adjustBalanceMode == true) {
            save(
                account = account,
                nameTextFieldValue = nameTextFieldValue,
                currency = currencyCode,
                color = color,
                icon = icon,
                amount = newAmount.toBalance(isCreditCard),
                includeInBalance = includeInBalance,
                card = CardFields(isCreditCard, creditLimit, statementDay, paymentDueDay),

                onCreateAccount = onCreateAccount,
                onEditAccount = onEditAccount,
                dismiss = dismiss
            )
        }
    }

    AmountModal(
        id = remember(modal, creditLimit) { UUID.randomUUID() },
        visible = creditLimitModalVisible,
        currency = currencyCode,
        initialAmount = creditLimit,
        dismiss = { creditLimitModalVisible = false }
    ) { newLimit ->
        creditLimit = newLimit.takeIf { it > 0 }
    }

    val context = LocalContext.current
    CurrencyModal(
        title = stringResource(R.string.choose_currency),
        initialCurrency = IvyCurrency.fromCode(currencyCode),
        visible = currencyModalVisible,
        dismiss = { currencyModalVisible = false }
    ) {
        currencyCode = it

//        if (IvyCurrency.fromCode(it)?.isCrypto == true) {
//            if (getCustomIconId(context = context, iconName = it, size = "m") != null) {
//                icon = it
//            }
//        }
    }

    ChooseIconModal(
        visible = chooseIconModalVisible,
        initialIcon = icon ?: "account",
        color = color,
        dismiss = { chooseIconModalVisible = false }
    ) {
        icon = it
    }
}

private fun save(
    account: Account?,
    nameTextFieldValue: TextFieldValue,
    currency: String,
    color: Color,
    icon: String?,
    amount: Double,
    includeInBalance: Boolean,
    card: CardFields,

    onCreateAccount: (CreateAccountData) -> Unit,
    onEditAccount: (Account, balance: Double) -> Unit,
    dismiss: () -> Unit
) {
    if (account != null) {
        onEditAccount(
            account.copy(
                name = nameTextFieldValue.text.trim(),
                currency = currency,
                includeInBalance = includeInBalance,
                icon = icon,
                color = color.toArgb(),
                isCreditCard = card.isCreditCard,
                creditLimit = card.creditLimit.takeIf { card.isCreditCard },
                statementDay = card.statementDay.takeIf { card.isCreditCard },
                paymentDueDay = card.paymentDueDay.takeIf { card.isCreditCard },
            ),
            amount
        )
    } else {
        onCreateAccount(
            CreateAccountData(
                name = nameTextFieldValue.text.trim(),
                currency = currency,
                color = color,
                icon = icon,
                balance = amount,
                includeBalance = includeInBalance,
                isCreditCard = card.isCreditCard,
                creditLimit = card.creditLimit.takeIf { card.isCreditCard },
                statementDay = card.statementDay.takeIf { card.isCreditCard },
                paymentDueDay = card.paymentDueDay.takeIf { card.isCreditCard },
            )
        )
    }

    dismiss()
}

private data class CardFields(
    val isCreditCard: Boolean,
    val creditLimit: Double?,
    val statementDay: Int?,
    val paymentDueDay: Int?,
)

/** Credit cards are edited as "amount owed" but stored as a negative balance. */
private fun Double.toBalance(isCreditCard: Boolean): Double = if (isCreditCard) -this else this

@Composable
private fun CardSettingRow(
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .background(UI.colors.medium, UI.shapes.r4)
            .clip(UI.shapes.r4)
            .clickable { onClick() }
            .padding(vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(24.dp))
        Text(
            text = label,
            style = UI.typo.b2.style(fontWeight = FontWeight.SemiBold)
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            style = UI.typo.b2.style(fontWeight = FontWeight.ExtraBold)
        )
        Spacer(Modifier.width(24.dp))
    }
}

@Suppress("MagicNumber")
@Composable
private fun DayOfMonthRow(
    label: String,
    day: Int?,
    onDayChange: (Int?) -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .background(UI.colors.medium, UI.shapes.r4)
            .clip(UI.shapes.r4)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(24.dp))
        Text(
            text = label,
            style = UI.typo.b2.style(fontWeight = FontWeight.SemiBold)
        )
        Spacer(Modifier.weight(1f))
        StepperButton(text = "−") {
            onDayChange(
                when {
                    day == null -> 28
                    day <= 1 -> null
                    else -> day - 1
                }
            )
        }
        Text(
            modifier = Modifier.width(64.dp),
            text = day?.let { stringResource(R.string.day_of_month_n, it) }
                ?: stringResource(R.string.not_set),
            style = UI.typo.b2.style(
                fontWeight = FontWeight.ExtraBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        )
        StepperButton(text = "+") {
            onDayChange(
                when {
                    day == null -> 1
                    day >= 31 -> null
                    else -> day + 1
                }
            )
        }
        Spacer(Modifier.width(16.dp))
    }
}

@Composable
private fun StepperButton(text: String, onClick: () -> Unit) {
    Text(
        modifier = Modifier
            .clip(UI.shapes.rFull)
            .background(UI.colors.pure, UI.shapes.rFull)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp),
        text = text,
        style = UI.typo.b1.style(fontWeight = FontWeight.Bold)
    )
}

@Composable
private fun AccountCurrency(
    currencyCode: String,

    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .background(UI.colors.medium, UI.shapes.r4)
            .clip(UI.shapes.r4)
            .clickable {
                onClick()
            }
            .padding(vertical = 24.dp)
            .testTag("account_modal_currency"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(32.dp))

        Text(
            text = currencyCode.toUpperCaseLocal(),
            style = UI.typo.b1.style(
                fontWeight = FontWeight.ExtraBold
            )
        )

        Spacer(Modifier.weight(1f))

        val currencyName = IvyCurrency.fromCode(currencyCode)?.name ?: ""
        Text(
            text = "-$currencyName".toLowerCaseLocal(),
            style = UI.typo.b2.style(
                fontWeight = FontWeight.SemiBold,
                color = Gray
            )
        )

        Spacer(Modifier.width(24.dp))
    }
}

@Preview
@Composable
private fun Preview() {
    IvyWalletPreview {
        AccountModal(
            modal = AccountModalData(
                account = null,
                baseCurrency = "BGN",
                balance = 0.0
            ),
            onCreateAccount = { },
            onEditAccount = { _, _ -> }
        ) {
        }
    }
}
