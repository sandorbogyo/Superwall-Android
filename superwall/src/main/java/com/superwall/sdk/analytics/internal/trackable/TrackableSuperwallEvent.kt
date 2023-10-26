package com.superwall.sdk.analytics.internal.trackable

import android.net.Uri
import com.superwall.sdk.analytics.SessionEventsManager
import com.superwall.sdk.analytics.superwall.SuperwallEvent
import com.superwall.sdk.analytics.superwall.TransactionProduct
import com.superwall.sdk.config.models.Survey
import com.superwall.sdk.config.models.SurveyOption
import com.superwall.sdk.delegate.SubscriptionStatus
import com.superwall.sdk.dependencies.ComputedPropertyRequestsFactory
import com.superwall.sdk.dependencies.FeatureFlagsFactory
import com.superwall.sdk.dependencies.RuleAttributesFactory
import com.superwall.sdk.models.config.FeatureFlags
import com.superwall.sdk.models.events.EventData
import com.superwall.sdk.models.triggers.TriggerResult
import com.superwall.sdk.paywall.presentation.PaywallInfo
import com.superwall.sdk.paywall.presentation.internal.PaywallPresentationRequestStatus
import com.superwall.sdk.paywall.presentation.internal.PaywallPresentationRequestStatusReason
import com.superwall.sdk.paywall.presentation.internal.PresentationRequestType
import com.superwall.sdk.paywall.vc.Survey.SurveyPresentationResult
import com.superwall.sdk.store.abstractions.product.StoreProduct
import com.superwall.sdk.store.abstractions.transactions.GoogleBillingPurchaseTransaction
import com.superwall.sdk.store.abstractions.transactions.StoreTransactionType
import com.superwall.sdk.store.transactions.TransactionError
import kotlinx.serialization.json.*
import java.net.URL


interface TrackableSuperwallEvent : Trackable {
    val superwallEvent: SuperwallEvent
}

