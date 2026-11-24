package com.mairwunnx.capsa.samples.dashboard

import com.mairwunnx.capsa.Load
import com.mairwunnx.capsa.test.capsaHarness
import com.mairwunnx.capsa.test.inCapabilityScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DashboardTest {

    @Test
    fun `one refresh reloads both tiles`() = runTest {
        var celsius = 20
        var open = 3
        val weather = inCapabilityScope { weather(WeatherPorts { celsius }) }
        val tasks = inCapabilityScope { tasks(TasksPorts { open }) }
        val harness = capsaHarness(weather, tasks, inCapabilityScope { dashboardRefresh() })
        harness.runCurrent()

        celsius = 25
        open = 1
        harness.append(RefreshClicked)
        harness.runCurrent()

        assertEquals(Load.Loaded(25), harness.stateOf(weather).celsius)
        assertEquals(Load.Loaded(1), harness.stateOf(tasks).open)
    }
}
