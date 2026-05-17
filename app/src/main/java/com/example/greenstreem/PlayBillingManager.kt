package com.example.greenstreem

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.widget.Toast
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import java.util.concurrent.atomic.AtomicInteger

object PlayBillingManager : PurchasesUpdatedListener {
    private var billingClient: BillingClient? = null
    private var proOneTimeProductDetails: ProductDetails? = null
    private var proSubscriptionProductDetails: ProductDetails? = null
    private var appContext: Context? = null

    fun start(context: Context) {
        if (!BuildConfig.PLAY_STORE_BUILD) return
        appContext = context.applicationContext
        val existing = billingClient
        if (existing?.isReady == true) {
            queryActivePurchases()
            return
        }

        billingClient = BillingClient.newBuilder(context.applicationContext)
            .setListener(this)
            .enableAutoServiceReconnection()
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build()
            )
            .build()

        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode != BillingClient.BillingResponseCode.OK) return
                queryProProducts()
                queryActivePurchases()
            }

            override fun onBillingServiceDisconnected() = Unit
        })
    }

    fun launchProUnlock(activity: Activity) {
        if (!BuildConfig.PLAY_STORE_BUILD) {
            Toast.makeText(activity, "Pro is unlocked in this build", Toast.LENGTH_SHORT).show()
            return
        }

        if (ProEntitlement.isProUnlocked(activity)) {
            Toast.makeText(activity, "Pro is already unlocked", Toast.LENGTH_SHORT).show()
            return
        }

        val client = billingClient
        if (client?.isReady != true) {
            start(activity)
            Toast.makeText(activity, "Connecting to Google Play Billing", Toast.LENGTH_SHORT).show()
            return
        }

        queryActivePurchases { unlocked ->
            activity.runOnUiThread {
                if (unlocked || ProEntitlement.isProUnlocked(activity)) {
                    Toast.makeText(activity, "Pro is already unlocked", Toast.LENGTH_SHORT).show()
                } else {
                    queryProProducts {
                        activity.runOnUiThread {
                            showProOptions(activity)
                        }
                    }
                }
            }
        }
    }

    private fun showProOptions(activity: Activity) {
        val products = buildAvailableProProducts()
        if (products.isEmpty()) {
            Toast.makeText(activity, "Pro unlock is not available yet", Toast.LENGTH_SHORT).show()
            return
        }

        if (products.size == 1 && products.first().details.productType == BillingClient.ProductType.INAPP) {
            launchBillingFlow(activity, products.first().details)
            return
        }

        AlertDialog.Builder(activity)
            .setTitle("Unlock GreenStreem Pro")
            .setItems(products.map { it.label }.toTypedArray()) { _, which ->
                launchBillingFlow(activity, products[which].details)
            }
            .setNegativeButton("Not now", null)
            .show()
    }

    private data class ProProductOption(
        val label: String,
        val details: ProductDetails
    )

    private fun buildAvailableProProducts(): List<ProProductOption> {
        val oneTime = proOneTimeProductDetails?.let { details ->
            val price = details.oneTimePurchaseOfferDetails?.formattedPrice
            ProProductOption(
                "One-time Pro unlock${price?.let { " - $it" }.orEmpty()}",
                details
            )
        }
        val monthly = proSubscriptionProductDetails?.let { details ->
            val price = details.subscriptionOfferDetails
                ?.firstOrNull()
                ?.pricingPhases
                ?.pricingPhaseList
                ?.firstOrNull()
                ?.formattedPrice
            ProProductOption(
                "Monthly Pro subscription${price?.let { " - $it/month" }.orEmpty()}",
                details
            )
        }
        return listOfNotNull(oneTime, monthly)
    }

    private fun launchBillingFlow(activity: Activity, details: ProductDetails) {
        val client = billingClient
        if (client?.isReady != true) {
            start(activity)
            Toast.makeText(activity, "Connecting to Google Play Billing", Toast.LENGTH_SHORT).show()
            return
        }

        val productDetailsParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
        if (details.productType == BillingClient.ProductType.SUBS) {
            val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken
            if (offerToken.isNullOrBlank()) {
                Toast.makeText(activity, "Pro subscription is not available yet", Toast.LENGTH_SHORT).show()
                return
            }
            productDetailsParamsBuilder.setOfferToken(offerToken)
        }

        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    productDetailsParamsBuilder.build()
                )
            )
            .build()
        client.launchBillingFlow(activity, params)
    }

    fun refreshPurchases() {
        if (!BuildConfig.PLAY_STORE_BUILD) return
        if (billingClient?.isReady == true) queryActivePurchases()
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode != BillingClient.BillingResponseCode.OK) return
        processPurchases(purchases.orEmpty())
    }

    private fun queryProProducts(onComplete: (() -> Unit)? = null) {
        val pending = AtomicInteger(2)
        val done = {
            if (pending.decrementAndGet() == 0) onComplete?.invoke()
        }
        queryProOneTimeProduct(done)
        queryProSubscriptionProduct(done)
    }

    private fun queryProOneTimeProduct(onComplete: (() -> Unit)? = null) {
        val client = billingClient ?: run {
            onComplete?.invoke()
            return
        }
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(ProEntitlement.PRODUCT_ID_PRO_UNLOCK)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(product))
            .build()

        client.queryProductDetailsAsync(params) { result, productDetailsResult ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                proOneTimeProductDetails = productDetailsResult.productDetailsList.firstOrNull {
                    it.productId == ProEntitlement.PRODUCT_ID_PRO_UNLOCK
                }
            }
            onComplete?.invoke()
        }
    }

    private fun queryProSubscriptionProduct(onComplete: (() -> Unit)? = null) {
        val client = billingClient ?: run {
            onComplete?.invoke()
            return
        }
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(ProEntitlement.PRODUCT_ID_PRO_MONTHLY)
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(product))
            .build()

        client.queryProductDetailsAsync(params) { result, productDetailsResult ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                proSubscriptionProductDetails = productDetailsResult.productDetailsList.firstOrNull {
                    it.productId == ProEntitlement.PRODUCT_ID_PRO_MONTHLY
                }
            }
            onComplete?.invoke()
        }
    }

    private fun queryActivePurchases(onComplete: ((Boolean) -> Unit)? = null) {
        val client = billingClient ?: run {
            onComplete?.invoke(false)
            return
        }
        val inAppParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        client.queryPurchasesAsync(inAppParams) { inAppResult, inAppPurchases ->
            if (inAppResult.responseCode != BillingClient.BillingResponseCode.OK) {
                onComplete?.invoke(false)
                return@queryPurchasesAsync
            }
            val subParams = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
            client.queryPurchasesAsync(subParams) { result, subPurchases ->
                val activePurchases = if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    inAppPurchases + subPurchases
                } else {
                    inAppPurchases
                }
                onComplete?.invoke(processPurchases(activePurchases))
            }
        }
    }

    private fun processPurchases(purchases: List<Purchase>): Boolean {
        val context = appContext ?: return false
        val proPurchase = purchases.firstOrNull { purchase ->
            purchase.products.any { it in ProEntitlement.PRO_PRODUCT_IDS } &&
                purchase.purchaseState == Purchase.PurchaseState.PURCHASED
        }

        ProEntitlement.setProUnlocked(context, proPurchase != null)
        if (proPurchase != null && !proPurchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(proPurchase.purchaseToken)
                .build()
            billingClient?.acknowledgePurchase(params) { result ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    ProEntitlement.setProUnlocked(context, true)
                }
            }
        }
        return proPurchase != null
    }
}
