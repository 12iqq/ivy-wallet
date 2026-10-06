package com.ivy.autocapture.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.ivy.data.db.dao.write.CapturedTransactionDao
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.navigation.AutoCaptureReviewScreen
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.ivy.ui.R
import com.ivy.wallet.ui.theme.Ivy
import com.ivy.wallet.ui.theme.White
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AutoCaptureBannerViewModel @Inject constructor(
    capturedDao: CapturedTransactionDao,
) : ViewModel() {
    val pendingCount = capturedDao.pendingCountFlow()
}

/** "3 to review" pill shown on Home when bank transactions wait for review. */
@Composable
fun AutoCaptureReviewBanner(modifier: Modifier = Modifier) {
    val viewModel: AutoCaptureBannerViewModel = screenScopedViewModel()
    val count by viewModel.pendingCount.collectAsState(initial = 0)
    if (count <= 0) return

    val nav = navigation()
    Row(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(UI.shapes.rFull)
            .background(Ivy, UI.shapes.rFull)
            .clickable { nav.navigateTo(AutoCaptureReviewScreen) }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.new_bank_transaction),
            style = UI.typo.c.style(color = White, fontWeight = FontWeight.Bold)
        )
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.n_to_review, count),
            style = UI.typo.c.style(color = White, fontWeight = FontWeight.ExtraBold)
        )
    }
}
