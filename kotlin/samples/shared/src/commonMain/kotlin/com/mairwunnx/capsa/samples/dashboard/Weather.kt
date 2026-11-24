package com.mairwunnx.capsa.samples.dashboard

import com.mairwunnx.capsa.Capability
import com.mairwunnx.capsa.CapabilityScope
import com.mairwunnx.capsa.Decision
import com.mairwunnx.capsa.Load
import com.mairwunnx.capsa.Signal
import com.mairwunnx.capsa.WorkKey
import com.mairwunnx.capsa.capability

data class WeatherSlice(val celsius: Load<Int> = Load.Loading)

sealed interface WeatherSignal : Signal
data class WeatherLoaded(val celsius: Int) : WeatherSignal
data object WeatherFailed : WeatherSignal

class WeatherPorts(val celsius: suspend () -> Int)

private val LOAD = WorkKey("load")

context(_: CapabilityScope)
fun weather(ports: WeatherPorts): Capability<WeatherSlice> = capability<WeatherSlice, Nothing, WeatherSignal>(name = "weather", initial = WeatherSlice()) {
    val works = object {
        fun load() = work(key = LOAD, onError = { WeatherFailed }) { WeatherLoaded(ports.celsius()) }
    }

    bootstrap { state -> Decision(state = state, work = works.load()) }
    onSignal<RefreshRequested> { _, _ -> Decision(state = WeatherSlice(), work = works.load()) }
    onSignal<WeatherLoaded> { state, loaded -> Decision(state = state.copy(celsius = Load.Loaded(loaded.celsius))) }
    onSignal<WeatherFailed> { state, _ -> Decision(state = state.copy(celsius = Load.Failed)) }
}
