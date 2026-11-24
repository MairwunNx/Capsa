package com.mairwunnx.capsa.benchmarks

import com.mairwunnx.capsa.CapsaBuilder
import com.mairwunnx.capsa.CapsaRuntime
import com.mairwunnx.capsa.Decision
import com.mairwunnx.capsa.Intent
import com.mairwunnx.capsa.Signal
import com.mairwunnx.capsa.WorkKey
import com.mairwunnx.capsa.capability
import com.mairwunnx.capsa.work
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.benchmark.TearDown
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration.Companion.milliseconds

/**
 * Search as you type: every keystroke cancels the debounced request of the previous one and starts a new one with `WorkPolicy.Latest`.
 *
 * The debounce never runs out, so one operation is a fact, a cancel, and a launch, without the request itself.
 */
@State(Scope.Benchmark)
class LatestWorkBenchmark {

    private lateinit var scope: CoroutineScope
    private lateinit var runtime: CapsaRuntime
    private lateinit var query: StateFlow<String>
    private val keystrokes = List(KEYSTROKES) { QueryChanged("q".repeat(it + 1)) }
    private var next = 0

    @Setup
    fun setUp() {
        scope = unconfinedScope()
        val builder = CapsaBuilder()
        query = builder.install(
            capability<String, QueryChanged, SearchSignal>(name = "search", initial = "") {
                onIntent<QueryChanged> { _, intent ->
                    Decision(
                        state = intent.text,
                        work = work(key = SEARCH, onError = { SearchFailed }, debounce = 300.milliseconds) { SearchLoaded(intent.text) },
                    )
                }
                onSignal<SearchLoaded> { state, _ -> Decision(state = state) }
                onSignal<SearchFailed> { state, _ -> Decision(state = state) }
            },
        )
        runtime = builder.build(scope)
    }

    @TearDown
    fun tearDown() {
        scope.cancel()
    }

    @Benchmark
    fun typeOneCharacter(): String {
        runtime.append(keystrokes[next])
        next = (next + 1) % KEYSTROKES
        return query.value
    }

    private data class QueryChanged(val text: String) : Intent

    private sealed interface SearchSignal : Signal

    private data class SearchLoaded(val query: String) : SearchSignal

    private data object SearchFailed : SearchSignal

    private companion object {
        const val KEYSTROKES = 8
        val SEARCH = WorkKey("search")
    }
}
