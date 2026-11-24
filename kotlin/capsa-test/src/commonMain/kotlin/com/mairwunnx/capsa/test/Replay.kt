package com.mairwunnx.capsa.test

import com.mairwunnx.capsa.Capability
import com.mairwunnx.capsa.Decision
import com.mairwunnx.capsa.Effect
import com.mairwunnx.capsa.Fact
import com.mairwunnx.capsa.Signal
import com.mairwunnx.capsa.Started
import com.mairwunnx.capsa.Work

/**
 * Holds the result of feeding facts one by one through a single capability.
 *
 * The replay calls [Capability.decide] directly, with no coroutines, no Compose, and no runtime.
 * It neither starts works nor handles nested signals: the result lets you check only how they are described.
 */
public class Replay<S : Any> internal constructor(
    /** The state before the first fact. */
    public val initial: S,

    /** The decisions for the accepted facts, in the order of handling. */
    public val steps: List<Step<S>>,

    /** The facts that the capability doesn’t accept, so the runtime would never pass them to [Capability.decide]. */
    public val ignored: List<Fact>,
) {
    /** Pairs an accepted `fact` with the `decision` it produced. */
    public class Step<S : Any>(public val fact: Fact, public val decision: Decision<S, Signal>)

    /** The final state, or [initial] if the capability accepted no facts. */
    public val state: S get() = steps.lastOrNull()?.decision?.state ?: initial

    /** The state after each accepted fact. */
    public val states: List<S> get() = steps.map { it.decision.state }

    /** The effects of all decisions, in the order of handling. */
    public val effects: List<Effect> get() = steps.flatMap { it.decision.effects }

    /** The works of all decisions. The replay doesn’t start them. */
    public val works: List<Work<Signal>> get() = steps.flatMap { it.decision.works }

    /** The signals of all decisions. The replay doesn’t handle them again. */
    public val signals: List<Signal> get() = steps.flatMap { it.decision.signals }
}

/**
 * Feeds `facts` into the capability one by one and returns a snapshot of every decision.
 *
 * The replay starts from `from` and skips the facts that are not in [Capability.handles].
 * [Started] runs the bootstrap of the capability, as the runtime does: `replay(Started, RetryClicked)`.
 * A capability without a bootstrap ignores it.
 * To check works, virtual time, and chains of signals, use [capsaHarness].
 *
 * ```
 * val replay = likes.replay(HighlightClicked(4))
 * assertEquals(LikesSlice(highlighted = 4, busy = true), replay.state)
 * ```
 *
 * @param facts the facts in the order of handling
 * @param from the state before the first fact
 * @return the result of the pure replay
 */
public fun <S : Any> Capability<S>.replay(vararg facts: Fact, from: S = initial()): Replay<S> {
    var state = from
    val steps = mutableListOf<Replay.Step<S>>()
    val ignored = mutableListOf<Fact>()
    for (fact in facts) {
        val decision = when {
            fact === Started -> bootstrap(state)
            handles.any { it.isInstance(fact) } -> decide(state, fact)
            else -> null
        }
        if (decision == null) {
            ignored += fact
            continue
        }
        state = decision.state
        steps += Replay.Step(fact, decision)
    }
    return Replay(from, steps, ignored)
}
