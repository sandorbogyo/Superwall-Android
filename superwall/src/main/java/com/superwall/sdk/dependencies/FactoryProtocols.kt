package com.superwall.sdk.dependencies

import ComputedPropertyRequest
import android.app.Activity
import com.android.billingclient.api.Purchase
import com.superwall.sdk.analytics.trigger_session.TriggerSessionManager
import com.superwall.sdk.config.ConfigManager
import com.superwall.sdk.config.options.SuperwallOptions
import com.superwall.sdk.delegate.SubscriptionStatus
import com.superwall.sdk.identity.IdentityInfo
import com.superwall.sdk.identity.IdentityManager
import com.superwall.sdk.models.config.FeatureFlags
import com.superwall.sdk.models.events.EventData
import com.superwall.sdk.models.paywall.Paywall
import com.superwall.sdk.models.product.ProductVariable
import com.superwall.sdk.network.Api
import com.superwall.sdk.network.device.DeviceInfo
import com.superwall.sdk.paywall.manager.PaywallViewControllerCache
import com.superwall.sdk.paywall.presentation.internal.PresentationRequest
import com.superwall.sdk.paywall.presentation.internal.PresentationRequestType
import com.superwall.sdk.paywall.presentation.internal.request.PaywallOverrides
import com.superwall.sdk.paywall.presentation.internal.request.PresentationInfo
import com.superwall.sdk.paywall.request.PaywallRequest
import com.superwall.sdk.paywall.request.ResponseIdentifiers
import com.superwall.sdk.paywall.vc.PaywallViewController
import com.superwall.sdk.paywall.vc.delegate.PaywallViewControllerDelegateAdapter
import com.superwall.sdk.paywall.vc.web_view.templating.models.JsonVariables
import com.superwall.sdk.storage.Storage
import com.superwall.sdk.store.abstractions.transactions.StoreTransactionType
import com.superwall.sdk.store.transactions.GoogleBillingTransactionVerifier
import kotlinx.coroutines.flow.StateFlow


internal interface ApiFactory {
    // TODO: Think of an alternative way such that we don't need to do this:
    // swiftlint:disable implicitly_unwrapped_optional
    var api: Api
    var storage: Storage

    //    var storage: Storage! { get }
//    var deviceHelper: DeviceHelper! { get }
    var configManager: ConfigManager
    var identityManager: IdentityManager
    // swiftlint:enable implicitly_unwrapped_optional

    suspend fun makeHeaders(
        isForDebugging: Boolean,
        requestId: String
    ): Map<String, String>
}


internal interface DeviceInfoFactory {
    fun makeDeviceInfo(): DeviceInfo
}

internal interface FeatureFlagsFactory {
    fun makeFeatureFlags(): FeatureFlags?
}

internal interface ComputedPropertyRequestsFactory {
    fun makeComputedPropertyRequests(): List<ComputedPropertyRequest>
}

internal interface TriggerSessionManagerFactory {
    fun makeTriggerSessionManager(): TriggerSessionManager
    fun getTriggerSessionManager(): TriggerSessionManager
}


// RequestFactory interface
internal interface RequestFactory {
    fun makePaywallRequest(
        eventData: EventData?,
        responseIdentifiers: ResponseIdentifiers,
        overrides: PaywallRequest.Overrides?,
        isDebuggerLaunched: Boolean,
        presentationSourceType: String?,
        retryCount: Int
    ): PaywallRequest

    fun makePresentationRequest(
        presentationInfo: PresentationInfo,
        paywallOverrides: PaywallOverrides? = null,
        presenter: Activity? = null,
        isDebuggerLaunched: Boolean? = null,
        subscriptionStatus: StateFlow<SubscriptionStatus?>? = null,
        isPaywallPresented: Boolean,
        type: PresentationRequestType
    ): PresentationRequest
}


internal interface RuleAttributesFactory {
    suspend fun makeRuleAttributes(
        event: EventData?,
        computedPropertyRequests: List<ComputedPropertyRequest>
    ): Map<String, Any>
}

internal interface IdentityInfoFactory {
    suspend fun makeIdentityInfo(): IdentityInfo
}

internal interface LocaleIdentifierFactory {
    fun makeLocaleIdentifier(): String?
}


internal interface TransactionVerifierFactory {
    fun makeTransactionVerifier(): GoogleBillingTransactionVerifier
}

internal interface DeviceHelperFactory {
    fun makeDeviceInfo(): DeviceInfo
    fun makeIsSandbox(): Boolean
}

internal interface HasExternalPurchaseControllerFactory {
    fun makeHasExternalPurchaseController(): Boolean
}

internal interface ViewControllerFactory {
    // NOTE: THIS MUST BE EXECUTED ON THE MAIN THREAD (no way to enforce in Kotlin)
    suspend fun makePaywallViewController(
        paywall: Paywall,
        cache: PaywallViewControllerCache?,
        delegate: PaywallViewControllerDelegateAdapter?
    ): PaywallViewController

    // TODO: (Debug)
//    fun makeDebugViewController(id: String?): DebugViewController
}


//ViewControllerFactory & CacheFactory & DeviceInfoFactory,
//interface ViewControllerCacheDevice {
//    suspend fun makePaywallViewController(
//        paywall: Paywall,
//        cache: PaywallViewControllerCache?,
//        delegate: PaywallViewControllerDelegate?
//    ): PaywallViewController
//
//    // TODO: (Debug)
////    fun makeDebugViewController(id: String?): DebugViewController
//
//    // Mark - device
//    fun makeDeviceInfo(): DeviceInfo
//
//    // Mark - cache
//    fun makeCache(): PaywallViewControllerCache
//}


internal interface CacheFactory {
    fun makeCache(): PaywallViewControllerCache
}

internal interface VariablesFactory {
    suspend fun makeJsonVariables(
        productVariables: List<ProductVariable>?,
        computedPropertyRequests: List<ComputedPropertyRequest>,
        event: EventData?
    ): JsonVariables
}

internal interface ConfigManagerFactory {
    fun makeStaticPaywall(paywallId: String?): Paywall?
}

//interface ProductPurchaserFactory {
//    fun makeSK1ProductPurchaser(): ProductPurchaserSK1
//}

internal interface StoreTransactionFactory {
    suspend fun makeStoreTransaction(transaction: Purchase): StoreTransactionType
}

internal interface OptionsFactory {
    suspend fun makeSuperwallOptions(): SuperwallOptions
}

internal interface  TriggerFactory {
    suspend fun makeTriggers(): Set<String>
}