package com.superwall.sdk.paywall.presentation.internal.operators

import android.app.Activity
import com.superwall.sdk.Superwall
import com.superwall.sdk.analytics.internal.track
import com.superwall.sdk.analytics.internal.trackable.InternalSuperwallEvent
import com.superwall.sdk.models.triggers.TriggerRuleOccurrence
import com.superwall.sdk.paywall.presentation.internal.InternalPresentationLogic
import com.superwall.sdk.paywall.presentation.internal.PaywallPresentationRequestStatus
import com.superwall.sdk.paywall.presentation.internal.PaywallPresentationRequestStatusReason
import com.superwall.sdk.paywall.presentation.internal.PresentationRequest
import com.superwall.sdk.paywall.presentation.internal.state.PaywallState
import com.superwall.sdk.paywall.vc.PaywallViewController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


/**
 * Presents the paywall view controller, stores the presentation request for future use,
 * and sends back a `presented` state to the paywall state publisher.
 *
 * @param paywallViewController The paywall view controller to present.
 * @param presenter The view controller to present the paywall on.
 * @param unsavedOccurrence The trigger rule occurrence to save, if available.
 * @param debugInfo Information to help with debugging.
 * @param request The request to present the paywall.
 * @param paywallStatePublisher A `MutableStateFlow` that gets sent `PaywallState` objects.
 *
 * @return A publisher that contains info for the next pipeline operator.
 */
suspend fun Superwall.presentPaywallViewController(
    paywallViewController: PaywallViewController,
    presenter: Activity,
    unsavedOccurrence: TriggerRuleOccurrence?,
    debugInfo: Map<String, Any>,
    request: PresentationRequest,
    paywallStatePublisher: MutableSharedFlow<PaywallState>
) = withContext(Dispatchers.Main) {
    val trackedEvent = InternalSuperwallEvent.PresentationRequest(
        eventData = request.presentationInfo.eventData,
        type = request.flags.type,
        status = PaywallPresentationRequestStatus.Presentation,
        statusReason = null,
        factory = this@presentPaywallViewController.dependencyContainer
    )
    track(trackedEvent)

    paywallViewController.present(
        presenter = presenter,
        request = request,
        unsavedOccurrence = unsavedOccurrence,
        presentationStyleOverride = request.paywallOverrides?.presentationStyle,
        paywallStatePublisher = paywallStatePublisher
    ) { isPresented ->
        if (isPresented) {
            val state = PaywallState.Presented(paywallViewController.info)
            CoroutineScope(Dispatchers.IO).launch {
                paywallStatePublisher.emit(state)
            }
        } else {
            Logger.debug(
                logLevel = LogLevel.info,
                scope = LogScope.paywallPresentation,
                message = "Paywall Already Presented",
                info = debugInfo
            )
            val error = InternalPresentationLogic.presentationError(
                domain = "SWKPresentationError",
                code = 102,
                title = "Paywall Already Presented",
                value = "Trying to present paywall while another paywall is presented."
            )
            CoroutineScope(Dispatchers.IO).launch {
                paywallStatePublisher.emit(PaywallState.PresentationError(error))
            }
            throw PaywallPresentationRequestStatusReason.PaywallAlreadyPresented()
        }
    }
}