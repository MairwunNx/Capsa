package com.mairwunnx.capsa

/**
 * Holds what a capability decided for one fact: the new slice and what happens next.
 *
 * [O] is the type of the signals the capability owns. Its works answer with [O], and it sends only [O] to its neighbours,
 * so the compiler stops a capability that sends a neighbour’s signal.
 *
 * The public properties are open to tests in other modules. The internal constructor keeps the [Decision] function
 * the only way to create a decision.
 */
public class Decision<out S : Any, out O : Signal> internal constructor(
    public val state: S,
    public val effects: List<Effect>,
    public val works: List<Work<O>>,
    public val cancels: List<WorkKey>,
    public val signals: List<O>,
) {
    override fun toString(): String =
        "Decision(state=$state, effects=$effects, works=$works, cancels=$cancels, signals=$signals)"
}

/**
 * Returns the decision of a reaction: the new slice and what the runtime does after it.
 *
 * A reaction decides everything at once and returns it as one value. Nothing happens while the reaction runs:
 * the runtime stores the slice, hands the effects to the host, cancels and starts the works, and delivers the signals
 * after the reaction returns. A `null` in a single-item argument means “nothing”, so a condition fits right into the call.
 *
 * ```
 * onIntent<QueryChanged> { state, intent ->
 *     Decision(
 *         state = state.copy(query = intent.text),
 *         work = works.search(intent.text).takeIf { intent.text.isNotBlank() },
 *         cancel = SEARCH.takeIf { intent.text.isBlank() },
 *     )
 * }
 * ```
 *
 * The runtime applies the parts in a fixed order: effects, cancels, works, signals. A cancel goes before a start,
 * so `cancel = K` together with `work = work(K)` restarts the work. Inside one kind, the single item goes first,
 * then the list. Works start in this order, but they run concurrently and answer in the order they finish:
 * when one work needs the result of another, start it in the reaction to the other’s signal.
 *
 * Name every argument. The names tell a reader what the decision does without looking at the types.
 *
 * @param state the new slice. Pass the old one to keep it
 * @param effect a command for the host: navigation, a snackbar, analytics
 * @param effects several commands for the host, after [effect]
 * @param work a work to start. It answers with this capability’s signals
 * @param works several works to start, after [work]
 * @param cancel the key of this capability’s work to stop. The runtime drops the signals of the stopped run
 * that still wait in the queue
 * @param cancels several keys to stop, after [cancel]
 * @param signal a signal of this capability for its neighbours. The runtime handles it right after the current fact,
 * before the next fact from the queue, and every capability that handles it receives it
 * @param signals several signals, after [signal]
 */
@Suppress("FunctionNaming", "LongParameterList")
public fun <S : Any, O : Signal> Decision(
    state: S,
    effect: Effect? = null,
    effects: List<Effect> = emptyList(),
    work: Work<O>? = null,
    works: List<Work<O>> = emptyList(),
    cancel: WorkKey? = null,
    cancels: List<WorkKey> = emptyList(),
    signal: O? = null,
    signals: List<O> = emptyList(),
): Decision<S, O> = Decision(state, join(effect, effects), join(work, works), join(cancel, cancels), join(signal, signals))

/** Puts `one` before `many` without a copy when one of them is empty: most decisions carry nothing or a single item. */
private fun <T : Any> join(one: T?, many: List<T>): List<T> = when {
    one == null -> many
    many.isEmpty() -> listOf(one)
    else -> listOf(one) + many
}
