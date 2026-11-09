package com.lstepnio.egauge.premium

import java.security.PublicKey
import java.security.Signature
import java.util.Base64

internal enum class PremiumFeature(val title: String, val productId: String) {
    VirtualGauge("Virtual Gauge", "egauge_virtual_gauge")
}

internal data class SubscriptionPhase(
    val price: String,
    val amountMicros: Long,
    val period: String,
    val recurring: Boolean,
    val cycles: Int
)

internal data class SubscriptionTerms(val renewal: String, val trial: String?)

internal fun subscriptionTerms(phases: List<SubscriptionPhase>): SubscriptionTerms? {
    fun duration(period: String): Pair<Int, String>? {
        val parts = Regex("P([1-9][0-9]{0,2})([DWMY])").matchEntire(period) ?: return null
        val unit = when (parts.groupValues[2]) {
            "D" -> "day"
            "W" -> "week"
            "M" -> "month"
            else -> "year"
        }
        return parts.groupValues[1].toInt() to unit
    }
    if (phases.size !in 1..2) return null
    val renewal = phases.last()
    if (!renewal.recurring || renewal.amountMicros <= 0 || renewal.price.isBlank()) return null
    val (count, unit) = duration(renewal.period) ?: return null
    val price = if (count == 1) "${renewal.price} per $unit" else "${renewal.price} every $count ${unit}s"
    val trial = phases.first().takeIf { phases.size == 2 }?.let { initial ->
        if (initial.recurring || initial.amountMicros != 0L || initial.cycles !in 0..1) return null
        val (length, trialUnit) = duration(initial.period) ?: return null
        if (length == 1) "1 $trialUnit free" else "$length ${trialUnit}s free"
    }
    return SubscriptionTerms(price, trial)
}

/** The signature is checked against the exact JSON supplied by Play. */
internal fun validPlaySignature(json: String, encodedSignature: String, key: PublicKey): Boolean = runCatching {
    val signature = Signature.getInstance("SHA1withRSA")
    signature.initVerify(key)
    signature.update(json.encodeToByteArray())
    signature.verify(Base64.getDecoder().decode(encodedSignature))
}.getOrDefault(false)
