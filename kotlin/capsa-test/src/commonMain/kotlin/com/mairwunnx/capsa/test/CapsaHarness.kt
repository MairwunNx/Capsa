package com.mairwunnx.capsa.test

import com.mairwunnx.capsa.Capability
import com.mairwunnx.capsa.CapsaBuilder
import com.mairwunnx.capsa.CapsaConfig
import com.mairwunnx.capsa.CapsaObserver
import com.mairwunnx.capsa.CapsaRuntime
import com.mairwunnx.capsa.CapsaViolation
import com.mairwunnx.capsa.CapsaVisibility
import com.mairwunnx.capsa.Decision
import com.mairwunnx.capsa.Effect
import com.mairwunnx.capsa.Fact
import com.mairwunnx.capsa.Signal
import com.mairwunnx.capsa.WorkKey
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlin.time.Duration

/** Describes a start, a cancellation, a failure, or a dropped signal of a work during a test. */
public sealed interface WorkEvent {
    /** The name of the capability that created the work. */
    public val capability: String

    /** The key of the work. */
    public val key: WorkKey

    /** The runtime started the work `key` of `capability`. */
    public data class Started(override val capability: String, override val key: WorkKey) : WorkEvent

    /** The runtime cancelled the work `key` of `capability`. */
    public data class Cancelled(override val capability: String, override val key: WorkKey) : WorkEvent

    /** The work `key` failed, and `onError` turned the error into a signal. */
    public data class Failed(override val capability: String, override val key: WorkKey, val error: Throwable) : WorkEvent

    /** The work `key` was cancelled, and the runtime dropped its late signal. */
    public data class Dropped(override val capability: String, override val key: WorkKey, val signal: Signal) : WorkEvent
}

/**
 * Runs a real [CapsaRuntime] on virtual time.
 *
 * In strict mode (the default) a broken contract fails the test at once: a fact nobody handles or a failing `decide`
 * can’t slip through. A test that checks the violation itself creates the harness with `strict = false`
 * and reads [violations].
 * The runtime lives in `backgroundScope`, so `runTest` doesn’t wait for it to finish.
 *
 * The scheduler handles facts only after [runCurrent] or [advanceTimeBy]. For timers and streams use [advanceTimeBy].
 * The harness has no `advanceUntilIdle`: it skips `backgroundScope`, where the runtime lives, so it would handle no facts.
 */
