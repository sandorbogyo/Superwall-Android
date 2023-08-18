package com.superwall.sdk.paywall.presentation.internal

import android.app.Activity
import com.superwall.sdk.delegate.SubscriptionStatus
import com.superwall.sdk.paywall.presentation.internal.request.PaywallOverrides
import com.superwall.sdk.paywall.presentation.internal.request.PresentationInfo
import com.superwall.sdk.paywall.vc.delegate.PaywallViewControllerDelegate
import kotlinx.coroutines.flow.StateFlow


sealed class PresentationRequestType {

    object Presentation : PresentationRequestType()
    data class GetPaywallViewController(val adapter: PaywallViewControllerDelegate) : PresentationRequestType()
    object GetPresentationResult : PresentationRequestType()
    object GetImplicitPresentationResult : PresentationRequestType()

    val description: String
        get() = when (this) {
            is Presentation -> "presentation"
            is GetPaywallViewController -> "getPaywallViewController"
            is GetPresentationResult -> "getPresentationResult"
            is GetImplicitPresentationResult -> "getImplicitPresentationResult"
            else -> "Unknown"
        }

    val paywallVcDelegateAdapter: PaywallViewControllerDelegate?
        get() = if (this is GetPaywallViewController) this.adapter else null

    val hasObjcDelegate: Boolean
        get() = false

    companion object {
        fun areEqual(lhs: PresentationRequestType, rhs: PresentationRequestType): Boolean {
            return when {
                lhs is GetPaywallViewController && rhs is GetPaywallViewController -> lhs.adapter == rhs.adapter
                else -> lhs == rhs
            }
        }
    }
}


data class PresentationRequest(
    val presentationInfo: PresentationInfo,
    var presenter: Activity? = null,
    var paywallOverrides: PaywallOverrides? = null,
    var flags: Flags,

) {
    data class Flags(
        var isDebuggerLaunched: Boolean,
        var subscriptionStatus: StateFlow<SubscriptionStatus?>,
        var isPaywallPresented: Boolean,
        var type: PresentationRequestType
    )

//    val publisher: StateFlow<PresentationRequest> = flowOf(this)


}