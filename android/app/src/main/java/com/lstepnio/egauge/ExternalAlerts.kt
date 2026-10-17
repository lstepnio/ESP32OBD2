package com.lstepnio.egauge

/** Providers return typed notices only. They never choose BLE commands or critical priority. */
interface PhoneAlertProvider {
    val id: String
    suspend fun notices(vehicle: String, receivedAtElapsedMs: Long): List<PhoneAlert>
}
data class PhoneAlert(val provider: String,val correlationId: String,val vehicle: String,
    val title: String,val context: String,val severity: AlertSeverity,val ttlMs: Long,
    val receivedAtElapsedMs: Long,val simulated: Boolean=false)
data class ProviderReview(val id: String,val maximumSeverity: AlertSeverity,val maximumTtlMs: Long,
    val authorized: Boolean,val attribution: String)
class PhoneAlertBridge(private val reviews: List<ProviderReview>) {
    fun validate(alert: PhoneAlert, vehicle: String, now: Long): PhoneAlert {
        val review=reviews.singleOrNull { it.id==alert.provider && it.authorized }
            ?: error("This alert provider has not been reviewed")
        require(alert.vehicle==vehicle && alert.correlationId.length in 1..128 &&
            alert.title.isNotBlank() && alert.title.toByteArray().size<=31 &&
            alert.context.toByteArray().size<=11 && alert.title.none { it.isISOControl() } &&
            alert.context.none { it.isISOControl() } && alert.ttlMs in 1..review.maximumTtlMs &&
            alert.severity in AlertSeverity.Information..AlertSeverity.Warning && now>=alert.receivedAtElapsedMs)
        val remaining=alert.ttlMs-(now-alert.receivedAtElapsedMs)
        require(remaining>0) { "This notice has expired" }
        return alert.copy(ttlMs=remaining,severity=minOf(alert.severity,review.maximumSeverity))
    }
    companion object {
        val fixtures=PhoneAlertBridge(listOf(ProviderReview("synthetic",AlertSeverity.Advisory,60000,true,"eGauge offline test")))
    }
}
class SyntheticPhoneAlertProvider : PhoneAlertProvider {
    override val id="synthetic"
    override suspend fun notices(vehicle: String,receivedAtElapsedMs: Long) = listOf(
        PhoneAlert(id,"road-hazard-fixture",vehicle,"Phone alert test","Road hazard",AlertSeverity.Advisory,30000,receivedAtElapsedMs,true))
}
