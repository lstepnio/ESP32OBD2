package com.lstepnio.egauge.premium

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.lstepnio.egauge.core.designsystem.PrimaryAction

@Composable
internal fun PremiumOffer(terms: SubscriptionTerms, enabled: Boolean, onPurchase: () -> Unit) {
    Text(terms.trial?.let { "$it, then ${terms.renewal}." } ?: "${terms.renewal}.")
    Text("Renews automatically until canceled in Google Play.")
    PrimaryAction("Continue to Google Play", onPurchase, enabled = enabled)
}
