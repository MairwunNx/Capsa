package com.mairwunnx.capsa.samples.search

import com.mairwunnx.capsa.Capability
import com.mairwunnx.capsa.CapabilityScope
import com.mairwunnx.capsa.Intent
import com.mairwunnx.capsa.Load
import com.mairwunnx.capsa.Signal
import com.mairwunnx.capsa.WorkKey
import com.mairwunnx.capsa.capability
import com.mairwunnx.capsa.commit
import com.mairwunnx.capsa.work
import kotlin.time.Duration.Companion.milliseconds

data class SearchSlice(val query: String = "", val results: Load<List<String>> = Load.Loaded(emptyList()))

data class QueryChanged(val text: String) : Intent
data class ResultsLoaded(val items: List<String>) : Signal
data object SearchFailed : Signal

class SearchPorts(val search: suspend (query: String) -> List<String>)

private val SEARCH = WorkKey("search")

context(_: CapabilityScope)
fun search(ports: SearchPorts): Capability<SearchSlice> = capability("search", SearchSlice()) {
    on<QueryChanged> { state, fact ->
        if (fact.text.isBlank()) {
            state.copy(query = fact.text, results = Load.Loaded(emptyList())).commit(cancel = listOf(SEARCH))
        } else {
            // WorkPolicy.Latest by default: each new character cancels the pause or the request of the previous one.
            state.copy(query = fact.text, results = Load.Loading).commit(
                work(SEARCH, onError = { SearchFailed }, debounce = 300.milliseconds) { ResultsLoaded(ports.search(fact.text)) },
            )
        }
    }
    on<ResultsLoaded> { state, fact -> state.copy(results = Load.Loaded(fact.items)).commit() }
    on<SearchFailed> { state, _ -> state.copy(results = Load.Failed).commit() }
}
