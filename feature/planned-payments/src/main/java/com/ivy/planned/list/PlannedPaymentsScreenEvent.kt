package com.ivy.planned.list

import com.ivy.planned.installments.InstallmentPlanInput

sealed interface PlannedPaymentsScreenEvent {
    data class OnOneTimePaymentsExpanded(val isExpanded: Boolean) : PlannedPaymentsScreenEvent
    data class OnRecurringPaymentsExpanded(val isExpanded: Boolean) : PlannedPaymentsScreenEvent
    data class OnCreateInstallmentPlan(val input: InstallmentPlanInput) : PlannedPaymentsScreenEvent
}
