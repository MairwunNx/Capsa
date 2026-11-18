package com.mairwunnx.capsa

/**
 * Holds the result of one step of a capability: the new state and the next actions.
 *
 * The public properties are open to tests in other modules. The internal constructor keeps [commit]
 * the only way to create a decision.
 */
public class Decision<out S : Any> internal constructor(
    public val state: S,
    public val effects: List<Effect>,
    public val works: List<Work>,
    public val cancels: List<WorkKey>,
    public val signals: List<Signal>,
) {
    override fun toString(): String =
        "Decision(state=$state, effects=$effects, works=$works, cancels=$cancels, signals=$signals)"
}

/**
 * Commits the new state and describes the next actions for the runtime.
 *
 * A call without arguments changes the state only. [Capability.decide] stays pure this way:
 * the runtime performs the effects, starts the works, and delivers the signals.
 *
 * @param effects commands to the outside world, performed by the host
 * @param works works to start
 * @param cancel keys of this capability’s works to stop. The runtime cancels works first,
 * so `cancel = [K]` together with `works = [work(K)]` restarts the work
 * @param signals facts that the runtime processes right after the current one, before the next fact from the queue.
 * Every capability that handles them receives them, neighbours included: this is how capabilities share data
 */
public fun <S : Any> S.commit(
    effects: List<Effect> = emptyList(),
    works: List<Work> = emptyList(),
    cancel: List<WorkKey> = emptyList(),
    signals: List<Signal> = emptyList(),
): Decision<S> = Decision(
    state = this,
    effects = effects,
    works = works,
    cancels = cancel,
    signals = signals,
)

/** Commits the new state and starts one work. A shortcut for `commit(works = listOf(work))`. */
public fun <S : Any> S.commit(work: Work): Decision<S> = commit(works = listOf(work))

/** Commits the new state and sends one effect. A shortcut for `commit(effects = listOf(effect))`. */
public fun <S : Any> S.commit(effect: Effect): Decision<S> = commit(effects = listOf(effect))

/** Commits the new state and sends one signal. A shortcut for `commit(signals = listOf(signal))`. */
public fun <S : Any> S.commit(signal: Signal): Decision<S> = commit(signals = listOf(signal))
