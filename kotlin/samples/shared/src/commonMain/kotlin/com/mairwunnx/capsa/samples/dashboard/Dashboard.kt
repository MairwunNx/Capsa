package com.mairwunnx.capsa.samples.dashboard

import com.mairwunnx.capsa.Capability
import com.mairwunnx.capsa.CapabilityScope
import com.mairwunnx.capsa.Decision
import com.mairwunnx.capsa.Intent
import com.mairwunnx.capsa.Signal
import com.mairwunnx.capsa.capability

sealed interface DashboardIntent : Intent
data object RefreshClicked : DashboardIntent

sealed interface DashboardSignal : Signal

/** Tells every tile of the dashboard to load again. */
data object RefreshRequested : DashboardSignal

/**
 * Owns the refresh button. An intent has one owner, so this capability turns the click into a signal,
 * and both tiles react to it without knowing about each other.
 */
context(_: CapabilityScope)
fun dashboardRefresh(): Capability<Unit> = capability<Unit, DashboardIntent, DashboardSignal>(name = "refresh", initial = Unit) {
    onIntent<RefreshClicked> { state, _ -> Decision(state = state, signal = RefreshRequested) }
}