internal sealed class InternalSuperwallEvent(override val superwallEvent: SuperwallEvent) :
    TrackableSuperwallEvent {

    override val rawName: String
        get() = this.superwallEvent.rawName

    override val canImplicitlyTriggerPaywall: Boolean
        get() = this.superwallEvent.canImplicitlyTriggerPaywall

    internal class AppOpen(override var customParameters: HashMap<String, Any> = HashMap()) :
        InternalSuperwallEvent(SuperwallEvent.AppOpen()) {
        override suspend fun getSuperwallParameters(): HashMap<String, Any> {
            return HashMap()
        }
    }

    internal class AppInstall(
        val appInstalledAtString: String,
        val hasExternalPurchaseController: Boolean,
        override var customParameters: HashMap<String, Any> = HashMap()
    ) : InternalSuperwallEvent(SuperwallEvent.AppInstall()) {
        override suspend fun getSuperwallParameters(): HashMap<String, Any> {
            return hashMapOf(
                "application_installed_at" to appInstalledAtString,
                "using_purchase_controller" to hasExternalPurchaseController
            )
        }
    }

    // TODO: Implement the rest

    internal class SurveyClose(
        override val customParameters: MutableMap<String, Any> = mutableMapOf()
    ) : InternalSuperwallEvent(SuperwallEvent.SurveyClose()) {
        override suspend fun getSuperwallParameters(): Map<String, Any> {
            return emptyMap()
        }
    }

    internal data class SurveyResponse(
        val survey: Survey,
        val selectedOption: SurveyOption,
        val customResponse: String?,
        val paywallInfo: PaywallInfo
    ) : InternalSuperwallEvent(SuperwallEvent.SurveyResponse(
        survey,
        selectedOption,
        customResponse,
        paywallInfo
    )) {
        override val superwallEvent: SuperwallEvent
            get() = SuperwallEvent.SurveyResponse(
                survey, selectedOption, customResponse, paywallInfo
            )

        override val customParameters: Map<String, Any>
            get() {
                val output = paywallInfo.customParams()
                return output + mapOf(
                    "survey_selected_option_title" to selectedOption.title,
                    "survey_custom_response" to customResponse
                ).filter { (_, value) -> value != null } as MutableMap<String, Any>
            }

        override suspend fun getSuperwallParameters(): Map<String, Any> {
            val params: Map<String, Any> = mapOf(
                "survey_id" to survey.id,
                "survey_assignment_key" to survey.assignmentKey,
                "survey_selected_option_id" to selectedOption.id
            )

            return paywallInfo.eventParams(otherParams = params)
        }
    }



    internal class AppLaunch(override var customParameters: HashMap<String, Any> = HashMap()) :
        InternalSuperwallEvent(SuperwallEvent.AppLaunch()) {
        override suspend fun getSuperwallParameters(): HashMap<String, Any> = HashMap()
    }

    internal class Attributes(
        val appInstalledAtString: String,
        override var customParameters: HashMap<String, Any> = HashMap()
    ) : InternalSuperwallEvent(SuperwallEvent.UserAttributes(customParameters)) {
        override suspend fun getSuperwallParameters(): HashMap<String, Any> {
            return hashMapOf(
                "application_installed_at" to appInstalledAtString
            )
        }
    }

    internal class DeepLink(
        val uri: Uri,
        override var customParameters: HashMap<String, Any> = extractQueryParameters(uri)
    ) : InternalSuperwallEvent(SuperwallEvent.DeepLink(uri)) {

        override suspend fun getSuperwallParameters(): HashMap<String, Any> {
            return hashMapOf(
                "url" to uri.toString(),
                "path" to (uri.path ?: ""),
                "pathExtension" to (uri.lastPathSegment?.substringAfterLast('.') ?: ""),
                "lastPathComponent" to (uri.lastPathSegment ?: ""),
                "host" to (uri.host ?: ""),
                "query" to (uri.query ?: ""),
                "fragment" to (uri.fragment ?: "")
            )
        }

        companion object {
            private fun extractQueryParameters(uri: Uri): HashMap<String, Any> {
                val queryStrings = HashMap<String, Any>()
                uri.queryParameterNames.forEach { paramName ->
                    val paramValue = uri.getQueryParameter(paramName) ?: return@forEach
                    when {
                        paramValue.equals("true", ignoreCase = true) -> queryStrings[paramName] = true
                        paramValue.equals("false", ignoreCase = true) -> queryStrings[paramName] = false
                        paramValue.toIntOrNull() != null -> queryStrings[paramName] = paramValue.toInt()
                        paramValue.toDoubleOrNull() != null -> queryStrings[paramName] = paramValue.toDouble()
                        else -> queryStrings[paramName] = paramValue
                    }
                }
                return queryStrings
            }
        }
    }

    internal class FirstSeen(override var customParameters: HashMap<String, Any> = HashMap()) :
        InternalSuperwallEvent(SuperwallEvent.FirstSeen()) {
        override suspend fun getSuperwallParameters(): HashMap<String, Any> = HashMap()
    }

    internal class AppClose(override var customParameters: HashMap<String, Any> = HashMap()) :
        InternalSuperwallEvent(SuperwallEvent.AppClose()) {
        override suspend fun getSuperwallParameters(): HashMap<String, Any> = HashMap()
    }


    internal class PaywallLoad(
        val state: State,
        val eventData: EventData?
    ) : InternalSuperwallEvent(SuperwallEvent.PaywallResponseLoadStart(eventData?.name)) {
        sealed class State() {
            class Start() : State()
            class NotFound() : State()
            class Fail() : State()
            class Complete(val paywallInfo: PaywallInfo) : State()
        }

        override val customParameters: Map<String, Any>
            get() = when (state) {
                is State.Complete -> state.paywallInfo.customParams()
                else -> emptyMap()
            }

        override suspend fun getSuperwallParameters(): HashMap<String, Any> {
            val fromEvent = eventData != null
            val params: HashMap<String, Any> = hashMapOf(
                "is_triggered_from_event" to fromEvent
            )

            when (state) {
                is State.Start -> return params
                is State.NotFound -> return params
                is State.Fail -> return params
                is State.Complete -> return HashMap(
                    state.paywallInfo.eventParams(otherParams = params)
                        .filterValues { it != null } as Map<String, Any>)
            }
        }
    }

    internal class SubscriptionStatusDidChange(
        val subscriptionStatus: SubscriptionStatus,
        override var customParameters: HashMap<String, Any> = HashMap()
    ) : InternalSuperwallEvent(SuperwallEvent.SubscriptionStatusDidChange()) {
        override suspend fun getSuperwallParameters(): HashMap<String, Any> {
            return hashMapOf(
                "subscription_status" to subscriptionStatus.toString()
            )
        }
    }


    internal class SessionStart(override var customParameters: HashMap<String, Any> = HashMap()) :
        InternalSuperwallEvent(SuperwallEvent.SessionStart()) {
        override suspend fun getSuperwallParameters(): HashMap<String, Any> {
            return HashMap()
        }
    }

    internal class TriggerFire(
        val triggerResult: TriggerResult,
        val triggerName: String,
        override var customParameters: HashMap<String, Any> = HashMap(),
        private val sessionEventsManager: SessionEventsManager
    ) : InternalSuperwallEvent(
        SuperwallEvent.TriggerFire(
            eventName = triggerName,
            result = triggerResult
        )
    ) {
        override suspend fun getSuperwallParameters(): HashMap<String, Any> {
            val params: HashMap<String, Any> = hashMapOf(
                "trigger_name" to triggerName
            )

            // TODO: Fix trigger session
//            val triggerSession = sessionEventsManager.triggerSession.activeTriggerSession
//            if (triggerSession != null) {
//                params["trigger_session_id"] = triggerSession.id
//            }

            return when (triggerResult) {
                is TriggerResult.NoRuleMatch -> {
                    params.apply {
                        this["result"] = "no_rule_match"
                    }
                }
                is TriggerResult.Holdout -> {
                    params.apply {
                        this["variant_id"] = triggerResult.experiment.variant.id
                        this["experiment_id"] = triggerResult.experiment.id
                        this["result"] = "holdout"
                    }
                }
                is TriggerResult.Paywall -> {
                    params.apply {
                        this["variant_id"] = triggerResult.experiment.variant.id
                        this["experiment_id"] = triggerResult.experiment.id
                        this["paywall_identifier"] =
                            triggerResult.experiment.variant.paywallId ?: ""
                        this["result"] = "present"
                    }
                }
                is TriggerResult.EventNotFound -> {
                    params.apply {
                        this["result"] = "eventNotFound"
                    }
                }
                is TriggerResult.Error -> {
                    params.apply {
                        this["result"] = "error"
                    }
                }
            }
        }
    }

    internal class PresentationRequest(
        val eventData: EventData?,
        val type: PresentationRequestType,
        val status: PaywallPresentationRequestStatus,
        val statusReason: PaywallPresentationRequestStatusReason?,
        val factory: PresentationRequest.Factory,
        override var customParameters: HashMap<String, Any> = HashMap()
    ) : InternalSuperwallEvent(
        SuperwallEvent.PaywallPresentationRequest(
            status = status,
            reason = statusReason
        )
    ) {
        interface Factory: RuleAttributesFactory, FeatureFlagsFactory,
            ComputedPropertyRequestsFactory {}

        override suspend fun getSuperwallParameters(): HashMap<String, Any> {
            return hashMapOf(
                "source_event_name" to (eventData?.name ?: ""),
                "pipeline_type" to type.description,
                "status" to status.status,
                "status_reason" to (statusReason?.description ?: "")
            )
        }
    }

    internal class PaywallOpen(
        val paywallInfo: PaywallInfo
    ) : InternalSuperwallEvent(SuperwallEvent.PaywallOpen(paywallInfo = paywallInfo)) {
        override val customParameters: Map<String, Any>
            get() {
                return paywallInfo.customParams()
            }
        override suspend fun getSuperwallParameters(): HashMap<String, Any> {
            return HashMap(paywallInfo.eventParams())
        }
    }

    internal class PaywallClose(
        val paywallInfo: PaywallInfo,
        val surveyPresentationResult: SurveyPresentationResult
    ) : InternalSuperwallEvent(SuperwallEvent.PaywallClose(paywallInfo)) {
        override val customParameters: Map<String, Any>
            get() {
                return paywallInfo.customParams()
            }

        override suspend fun getSuperwallParameters(): HashMap<String, Any> {
            var params: MutableMap<String, Any> = mutableMapOf(
                "survey_attached" to paywallInfo.surveys.isNotEmpty()
            )

            if (surveyPresentationResult != SurveyPresentationResult.NOSHOW) {
                params["survey_presentation"] = surveyPresentationResult.rawValue
            }

            val eventParams = paywallInfo.eventParams()
            params.putAll(eventParams)
            return HashMap(params)
        }
    }

    internal class PaywallDecline(
        val paywallInfo: PaywallInfo
    ) : InternalSuperwallEvent(SuperwallEvent.PaywallDecline(paywallInfo = paywallInfo)) {
        override suspend fun getSuperwallParameters(): HashMap<String, Any> {
            return HashMap(paywallInfo.eventParams())
        }

        override val customParameters: Map<String, Any>
            get() {
                return paywallInfo.customParams()
            }
    }

    internal class Transaction(
        val state: State,
        val paywallInfo: PaywallInfo,
        val product: StoreProduct?,
        val model: StoreTransactionType?
    ) : TrackableSuperwallEvent {
        sealed class State {
            class Start(val product: StoreProduct) : State()
            class Fail(val error: TransactionError) : State()
            class Abandon(val product: StoreProduct) : State()
            class Complete(val product: StoreProduct, val transaction: StoreTransactionType?) :
                State()

            class Restore : State()
            class Timeout : State()
        }

        override val customParameters: Map<String, Any>
            get() {
                return paywallInfo.customParams()
            }

        override val superwallEvent: SuperwallEvent
            get() = when (state) {
                is State.Start -> SuperwallEvent.TransactionStart(
                    product = state.product,
                    paywallInfo = paywallInfo
                )
                is State.Fail -> SuperwallEvent.TransactionFail(
                    error = state.error,
                    paywallInfo = paywallInfo
                )
                is State.Abandon -> SuperwallEvent.TransactionAbandon(
                    product = state.product,
                    paywallInfo = paywallInfo
                )
                is State.Complete -> SuperwallEvent.TransactionComplete(
                    transaction = state.transaction,
                    product = state.product,
                    paywallInfo = paywallInfo
                )
                is State.Restore -> SuperwallEvent.TransactionRestore(paywallInfo = paywallInfo)
                is State.Timeout -> SuperwallEvent.TransactionTimeout(paywallInfo = paywallInfo)
            }
        override val rawName: String
            get() = superwallEvent.rawName

        override val canImplicitlyTriggerPaywall: Boolean
            get() = superwallEvent.canImplicitlyTriggerPaywall

        override suspend fun getSuperwallParameters(): HashMap<String, Any> {
            return when (state) {
                is State.Start,
                is State.Abandon,
                is State.Complete,
                is State.Restore,
                is State.Timeout -> {
                    var eventParams = paywallInfo.eventParams(product).toMutableMap()
                    if (model != null) {
                        val json = Json { encodeDefaults = true }
                        // TODO: Figure out how to get this to work with kotlinx.serialization
                        val jsonObject: JsonObject =
                            json.encodeToJsonElement(model as GoogleBillingPurchaseTransaction).jsonObject

                        val modelMap: Map<String, Any> = jsonObject.mapValues { entry ->
                            when (val value = entry.value) {
                                is JsonPrimitive -> value.content // Handle other primitive types as needed
                                else -> value // Handle complex objects if necessary
                            }
                        }
                        eventParams.putAll(modelMap)
                    }
                    HashMap(eventParams)
                }
                is State.Fail -> {
                    when (state.error) {
                        is TransactionError.Failure,
                        is TransactionError.Pending -> {
                            val message = state.error.message
                            HashMap(
                                paywallInfo.eventParams(
                                    product,
                                    otherParams = mapOf("message" to message)
                                )
                            )
                        }
                    }
                }
            }
        }
    }


    internal class SubscriptionStart(
        val paywallInfo: PaywallInfo,
        val product: StoreProduct
    ) : InternalSuperwallEvent(
        SuperwallEvent.SubscriptionStart(
            product = product,
            paywallInfo = paywallInfo
        )
    ) {
        override val customParameters: Map<String, Any>
            get() {
                return paywallInfo.customParams()
            }

        override suspend fun getSuperwallParameters(): HashMap<String, Any> {
            return HashMap(paywallInfo.eventParams(product))
        }
    }

    internal class FreeTrialStart(
        val paywallInfo: PaywallInfo,
        val product: StoreProduct
    ) : InternalSuperwallEvent(
        SuperwallEvent.FreeTrialStart(
            product = product,
            paywallInfo = paywallInfo
        )
    ) {
        override val customParameters: Map<String, Any>
            get() {
                return paywallInfo.customParams()
            }

        override suspend fun getSuperwallParameters(): HashMap<String, Any> {
            return HashMap(paywallInfo.eventParams(product))
        }
    }

    internal class NonRecurringProductPurchase(
        val paywallInfo: PaywallInfo,
        val product: StoreProduct
    ) : InternalSuperwallEvent(
        SuperwallEvent.NonRecurringProductPurchase(
            product = TransactionProduct(
                product = product
            ), paywallInfo = paywallInfo
        )
    ) {
        override val customParameters: Map<String, Any>
            get() {
                return paywallInfo.customParams()
            }

        override suspend fun getSuperwallParameters(): HashMap<String, Any> {
            return HashMap(paywallInfo.eventParams(product))
        }
    }


    internal class PaywallWebviewLoad(
        val state: State,
        val paywallInfo: PaywallInfo
    ) : InternalSuperwallEvent(SuperwallEvent.PaywallWebviewLoadStart(paywallInfo)) {

        sealed class State {
            class Start : State()
            class Fail : State()
            class Timeout : State()
            class Complete : State()
        }

        override val superwallEvent: SuperwallEvent
            get() = when (state) {
                is PaywallWebviewLoad.State.Start -> SuperwallEvent.PaywallWebviewLoadStart(
                    paywallInfo
                )
                is PaywallWebviewLoad.State.Timeout -> SuperwallEvent.PaywallWebviewLoadTimeout(
                    paywallInfo
                )
                is PaywallWebviewLoad.State.Fail -> SuperwallEvent.PaywallWebviewLoadFail(
                    paywallInfo
                )
                is PaywallWebviewLoad.State.Complete -> SuperwallEvent.PaywallWebviewLoadComplete(
                    paywallInfo
                )
            }

        override val customParameters: Map<String, Any>
            get() {
                return paywallInfo.customParams()
            }

        override suspend fun getSuperwallParameters(): HashMap<String, Any> {
            return HashMap(paywallInfo.eventParams())
        }
    }

    internal class PaywallProductsLoad(
        val state: State,
        val paywallInfo: PaywallInfo,
        val eventData: EventData?
    ) : InternalSuperwallEvent(
        SuperwallEvent.PaywallProductsLoadStart(
            eventData?.name,
            paywallInfo
        )
    ) {
        sealed class State {
            class Start : State()
            class Fail : State()
            class Complete : State()
        }

        override val superwallEvent: SuperwallEvent
            get() = when (state) {
                is State.Start -> SuperwallEvent.PaywallProductsLoadStart(
                    eventData?.name,
                    paywallInfo
                )
                is State.Fail -> SuperwallEvent.PaywallProductsLoadFail(
                    eventData?.name,
                    paywallInfo
                )
                is State.Complete -> SuperwallEvent.PaywallProductsLoadComplete(
                    eventData?.name,
                    paywallInfo
                )
            }

        override val customParameters: Map<String, Any>
            get() {
                return paywallInfo.customParams()
            }

        override suspend fun getSuperwallParameters(): HashMap<String, Any> {
            val fromEvent = eventData != null
            var params: HashMap<String, Any> = hashMapOf(
                "is_triggered_from_event" to fromEvent
            )
            params.putAll(paywallInfo.eventParams())
            return params
        }
    }


}