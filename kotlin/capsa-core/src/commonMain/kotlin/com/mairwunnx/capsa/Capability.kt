package com.mairwunnx.capsa

import kotlin.reflect.KClass

/**
 * Restricts the creation of capabilities to the right scope.
 *
 * Constructor functions are declared as
 * `context(_: CapabilityScope) fun likes(...)`.
 * The scope comes from `CapsaViewModel` and from `TestCapabilityScope` of the test harness, so the UI and the data layer
 * can’t create a capability directly.
 */
public interface CapabilityScope

/**
 * Describes an independent part of a screen’s scenario as a pure function `(state, fact) -> decision`.
 *
 * The [capability] factory builds an implementation from reactions to facts. Implement the interface directly
 * only for a custom way of dispatching.
 */
public interface Capability<S : Any> {

    /**
     * Returns the name of the capability, unique within the screen.
     *
     * The runtime binds the name to each [WorkKey] and prints it in diagnostics.
     */
    public val name: String

    /**
     * Returns the classes of facts the capability accepts. The runtime filters out the other facts.
     *
     * The set must not change: the runtime asks once for each class of fact and remembers the answer.
     */
    public val handles: Set<KClass<out Fact>>

    /** Creates the state before the first fact. A restored state takes priority over the initial one. */
    public fun initial(): S

    /**
     * Builds a [Decision] for a `fact` from [handles].
     *
     * The function stays pure: it performs no I/O, reads no clock, and launches no coroutines.
     * [Decision.works] and [Decision.effects] describe the outside work. If the function throws,
     * the runtime reports [CapsaViolation.DecideFailed] and keeps the previous state.
     */
    public fun decide(state: S, fact: Fact): Decision<S>
}

/**
 * Builds a [Capability] from `on<X> { ... }` reactions.
 *
 * The name comes from `name`, and the [Capability.handles] set comes from the registered reactions.
 *
 * ```
 * val likes = capability("likes", LikesSlice()) {
 *     on<LikeClicked> { state, _ -> state.copy(count = state.count + 1).commit() }
 * }
 * ```
 *
 * The state type is inferred from `initial`. For a sealed state, state it explicitly, otherwise the type narrows
 * to the initial subtype: `capability<DownloadSlice>("download", DownloadSlice.Idle) { ... }`.
 *
 * A reaction to a supertype, such as a sealed interface of facts, accepts all of its subtypes.
 * Reactions don’t overlap: a supertype and its subtype in one capability fail the build, otherwise the result
 * would depend on the order of registration.
 *
 * On Kotlin/Native the build can’t compare classes, so it only catches two reactions to the same class.
 * A fact that matches two reactions still fails in [Capability.decide], at the moment it arrives.
 *
 * @param name the name of the capability, unique on the screen
 * @param initial the initial state
 * @param build the block that lists the reactions with [CapabilityBuilder.on]
 * @throws IllegalArgumentException if the block registers two reactions to the same fact class
 * or to a supertype and its subtype
 */
public fun <S : Any> capability(
    name: String,
    initial: S,
    build: CapabilityBuilder<S>.() -> Unit,
): Capability<S> {
    val builder = CapabilityBuilder<S>().apply(build)
    return ReactiveCapability(name, initial, builder.reactions.toList())
}

/** Registers reactions to facts inside the [capability] block. */
public class CapabilityBuilder<S : Any> internal constructor() {

    internal val reactions: MutableList<Reaction<S>> = mutableListOf()

    /**
     * Registers a reaction to facts of type [F] and its subtypes.
     *
     * @param react a pure function from the state and the fact to a [Decision], which returns its result through [commit]
     * @throws IllegalArgumentException if a reaction to [F], its supertype, or its subtype is already registered
     */
    public inline fun <reified F : Fact> on(noinline react: (state: S, fact: F) -> Decision<S>) {
        addReaction(F::class, react)
    }

    @PublishedApi
    @Suppress("UNCHECKED_CAST")
    internal fun <F : Fact> addReaction(type: KClass<F>, react: (S, F) -> Decision<S>) {
        require(reactions.none { it.type == type }) { "A reaction to ${type.simpleName} is already registered" }
        val overlap = reactions.firstOrNull { it.type.overlaps(type) }
        require(overlap == null) {
            "Reactions to ${overlap?.type?.simpleName} and ${type.simpleName} overlap: one type inherits the other. " +
                "Keep one reaction and sort out the subtypes inside it with when"
        }
        reactions += Reaction(type, react as (S, Fact) -> Decision<S>)
    }
}

internal class Reaction<S : Any>(
    val type: KClass<out Fact>,
    val react: (S, Fact) -> Decision<S>,
)

private class ReactiveCapability<S : Any>(
    override val name: String,
    private val initial: S,
    private val reactions: List<Reaction<S>>,
) : Capability<S> {

    override val handles: Set<KClass<out Fact>> = reactions.map { it.type }.toSet()

    override fun initial(): S = initial

    override fun decide(state: S, fact: Fact): Decision<S> {
        val matching = reactions.filter { it.type.isInstance(fact) }
        // The build catches inheritance only. A fact that implements two unrelated interfaces is caught here.
        check(matching.size <= 1) {
            "Capability '$name': fact $fact matches several reactions at once: ${matching.joinToString { it.type.simpleName.orEmpty() }}"
        }
        val reaction = matching.firstOrNull() ?: error("Capability '$name' does not handle $fact")
        return reaction.react(state, fact)
    }

    override fun toString(): String = "Capability($name)"
}
