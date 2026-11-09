package com.lstepnio.egauge.premium

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.lstepnio.egauge.BuildConfig
import com.lstepnio.egauge.core.designsystem.*
import com.lstepnio.egauge.ui.ScreenContent
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import kotlinx.coroutines.channels.Channel

@Composable
internal fun PremiumGate(
    feature: PremiumFeature,
    onBack: () -> Unit,
    content: @Composable (onManageAccess: (() -> Unit)?) -> Unit
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val key = remember {
        runCatching {
            KeyFactory.getInstance("RSA").generatePublic(
                X509EncodedKeySpec(Base64.getDecoder().decode(BuildConfig.PLAY_BILLING_PUBLIC_KEY))
            )
        }.getOrNull()
    }
    if (key == null) {
        ScreenContent {
            ScreenTitle(feature.title, onBack, trailing = null)
            EmptyState("Subscriptions are coming", "Purchase and restore access through Google Play once subscriptions are available.")
        }
        return
    }
    val refresh = remember { Channel<Unit>(Channel.CONFLATED) }
    val store = remember(context.applicationContext, key) { PlaySubscriptions(context, key) { refresh.trySend(Unit) } }
    var access by remember(feature) { mutableStateOf<PremiumAccess>(PremiumAccess.Checking) }
    var manage by rememberSaveable { mutableStateOf(false) }
    var checkout by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var manageNotice by remember { mutableStateOf(false) }
    DisposableEffect(store) {
        onDispose { store.close() }
    }
    LifecycleResumeEffect(feature, store) {
        checkout = false
        refresh.trySend(Unit)
        onPauseOrDispose {}
    }
    LaunchedEffect(store, feature) {
        for (ignored in refresh) {
            checking = true
            val result = store.access(feature)
            // Avoid taking a working gauge away on a transient failure. A successful
            // ownership query can still revoke access, and re-entry requires a new check.
            if (result != PremiumAccess.Unavailable || access != PremiumAccess.Active) access = result
            checkout = false
            checking = false
        }
    }
    if (access == PremiumAccess.Active && !manage) {
        content { manage = true }
        return
    }
    ScreenContent {
        ScreenTitle(feature.title, { if (manage) manage = false else onBack() }, trailing = null)
        when (val current = access) {
            PremiumAccess.Checking -> StatusCard(StatusUi("Checking Google Play", tone = StatusTone.Loading))

            PremiumAccess.Active -> {
                Text("Your subscription is active.")
                PrimaryAction("Manage in Google Play", {
                    val uri = Uri.parse("https://play.google.com/store/account/subscriptions").buildUpon()
                        .appendQueryParameter("sku", feature.productId)
                        .appendQueryParameter("package", context.packageName).build()
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    } catch (_: ActivityNotFoundException) {
                        manageNotice = true
                    }
                })
                if (manageNotice) Text("Open Google Play to manage your subscription.")
            }

            PremiumAccess.Pending -> Text("Google Play is processing your purchase. Access starts after payment is confirmed.")

            PremiumAccess.Unavailable -> Text("Google Play couldn’t confirm access. Check your connection and Play account, then try again.")

            PremiumAccess.NoOffer -> Text("No subscription offer is available for this Google Play account.")

            PremiumAccess.VerificationRequired -> Text("This purchase couldn’t be verified. Restore purchases from your Google Play account.")

            is PremiumAccess.Offered -> PremiumOffer(
                current.plan.terms,
                enabled = activity != null && !checkout && !checking,
                onPurchase = {
                    if (activity != null) {
                        checkout = store.purchase(activity, current.plan)
                        if (!checkout) refresh.trySend(Unit)
                    }
                }
            )
        }
        if (access !in setOf(PremiumAccess.Active, PremiumAccess.Checking)) {
            TextButton({ refresh.trySend(Unit) }, enabled = !checking && !checkout) { Text("Restore purchases") }
        }
    }
}