@OptIn(ExperimentalCoroutinesApi::class)
public class CapsaHarness internal constructor(
    private val scheduler: TestCoroutineScheduler,
    private val runtime: CapsaRuntime,
    private val states: Map<Capability<*>, StateFlow<*>>,
    private val recorder: Recorder,
) {
    /** The facts in the order of handling, including the signals of works and [com.mairwunnx.capsa.Started]. */
    public val facts: List<Fact> get() = recorder.facts

    /** The effects that the runtime delivered to the test subscriber. */
    public val effects: List<Effect> get() = recorder.deliveredEffects

    /** Starts, cancellations, failures, and dropped signals of works, in the order they happened. */
    public val workEvents: List<WorkEvent> get() = recorder.workEvents

    /** The violations of the Capsa contract collected so far. */
    public val violations: List<CapsaViolation> get() = recorder.violations

    /**
     * Puts `fact` into the queue of the runtime.
     *
     * The scheduler doesn’t handle the queue until [runCurrent] or [advanceTimeBy].
     */
    public fun append(fact: Fact): Unit = runtime.append(fact)

    /**
     * Simulates the screen going to the background (`false`) and coming back (`true`).
     *
     * Works with `whileVisible` pause and restart. The screen of the harness starts visible: it uses [CapsaVisibility.Host],
     * so collectors of the slices don’t matter. Like [append], it waits for [runCurrent].
     */
    public fun setVisible(visible: Boolean): Unit = runtime.onVisibilityChanged(visible)

    /** Runs every task due at the current moment of virtual time. */
    public fun runCurrent(): Unit = scheduler.runCurrent()

    /**
     * Moves virtual time forward by `duration` and runs the tasks due at the new moment.
     *
     * @param duration a positive interval of virtual time
     */
    public fun advanceTimeBy(duration: Duration) {
        scheduler.advanceTimeBy(duration.inWholeMilliseconds)
        scheduler.runCurrent()
    }

    /**
     * Returns the current state of `capability`.
     *
     * @param capability a capability passed to [capsaHarness]
     * @return the current state of the capability
     * @throws NoSuchElementException if the harness doesn’t hold `capability`
     */
    @Suppress("UNCHECKED_CAST")
    public fun <S : Any> stateOf(capability: Capability<S>): S = (states.getValue(capability) as StateFlow<S>).value

    /** Returns the collected effects and clears their list in the harness. */
    public fun takeEffects(): List<Effect> = recorder.deliveredEffects.toList().also { recorder.deliveredEffects.clear() }

    /**
     * Checks that the runtime recorded no violations of the contract.
     *
     * @throws AssertionError if [violations] is not empty
     */
    public fun assertNoViolations() {
        if (violations.isNotEmpty()) throw AssertionError("Capsa contract violations: $violations")
    }

    internal class Recorder : CapsaObserver {
        val facts = mutableListOf<Fact>()
        val deliveredEffects = mutableListOf<Effect>()
        val workEvents = mutableListOf<WorkEvent>()
        val violations = mutableListOf<CapsaViolation>()

        override fun onFact(fact: Fact) {
            facts += fact
        }

        override fun onDecision(capability: String, fact: Fact, decision: Decision<*>) = Unit

        override fun onWorkStarted(capability: String, key: WorkKey) {
            workEvents += WorkEvent.Started(capability, key)
        }

        override fun onWorkCancelled(capability: String, key: WorkKey) {
            workEvents += WorkEvent.Cancelled(capability, key)
        }

        override fun onWorkFailed(capability: String, key: WorkKey, error: Throwable) {
            workEvents += WorkEvent.Failed(capability, key, error)
        }

        override fun onSignalDropped(capability: String, key: WorkKey, signal: Signal) {
            workEvents += WorkEvent.Dropped(capability, key, signal)
        }
    }
}

/**
 * Builds a [CapsaHarness] from capabilities inside `runTest { ... }`.
 *
 * The factory starts the runtime in `backgroundScope`, subscribes a collector of effects, and queues
 * [com.mairwunnx.capsa.Started]. The test drives the handling with virtual time.
 *
 * ```
 * @Test fun showsThanks() = runTest {
 *     val harness = capsaHarness(likes)
 *     harness.append(LikeClicked(4))
 *     harness.advanceTimeBy(200.milliseconds)
 *     assertEquals(listOf(ShowThanks(4)), harness.effects)
 * }
 * ```
 *
 * @param capabilities the capabilities for the runtime
 * @param maxSignalDepth the maximum depth of a chain of signals
 * @param strict fail the test on the first violation of the contract. `false` collects violations in [CapsaHarness.violations]
 * @return a ready [CapsaHarness]
 */
public fun TestScope.capsaHarness(
    vararg capabilities: Capability<*>,
    maxSignalDepth: Int = CapsaConfig().maxSignalDepth,
    strict: Boolean = true,
): CapsaHarness {
    val recorder = CapsaHarness.Recorder()
    val builder = CapsaBuilder()
    val states = capabilities.associateWith { builder.installAny(it) }
    val runtime = builder.build(
        scope = backgroundScope,
        config = CapsaConfig(
            maxSignalDepth = maxSignalDepth,
            onViolation = {
                recorder.violations += it.violation
                if (strict) throw it
            },
            observer = recorder,
            // Nobody collects the slices in a test, so only setVisible decides.
            visibility = CapsaVisibility.Host,
        ),
    )
    // The effect subscriber starts at once, so an effect reaches the list without an extra runCurrent().
    backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
        runtime.effects.collect { recorder.deliveredEffects += it }
    }
    return CapsaHarness(scheduler = testScheduler, runtime = runtime, states = states, recorder = recorder)
}

private fun <S : Any> CapsaBuilder.installAny(capability: Capability<S>): StateFlow<S> = install(capability)
