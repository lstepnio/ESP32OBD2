package com.lstepnio.egauge.premium

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import java.security.PublicKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

internal sealed interface PremiumAccess {
    data object Checking : PremiumAccess
    data object Active : PremiumAccess
    data object Pending : PremiumAccess
    data object Unavailable : PremiumAccess
    data object NoOffer : PremiumAccess
    data object VerificationRequired : PremiumAccess
    data class Offered(val plan: PlayPlan) : PremiumAccess
}

internal data class PlayPlan(
    val details: ProductDetails,
    val offerToken: String,
    val terms: SubscriptionTerms
)

internal class PlaySubscriptions(context: Context, private val key: PublicKey, onChanged: () -> Unit) {
    private val packageName = context.packageName
    private val client = BillingClient.newBuilder(context.applicationContext)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        // Re-query current ownership after a purchase, external completion or cancellation.
        .setListener { _, _ -> onChanged() }
        .build()

    private suspend fun connect(): Boolean {
        if (client.isReady) return true
        val result = CompletableDeferred<Boolean>()
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(response: BillingResult) {
                result.complete(response.responseCode == BillingClient.BillingResponseCode.OK)
            }

            override fun onBillingServiceDisconnected() {
                result.complete(false)
            }
        })
        return result.await()
    }

    suspend fun access(feature: PremiumFeature): PremiumAccess = withTimeoutOrNull(15_000) {
        if (!connect()) return@withTimeoutOrNull PremiumAccess.Unavailable
        val purchases = CompletableDeferred<List<Purchase>?>()
        val query = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        client.queryPurchasesAsync(query) { result, values ->
            purchases.complete(values.takeIf { result.responseCode == BillingClient.BillingResponseCode.OK })
        }
        val current = purchases.await() ?: return@withTimeoutOrNull PremiumAccess.Unavailable
        val matching = current.filter { it.packageName == packageName && feature.productId in it.products }
        val purchased = matching.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        if (purchased.isNotEmpty()) {
            val verified = purchased.firstOrNull { validPlaySignature(it.originalJson, it.signature, key) }
                ?: return@withTimeoutOrNull PremiumAccess.VerificationRequired
            if (!verified.isAcknowledged) {
                val acknowledged = CompletableDeferred<Boolean>()
                client.acknowledgePurchase(
                    AcknowledgePurchaseParams.newBuilder().setPurchaseToken(verified.purchaseToken).build()
                ) { result ->
                    acknowledged.complete(result.responseCode == BillingClient.BillingResponseCode.OK)
                }
                if (!acknowledged.await()) return@withTimeoutOrNull PremiumAccess.Unavailable
            }
            return@withTimeoutOrNull PremiumAccess.Active
        }
        if (matching.any { it.purchaseState == Purchase.PurchaseState.PENDING }) {
            return@withTimeoutOrNull PremiumAccess.Pending
        }
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(feature.productId).setProductType(BillingClient.ProductType.SUBS).build()
        val details = CompletableDeferred<ProductDetails?>()
        client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()) { result, response ->
            details.complete(
                response.productDetailsList.singleOrNull { it.productId == feature.productId }
                    .takeIf { result.responseCode == BillingClient.BillingResponseCode.OK }
            )
        }
        val item = details.await() ?: return@withTimeoutOrNull PremiumAccess.NoOffer
        val plans = item.subscriptionOfferDetails.orEmpty().mapNotNull { offer ->
            val terms = subscriptionTerms(
                offer.pricingPhases.pricingPhaseList.map {
                    SubscriptionPhase(
                        it.formattedPrice,
                        it.priceAmountMicros,
                        it.billingPeriod,
                        it.recurrenceMode == ProductDetails.RecurrenceMode.INFINITE_RECURRING,
                        it.billingCycleCount
                    )
                }
            )
            terms?.let { PlayPlan(item, offer.offerToken, it) }
        }
        val plan = plans.firstOrNull { it.terms.trial != null } ?: plans.firstOrNull()
        plan?.let(PremiumAccess::Offered) ?: PremiumAccess.NoOffer
    } ?: PremiumAccess.Unavailable

    fun purchase(activity: Activity, plan: PlayPlan): Boolean {
        val product = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(plan.details).setOfferToken(plan.offerToken).build()
        val request = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(product)).build()
        return client.launchBillingFlow(activity, request).responseCode == BillingClient.BillingResponseCode.OK
    }

    fun close() = client.endConnection()
}
