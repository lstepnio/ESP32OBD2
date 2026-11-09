package com.lstepnio.egauge

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.lstepnio.egauge.core.designsystem.EGaugeTheme
import com.lstepnio.egauge.core.designsystem.ScreenTitle
import com.lstepnio.egauge.premium.PremiumOffer
import com.lstepnio.egauge.premium.SubscriptionTerms
import com.lstepnio.egauge.ui.ScreenContent
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PremiumSubscriptionUiTest {
    @get:Rule val compose = createComposeRule()

    private fun render(fontScale: Float, trial: String?, onPurchase: () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                EGaugeTheme {
                    ScreenContent {
                        ScreenTitle("Virtual Gauge", trailing = null)
                        PremiumOffer(SubscriptionTerms("$4.99 per month", trial), true, onPurchase)
                    }
                }
            }
        }
    }

    @Test fun largeTextShowsTrialConversionAndPurchaseAction() {
        var purchases = 0
        render(2f, "30 days free") { purchases++ }
        compose.onNodeWithText("30 days free, then $4.99 per month.").assertIsDisplayed()
        compose.onNodeWithText("Renews automatically until canceled in Google Play.").assertIsDisplayed()
        val action = compose.onNodeWithText("Continue to Google Play").performScrollTo().assertIsDisplayed()
        capture("virtual-subscription-large-text")
        action.performClick()
        assertEquals(1, purchases)
    }

    @Test fun compactOfferWithoutTrialShowsOnlyTheRecurringPrice() {
        render(1f, null) {}
        compose.onNodeWithText("$4.99 per month.").assertIsDisplayed()
        compose.onAllNodesWithText("free", substring = true).assertCountEquals(0)
        capture("virtual-subscription-compact")
    }

    private fun capture(name: String) {
        val path = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: return
        File(path, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
