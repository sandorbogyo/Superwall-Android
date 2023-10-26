package com.superwall.sdk.paywall.presentation.internal.operators

import com.superwall.sdk.Superwall
import com.superwall.sdk.dependencies.DependencyContainer
import com.superwall.sdk.models.triggers.InternalTriggerResult
import com.superwall.sdk.models.triggers.TriggerResult
import com.superwall.sdk.paywall.presentation.internal.PresentationRequest
import com.superwall.sdk.paywall.presentation.rule_logic.RuleEvaluationOutcome

internal fun Superwall.confirmHoldoutAssignment(
    request: PresentationRequest,
    rulesOutcome: RuleEvaluationOutcome,
    dependencyContainer: DependencyContainer? = null
) {
    val container = dependencyContainer ?: this.dependencyContainer
    if (!request.flags.type.couldPresent) return
    if (rulesOutcome.triggerResult !is InternalTriggerResult.Holdout) return
    rulesOutcome.confirmableAssignment?.let {
        container.configManager.confirmAssignment(it)
    }
}

