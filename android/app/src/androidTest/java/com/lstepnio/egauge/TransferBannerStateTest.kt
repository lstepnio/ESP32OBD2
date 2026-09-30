package com.lstepnio.egauge

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lstepnio.egauge.core.designsystem.EGaugeTheme
import com.lstepnio.egauge.ui.rememberTransferBannerState
import com.lstepnio.egauge.ui.state.operationUi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransferBannerStateTest {
    @get:Rule val compose = createComposeRule()

    @Test fun confirmedSuccessClearsAfterDelay() {
        val success = operationUi(OperationState(1, OperationKind.CONFIGURATION,
            OperationStage.ACTIVE, "raw", terminal = true), confirmedSetup = true)
        compose.mainClock.autoAdvance = false
        compose.setContent { EGaugeTheme {
            val banner = rememberTransferBannerState(success)
            if (banner.visible) Button(banner.dismiss) { Text("Dismiss success") } else Text("Banner cleared")
        } }
        compose.onNodeWithText("Dismiss success").assertExists()
        compose.mainClock.advanceTimeBy(5_000)
        compose.onNodeWithText("Dismiss success").assertExists()
        compose.mainClock.advanceTimeBy(1_100)
        compose.onNodeWithText("Banner cleared").assertExists()
    }

    @Test fun confirmedSuccessCanBeDismissedImmediately() {
        val success = operationUi(OperationState(3, OperationKind.UPDATE,
            OperationStage.ACTIVE, "raw", terminal = true))
        compose.setContent { EGaugeTheme {
            val banner = rememberTransferBannerState(success)
            if (banner.visible) Button(banner.dismiss) { Text("Dismiss success") } else Text("Banner cleared")
        } }
        compose.onNodeWithText("Dismiss success").performClick()
        compose.onNodeWithText("Banner cleared").assertExists()
    }

    @Test fun uncertainOutcomeNeverClearsOnTheSuccessTimer() {
        val unknown = operationUi(OperationState(2, OperationKind.UPDATE,
            OperationStage.OUTCOME_UNKNOWN, "raw", terminal = true))
        compose.mainClock.autoAdvance = false
        compose.setContent { EGaugeTheme {
            val banner = rememberTransferBannerState(unknown)
            Text(if (banner.visible) "Check required" else "Banner cleared")
        } }
        compose.mainClock.advanceTimeBy(10_000)
        compose.onNodeWithText("Check required").assertExists()
    }
}
