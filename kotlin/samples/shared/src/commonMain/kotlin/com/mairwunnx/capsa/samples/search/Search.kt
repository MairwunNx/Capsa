package com.mairwunnx.capsa.samples.search

import com.mairwunnx.capsa.Capability
import com.mairwunnx.capsa.CapabilityScope
import com.mairwunnx.capsa.Decision
import com.mairwunnx.capsa.Intent
import com.mairwunnx.capsa.Load
import com.mairwunnx.capsa.Signal
import com.mairwunnx.capsa.WorkKey
import com.mairwunnx.capsa.capability
import kotlin.time.Duration.Companion.milliseconds

data class SearchSlice(val query: String = "", val results: Load<List<String>> = Load.Loaded(emptyList()))

sealed interface SearchIntent : Intent
data class QueryChanged(val text: String) : SearchIntent

sealed interface SearchSignal : Signal
data class ResultsLoaded(val items: List<String>) : SearchSignal
data object SearchFailed : SearchSignal

class SearchPorts(val search: suspend (query: String) -> List<String>)

private val SEARCH = WorkKey("search")

context(_: CapabilityScope)
fun search(ports: SearchPorts): Capability<SearchSlice> = capability<SearchSlice, SearchIntent, SearchSignal>(name = "search", initial = SearchSlice()) {
    val works = object {
        // WorkPolicy.Latest by default: each new character cancels the pause or the request of the previous one.
        fun search(query: String) = work(key = SEARCH, onError = { SearchFailed }, debounce = 300.milliseconds) { ResultsLoaded(ports.search(query)) }
    }

    onIntent<QueryChanged> { state, changed ->
        val blank = changed.text.isBlank()
        // A blank query needs no request: it clears the results and cancels the request still running.
        Decision(
            state = state.copy(query = changed.text, results = if (blank) Load.Loaded(emptyList()) else Load.Loading),
            work = works.search(changed.text).takeUnless { blank },
            cancel = SEARCH.takeIf { blank },
        )
    }
    onSignal<ResultsLoaded> { state, loaded -> Decision(state = state.copy(results = Load.Loaded(loaded.items))) }
    onSignal<SearchFailed> { state, _ -> Decision(state = state.copy(results = Load.Failed)) }
}
