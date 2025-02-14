package com.superwall.sdk.dependencies

import ComputedPropertyRequest
import android.app.Activity
import android.app.Application
import android.content.Context
import androidx.lifecycle.ProcessLifecycleOwner
import com.android.billingclient.api.Purchase
import com.superwall.sdk.Superwall
import com.superwall.sdk.analytics.SessionEventsManager
import com.superwall.sdk.analytics.internal.trackable.InternalSuperwallEvent
import com.superwall.sdk.analytics.session.AppManagerDelegate
import com.superwall.sdk.analytics.session.AppSession
import com.superwall.sdk.analytics.session.AppSessionManager
import com.superwall.sdk.analytics.trigger_session.TriggerSessionManager
import com.superwall.sdk.billing.GoogleBillingWrapper
import com.superwall.sdk.config.ConfigLogic
import com.superwall.sdk.config.ConfigManager
import com.superwall.sdk.config.options.SuperwallOptions
import com.superwall.sdk.debug.DebugManager
import com.superwall.sdk.debug.DebugViewController
import com.superwall.sdk.delegate.SubscriptionStatus
import com.superwall.sdk.delegate.SuperwallDelegateAdapter
import com.superwall.sdk.delegate.subscription_controller.PurchaseController
import com.superwall.sdk.identity.IdentityInfo
import com.superwall.sdk.identity.IdentityManager
import com.superwall.sdk.misc.CurrentActivityTracker
import com.superwall.sdk.misc.ActivityProvider
import com.superwall.sdk.misc.AppLifecycleObserver
import com.superwall.sdk.models.config.FeatureFlags
import com.superwall.sdk.models.events.EventData
import com.superwall.sdk.models.paywall.Paywall
import com.superwall.sdk.models.product.ProductVariable
import com.superwall.sdk.network.Api
import com.superwall.sdk.network.Network
import com.superwall.sdk.network.device.DeviceHelper
import com.superwall.sdk.network.device.DeviceInfo
import com.superwall.sdk.paywall.manager.PaywallManager
import com.superwall.sdk.paywall.manager.PaywallViewControllerCache
import com.superwall.sdk.paywall.presentation.internal.PresentationRequest
import com.superwall.sdk.paywall.presentation.internal.PresentationRequestType
import com.superwall.sdk.paywall.presentation.internal.request.PaywallOverrides
import com.superwall.sdk.paywall.presentation.internal.request.PresentationInfo
import com.superwall.sdk.paywall.request.PaywallRequest
import com.superwall.sdk.paywall.request.PaywallRequestManager
import com.superwall.sdk.paywall.request.PaywallRequestManagerDepFactory
import com.superwall.sdk.paywall.request.ResponseIdentifiers
import com.superwall.sdk.paywall.vc.PaywallViewController
import com.superwall.sdk.paywall.vc.delegate.PaywallViewControllerDelegateAdapter
import com.superwall.sdk.paywall.vc.web_view.SWWebView
import com.superwall.sdk.paywall.vc.web_view.messaging.PaywallMessageHandler
import com.superwall.sdk.paywall.vc.web_view.templating.models.JsonVariables
import com.superwall.sdk.paywall.vc.web_view.templating.models.Variables
import com.superwall.sdk.storage.EventsQueue
import com.superwall.sdk.storage.Storage
import com.superwall.sdk.store.InternalPurchaseController
import com.superwall.sdk.store.StoreKitManager
import com.superwall.sdk.store.abstractions.transactions.GoogleBillingPurchaseTransaction
import com.superwall.sdk.store.abstractions.transactions.StoreTransaction
import com.superwall.sdk.store.products.GooglePlayProductsFetcher
import com.superwall.sdk.store.transactions.TransactionManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DependencyContainer(
    val context: Context,
    purchaseController: PurchaseController? = null,
    options: SuperwallOptions?,
    var activityProvider: ActivityProvider?
) : ApiFactory, DeviceInfoFactory, AppManagerDelegate, RequestFactory, TriggerSessionManagerFactory,
    RuleAttributesFactory, DeviceHelper.Factory, CacheFactory,
    PaywallRequestManagerDepFactory, VariablesFactory,
    StoreTransactionFactory, Storage.Factory, InternalSuperwallEvent.PresentationRequest.Factory,
    ViewControllerFactory, PaywallManager.Factory, OptionsFactory, TriggerFactory,
    TransactionVerifierFactory, TransactionManager.Factory, PaywallViewController.Factory,
    ConfigManager.Factory, AppSessionManager.Factory, DebugViewController.Factory {

    var network: Network
    override lateinit var api: Api
    override lateinit var deviceHelper: DeviceHelper
    override lateinit var storage: Storage
    override lateinit var configManager: ConfigManager
    override lateinit var identityManager: IdentityManager
    override var appLifecycleObserver: AppLifecycleObserver = AppLifecycleObserver()
    var appSessionManager: AppSessionManager
    var sessionEventsManager: SessionEventsManager
    var delegateAdapter: SuperwallDelegateAdapter
    var queue: EventsQueue
    var debugManager: DebugManager
    var paywallManager: PaywallManager
    var paywallRequestManager: PaywallRequestManager
    var storeKitManager: StoreKitManager
    val transactionManager: TransactionManager
    val googleBillingWrapper: GoogleBillingWrapper
    val productsFetcher: GooglePlayProductsFetcher

    init {
        // TODO: Add delegate adapter

        // For tracking when the app enters the background.
        CoroutineScope(Dispatchers.Main).launch {
            ProcessLifecycleOwner.get().lifecycle.addObserver(appLifecycleObserver)
        }

        // If activity provider exists, let it be. Otherwise, create our own.
        val activityProvider: ActivityProvider

        if (this.activityProvider == null) {
            val currentActivityTracker = CurrentActivityTracker()

            (context.applicationContext as Application).registerActivityLifecycleCallbacks(
                currentActivityTracker
            )
            activityProvider = currentActivityTracker
            this.activityProvider = activityProvider
        } else {
           activityProvider = this.activityProvider!!
        }

        googleBillingWrapper = GoogleBillingWrapper(context)
        productsFetcher = GooglePlayProductsFetcher(context, googleBillingWrapper)

        var purchaseController = InternalPurchaseController(
            kotlinPurchaseController = purchaseController,
            javaPurchaseController = null,
            context
        )
        storeKitManager = StoreKitManager(context, purchaseController, productsFetcher)

        delegateAdapter = SuperwallDelegateAdapter()
        storage = Storage(context = context, factory = this)
        network = Network(factory = this)

        paywallRequestManager = PaywallRequestManager(
            storeKitManager = storeKitManager,
            network = network,
            factory = this
        )
        paywallManager = PaywallManager(
            paywallRequestManager = paywallRequestManager,
            factory = this,
        )

        configManager = ConfigManager(
            context = context,
            storage = storage,
            network = network,
            options = options,
            factory = this,
            paywallManager = paywallManager
        )

        api = Api(networkEnvironment = configManager.options.networkEnvironment)

        deviceHelper = DeviceHelper(context = context, storage = storage, factory = this)

        queue = EventsQueue(context, configManager = configManager, network = network)

        identityManager = IdentityManager(
            storage = storage,
            deviceHelper = deviceHelper,
            configManager = configManager
        )

        sessionEventsManager = SessionEventsManager(
            network = network,
            storage = storage,
            configManager = configManager,
            factory = this
        )

        // Must be after session events
        appSessionManager = AppSessionManager(
            context = context,
            storage = storage,
            configManager = configManager,
            delegate = this
        )

        debugManager = DebugManager(
            context = context,
            storage = storage,
            factory = this
        )

        CoroutineScope(Dispatchers.Main).launch {
            ProcessLifecycleOwner.get().lifecycle.addObserver(appSessionManager)
        }

        transactionManager = TransactionManager(
            storeKitManager = storeKitManager,
            purchaseController = purchaseController,
            sessionEventsManager,
            activityProvider,
            factory = this,
            context = context
        )

        // Calling this just to initialise the trigger session manager so it can start listening
        // to config.
        sessionEventsManager.triggerSession
    }

    override suspend fun makeHeaders(
        isForDebugging: Boolean,
        requestId: String
    ): Map<String, String> {
        // TODO: Add storage
        val key = if (isForDebugging) storage.debugKey else storage.apiKey
        val auth = "Bearer $key"
        val headers = mapOf(
            "Authorization" to auth,
            "X-Platform" to "iOS",
            "X-Platform-Environment" to "SDK",
            "X-Platform-Wrapper" to deviceHelper.platformWrapper,
            "X-App-User-ID" to (identityManager.appUserId ?: ""),
            "X-Alias-ID" to identityManager.aliasId,
            "X-URL-Scheme" to deviceHelper.urlScheme,
            "X-Vendor-ID" to deviceHelper.vendorId,
            "X-App-Version" to deviceHelper.appVersion,
            "X-OS-Version" to deviceHelper.osVersion,
            "X-Device-Model" to deviceHelper.model,
            "X-Device-Locale" to deviceHelper.locale,
            "X-Device-Language-Code" to deviceHelper.languageCode,
            "X-Device-Currency-Code" to deviceHelper.currencyCode,
            "X-Device-Currency-Symbol" to deviceHelper.currencySymbol,
            "X-Device-Timezone-Offset" to deviceHelper.secondsFromGMT,
            "X-App-Install-Date" to deviceHelper.appInstalledAtString,
            "X-Radio-Type" to deviceHelper.radioType,
            "X-Device-Interface-Style" to deviceHelper.interfaceStyle,
            "X-SDK-Version" to deviceHelper.sdkVersion,
            "X-Git-Sha" to if(deviceHelper.gitSha != null) deviceHelper.gitSha!! else "",
            "X-Build-Time" to  if(deviceHelper.buildTime != null) deviceHelper.buildTime!! else "",
            "X-Bundle-ID" to deviceHelper.bundleId,
            "X-Low-Power-Mode" to deviceHelper.isLowPowerModeEnabled.toString(),
            "X-Is-Sandbox" to deviceHelper.isSandbox.toString(),
            "Content-Type" to "application/json"
        )

        return headers
    }

    override suspend fun makePaywallViewController(
        paywall: Paywall,
        cache: PaywallViewControllerCache?,
        delegate: PaywallViewControllerDelegateAdapter?
    ): PaywallViewController {
        return withContext(Dispatchers.Main) {
            // TODO: Fix this up

            val messageHandler = PaywallMessageHandler(
                sessionEventsManager = sessionEventsManager,
                factory = this@DependencyContainer
            )

            val webViewDeffered = CompletableDeferred<SWWebView>()

            val _webView = SWWebView(
                context = context,
                messageHandler = messageHandler,
                sessionEventsManager = sessionEventsManager,
            )
            webViewDeffered.complete(_webView)

            val webView = webViewDeffered.await()

            val paywallViewController = PaywallViewController(
                context = context,
                paywall = paywall,
                factory = this@DependencyContainer,
                cache = cache,
                delegate = delegate,
                deviceHelper = deviceHelper,
                paywallManager = paywallManager,
                storage = storage,
                webView = webView,
                eventDelegate = Superwall.instance
            )
            webView.delegate = paywallViewController
            messageHandler.delegate = paywallViewController

            return@withContext paywallViewController
        }

    }

    override fun makeDebugViewController(id: String?): DebugViewController {
        val viewController = DebugViewController(
            context = context,
            storeKitManager = storeKitManager,
            network = network,
            paywallRequestManager = paywallRequestManager,
            paywallManager = paywallManager,
            debugManager = debugManager,
            factory = this
        )
        viewController.paywallDatabaseId = id
        // Note: Modal presentation style is an iOS concept. In Android, you might handle this differently.
        return viewController
    }

    override fun makeCache(): PaywallViewControllerCache {
        return PaywallViewControllerCache(deviceHelper.locale)
    }

    override fun makeDeviceInfo(): DeviceInfo {
        return DeviceInfo(
            appInstalledAtString = deviceHelper.appInstalledAtString,
            locale = deviceHelper.locale,
        )
    }

    override fun makeIsSandbox(): Boolean {
        return deviceHelper.isSandbox
    }

    override suspend fun makeSessionDeviceAttributes(): HashMap<String, Any> {
        val attributes = deviceHelper.getTemplateDevice().toMutableMap()

        attributes.remove("utcDate")
        attributes.remove("localDate")
        attributes.remove("localTime")
        attributes.remove("utcTime")
        attributes.remove("utcDateTime")
        attributes.remove("localDateTime")

        return HashMap(attributes)
    }

    override fun makeHasExternalPurchaseController(): Boolean {
        return storeKitManager.purchaseController.hasExternalPurchaseController
    }

    override suspend fun didUpdateAppSession(appSession: AppSession) {

    }


    // Mark - RequestFactory

    // Extension of DependencyContainer implementing RequestFactory
    override fun makePaywallRequest(
        eventData: EventData?,
        responseIdentifiers: ResponseIdentifiers,
        overrides: PaywallRequest.Overrides?,
        isDebuggerLaunched: Boolean,
        presentationSourceType: String?,
        retryCount: Int
    ): PaywallRequest {
        return PaywallRequest(
            eventData = eventData,
            responseIdentifiers = responseIdentifiers,
            overrides = overrides ?: PaywallRequest.Overrides(products = null, isFreeTrial = null),
            isDebuggerLaunched = isDebuggerLaunched,
            presentationSourceType = presentationSourceType,
            retryCount = retryCount
        )
    }

    override fun makePresentationRequest(
        presentationInfo: PresentationInfo,
        paywallOverrides: PaywallOverrides?,
        presenter: Activity?,
        isDebuggerLaunched: Boolean?,
        subscriptionStatus: StateFlow<SubscriptionStatus?>?,
        isPaywallPresented: Boolean,
        type: PresentationRequestType
    ): PresentationRequest {
        return PresentationRequest(
            presentationInfo = presentationInfo,
            presenter = presenter,
            paywallOverrides = paywallOverrides,
            flags = PresentationRequest.Flags(
                isDebuggerLaunched = isDebuggerLaunched ?: debugManager.isDebuggerLaunched,
                // TODO: (PresentationCritical) Fix subscription status
                subscriptionStatus = subscriptionStatus ?: Superwall.instance.subscriptionStatus,
//                subscriptionStatus = subscriptionStatus!!,
                isPaywallPresented = isPaywallPresented,
                type = type
            )
        )
    }

    override fun makeTriggerSessionManager(): TriggerSessionManager {
        return TriggerSessionManager(
            delegate = sessionEventsManager,
            sessionEventsManager = sessionEventsManager,
            storage = storage,
            configManager = configManager,
            identityManager = identityManager
        )
    }

    override fun getTriggerSessionManager(): TriggerSessionManager {
        return sessionEventsManager.triggerSession
    }

    override fun makeStaticPaywall(
        paywallId: String?,
        isDebuggerLaunched: Boolean
    ): Paywall? {
        if (isDebuggerLaunched) {
            return null
        }
        val deviceInfo = makeDeviceInfo()
        return ConfigLogic.getStaticPaywall(
            withId = paywallId,
            config = configManager.config,
            deviceLocale = deviceInfo.locale
        )
    }

    override suspend fun makeRuleAttributes(
        event: EventData?,
        computedPropertyRequests: List<ComputedPropertyRequest>
    ): Map<String, Any> {
        val userAttributes = identityManager.userAttributes.toMutableMap()
        userAttributes.put("isLoggedIn", identityManager.isLoggedIn)

        val deviceAttributes = deviceHelper.getDeviceAttributes(
            sinceEvent = event,
            computedPropertyRequests = computedPropertyRequests
        )

        return mapOf(
            "user" to userAttributes,
            "device" to deviceAttributes,
            "params" to (event?.parameters ?: "")
        )
    }

    override fun makeFeatureFlags(): FeatureFlags? {
        return configManager.config?.featureFlags
    }

    override fun makeComputedPropertyRequests(): List<ComputedPropertyRequest> {
        return configManager.config?.allComputedProperties ?: emptyList()
    }

    override suspend fun makeIdentityInfo(): IdentityInfo {
        return IdentityInfo(
            aliasId = identityManager.aliasId,
            appUserId = identityManager.appUserId,
        )
    }

    override fun makeLocaleIdentifier(): String? {
        return configManager.options?.localeIdentifier
    }

    override suspend fun makeJsonVariables(
        productVariables: List<ProductVariable>?,
        computedPropertyRequests: List<ComputedPropertyRequest>,
        event: EventData?
    ): JsonVariables {
        val templateDeviceDictionary = deviceHelper.getDeviceAttributes(
            sinceEvent = event,
            computedPropertyRequests = computedPropertyRequests
        )

        val variables = Variables(
            productVariables = productVariables ?: listOf<ProductVariable>(),
            params = event?.parameters ?: emptyMap(),
            userAttributes = identityManager.userAttributes,
            templateDeviceDictionary = templateDeviceDictionary
        ).templated()

        return variables
    }

    override suspend fun makeStoreTransaction(transaction: Purchase): StoreTransaction {
        val triggerSessionId =  sessionEventsManager.triggerSession.getActiveTriggerSession()?.sessionId
        return StoreTransaction(
            GoogleBillingPurchaseTransaction(
                transaction = transaction,
            ),
            configRequestId = configManager.config?.requestId ?: "",
            appSessionId = appSessionManager.appSession.id,
            triggerSessionId = triggerSessionId
        )
    }

    override  fun makeTransactionVerifier(): GoogleBillingWrapper {
        return googleBillingWrapper
    }

    override suspend fun makeSuperwallOptions(): SuperwallOptions {
        return configManager.options
    }

    override suspend fun makeTriggers(): Set<String> {
        return configManager.triggersByEventName.keys
    }
}