package com.mairwunnx.capsa.samples.search

import com.mairwunnx.capsa.Load
import com.mairwunnx.capsa.WorkKey
import com.mairwunnx.capsa.test.WorkEvent
import com.mairwunnx.capsa.test.capsaHarness
import com.mairwunnx.capsa.test.inCapabilityScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class SearchTest {

    @Test
    fun `fast typing sends one request after the pause`() = runTest {
        val queries = mutableListOf<String>()
        val search = inCapabilityScope { search(SearchPorts { query -> queries += query; listOf("Apple") }) }
        val harness = capsaHarness(search)

        harness.append(QueryChanged("a"))
        harness.append(QueryChanged("ap"))
        harness.append(QueryChanged("app"))
        harness.advanceTimeBy(300.milliseconds)

        assertEquals(listOf("app"), queries)
        assertEquals(SearchSlice(query = "app", results = Load.Loaded(listOf("Apple"))), harness.stateOf(search))
    }

    @Test
    fun `a late answer never overwrites a newer one`() = runTest {
        // The old query answers in a second, the new one in 100 ms.
        val search = inCapabilityScope {
            search(SearchPorts { query -> delay(if (query == "ap") 1000 else 100); listOf("results for $query") })
        }
        val harness = capsaHarness(search)

        harness.append(QueryChanged("ap"))
        harness.advanceTimeBy(400.milliseconds)
        harness.append(QueryChanged("app"))
        harness.advanceTimeBy(2.seconds)

        assertEquals(Load.Loaded(listOf("results for app")), harness.stateOf(search).results)
        assertTrue(WorkEvent.Cancelled("search", WorkKey("search")) in harness.workEvents)
    }

    @Test
    fun `a blank query clears the results and cancels the request`() = runTest {
        val search = inCapabilityScope { search(SearchPorts { listOf("Apple") }) }
        val harness = capsaHarness(search)

        harness.append(QueryChanged("app"))
        harness.advanceTimeBy(100.milliseconds)
        harness.append(QueryChanged(""))
        harness.advanceTimeBy(1.seconds)

        assertEquals(SearchSlice(query = "", results = Load.Loaded(emptyList())), harness.stateOf(search))
    }

    @Test
    fun `a failed request shows the error`() = runTest {
        val search = inCapabilityScope { search(SearchPorts { error("offline") }) }
        val harness = capsaHarness(search)

        harness.append(QueryChanged("app"))
        harness.advanceTimeBy(300.milliseconds)

        assertEquals(Load.Failed, harness.stateOf(search).results)
    }
}
