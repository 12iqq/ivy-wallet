@file:Suppress("InvalidPackageDeclaration")

package com.ivy.wallet.ui.theme.modal.edit

import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ivy.data.model.CardEndings
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.legacy.utils.selectEndTextFieldValue
import com.ivy.ui.R
import com.ivy.wallet.ui.theme.components.IvyTitleTextField
import com.ivy.wallet.ui.theme.modal.IvyModal
import com.ivy.wallet.ui.theme.modal.ModalSave
import com.ivy.wallet.ui.theme.modal.ModalTitle
import java.util.UUID

/** Edit the last 4 digits of the cards / account numbers of an account ("1234, 5678"). */
@Suppress("ModifierMissing", "ComposeModifierMissing")
@Composable
fun BoxWithConstraintsScope.CardEndingsModal(
    visible: Boolean,
    endings: String?,
    dismiss: () -> Unit,
    onSave: (String?) -> Unit,
) {
    val id = remember(visible) { UUID.randomUUID() }
    var text by remember(id) { mutableStateOf(selectEndTextFieldValue(endings.orEmpty())) }

    IvyModal(
        id = id,
        visible = visible,
        dismiss = dismiss,
        PrimaryAction = {
            ModalSave {
                onSave(CardEndings.format(CardEndings.parse(text.text)))
                dismiss()
            }
        }
    ) {
        Spacer(Modifier.height(32.dp))
        ModalTitle(text = stringResource(R.string.card_endings))
        Spacer(Modifier.height(8.dp))
        Text(
            modifier = Modifier.padding(horizontal = 32.dp),
            text = stringResource(R.string.card_endings_desc),
            style = UI.typo.c.style(color = UI.colors.gray, fontWeight = FontWeight.Medium)
        )
        Spacer(Modifier.height(24.dp))
        IvyTitleTextField(
            modifier = Modifier.padding(horizontal = 32.dp),
            dividerModifier = Modifier.padding(horizontal = 24.dp),
            value = text,
            hint = "1234, 5678",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Done,
            ),
        ) { text = it }
        Spacer(Modifier.height(48.dp))
    }
}
