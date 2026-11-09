package com.lstepnio.egauge.virtual

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.lstepnio.egauge.BuildConfig
import com.lstepnio.egauge.core.designsystem.*
import com.lstepnio.egauge.ui.ScreenContent
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DAY = 86_400L
private const val MAX_GRANT_SIZE = 2_048

internal enum class VirtualAccessKind { Trial, Subscription }

internal data class VirtualAccessGrant(val kind: VirtualAccessKind, val expiresAtSeconds: Long)

/** Verify a grant from the billing service. A local setting or checkout redirect cannot unlock access. */
internal fun verifyVirtualAccess(
    token: String,
    key: PublicKey,
    installation: String,
    nowSeconds: Long
): VirtualAccessGrant? = runCatching {
    require(token.length in 1..MAX_GRANT_SIZE)
    val parts = token.split('.')
    require(parts.size == 2 && parts.all { it.matches(Regex("[A-Za-z0-9_-]+")) })
    val decoder = Base64.getUrlDecoder()
    val payload = decoder.decode(parts[0])
    val signature = Signature.getInstance("SHA256withECDSA")
    signature.initVerify(key)
    signature.update(payload)
    require(signature.verify(decoder.decode(parts[1])))
    val fields = payload.decodeToString(throwOnInvalidSequence = true).split('|')
    require(fields.size == 7 && fields[0] == "EGVG1" && fields[1] == "virtual-gauge" && fields[2] == installation)
    val kind = VirtualAccessKind.valueOf(fields[3])
    val starts = fields[4].toLong()
    val ends = fields[5].toLong()
    val issued = fields[6].toLong()
    require(starts in 1..issued && issued <= nowSeconds && nowSeconds < ends)
    require(kind != VirtualAccessKind.Trial || ends - starts <= 30 * DAY)
    require(nowSeconds - issued < 3 * DAY)
    VirtualAccessGrant(kind, ends)
}.getOrNull()

@Composable
internal fun VirtualGaugeAccess(
    onBack: () -> Unit,
    dashboard: @Composable (onManageAccess: (() -> Unit)?) -> Unit
) {
    // Developer APKs are for bench testing. A subscription release uses the release build.
    if (BuildConfig.DEBUG) {
        dashboard(null)
        return
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("virtual-access", 0) }
    val installation = remember {
        prefs.getString("installation", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("installation", it).apply()
        }
    }
    val key = remember {
        runCatching {
            KeyFactory.getInstance("EC").generatePublic(
                X509EncodedKeySpec(Base64.getDecoder().decode(BuildConfig.VIRTUAL_GAUGE_PUBLIC_KEY))
            )
        }.getOrNull()
    }
    fun evaluate(token: String?): VirtualAccessGrant? {
        if (key == null || token == null) return null
        val wallTime = System.currentTimeMillis() / 1_000
        val latestCheck = prefs.getLong("latest-check", 0)
        if (wallTime < latestCheck - 300) return null
        val now = maxOf(wallTime, latestCheck)
        prefs.edit().putLong("latest-check", now).apply()
        return verifyVirtualAccess(token, key, installation, now)
    }
    var access by remember { mutableStateOf(evaluate(prefs.getString("grant", null))) }
    var manage by rememberSaveable { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    LifecycleResumeEffect(Unit) {
        access = evaluate(prefs.getString("grant", null))
        onPauseOrDispose {}
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val token = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            val bytes = ByteArray(MAX_GRANT_SIZE + 1)
                            var size = 0
                            while (size < bytes.size) {
                                val count = input.read(bytes, size, bytes.size - size)
                                if (count <= 0) break
                                size += count
                            }
                            if (size > MAX_GRANT_SIZE) null else bytes.decodeToString(endIndex = size, throwOnInvalidSequence = true).trim()
                        }
                    }.getOrNull()
                }
                val grant = evaluate(token)
                if (grant == null) {
                    notice = "This access file has expired or belongs to another installation."
                } else {
                    prefs.edit().putString("grant", token).apply()
                    access = grant
                    manage = false
                    notice = null
                }
            }
        }
    }
    if (access != null && !manage) {
        dashboard { manage = true }
        return
    }
    ScreenContent {
        ScreenTitle("Virtual Gauge access", { if (manage) manage = false else onBack() }, trailing = null)
        if (key == null || BuildConfig.VIRTUAL_GAUGE_CHECKOUT.isEmpty()) {
            EmptyState("Subscription setup is pending", "Virtual Gauge will offer a free 30-day trial before the paid subscription.")
        } else {
            Text(
                when (access?.kind) {
                    VirtualAccessKind.Trial -> "Your free trial is active."
                    VirtualAccessKind.Subscription -> "Your subscription is active."
                    null -> "Try Virtual Gauge free for 30 days."
                }
            )
            Text("Review the price, renewal date and cancellation terms on the eGauge website.")
            PrimaryAction(if (access == null) "View trial and subscription" else "Manage subscription", {
                val url = Uri.parse(BuildConfig.VIRTUAL_GAUGE_CHECKOUT).buildUpon()
                    .appendQueryParameter("installation", installation).build()
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, url))
                } catch (_: ActivityNotFoundException) {
                    notice = "Open the eGauge website in a browser to manage access."
                }
            })
            TextButton({ restore.launch(arrayOf("application/octet-stream", "text/plain")) }) { Text("Restore access from file") }
        }
        notice?.let { StatusCard(StatusUi(it, tone = StatusTone.Warning)) }
    }
}
