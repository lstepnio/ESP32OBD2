package com.lstepnio.egauge.premium

import androidx.compose.runtime.Composable

/** Development builds exercise the dashboard without including a payment client. */
@Composable
internal fun PremiumGate(
    feature: PremiumFeature,
    onBack: () -> Unit,
    content: @Composable (onManageAccess: (() -> Unit)?) -> Unit
) {
    content(null)
}
