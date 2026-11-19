package com.mairwunnx.capsa.samples.dashboard

import com.mairwunnx.capsa.Capability
import com.mairwunnx.capsa.CapabilityScope
import com.mairwunnx.capsa.Load
import com.mairwunnx.capsa.Signal
import com.mairwunnx.capsa.Started
import com.mairwunnx.capsa.Work
import com.mairwunnx.capsa.WorkKey
import com.mairwunnx.capsa.capability
import com.mairwunnx.capsa.commit
import com.mairwunnx.capsa.work

data class WeatherSlice(val celsius: Load<Int> = Load.Loading)

data class WeatherLoaded(val celsius: Int) : Signal
data object WeatherFailed : Signal

class WeatherPorts(val celsius: suspend () -> Int)

private val LOAD = WorkKey("load")

context(_: CapabilityScope)
fun weather(ports: WeatherPorts): Capability<WeatherSlice> = capability("weather", WeatherSlice()) {
    on<Started> { state, _ -> state.commit(load(ports)) }
    on<RefreshClicked> { _, _ -> WeatherSlice().commit(load(ports)) }
    on<WeatherLoaded> { state, fact -> state.copy(celsius = Load.Loaded(fact.celsius)).commit() }
    on<WeatherFailed> { state, _ -> state.copy(celsius = Load.Failed).commit() }
}

private fun load(ports: WeatherPorts): Work = work(LOAD, onError = { WeatherFailed }) { WeatherLoaded(ports.celsius()) }
