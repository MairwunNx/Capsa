package com.mairwunnx.capsa

import kotlinx.coroutines.flow.Flow
import kotlin.concurrent.Volatile
import kotlin.reflect.KClass
import kotlin.time.Duration

/**
 * Restricts the creation of capabilities to the right scope.
 *
 * Constructor functions are declared as
 * `context(_: CapabilityScope) fun likes(...)`.
 * The scope comes from the hosts, `CapsaViewModel`, `CapsaComposable`, and the lambda of `rememberCapsa`, and from
 * `TestCapabilityScope` of the test harness, so the UI and the data layer
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

    /**
     * Returns the classes of intents the capability owns, a part of [handles].
     *
     * An intent has one owner on a screen: the runtime refuses a second capability that handles the same intent class,
     * its supertype, or its subtype. A neighbour that needs to know about the user’s action listens to the owner’s signal.
     */
    public val intents: Set<KClass<out Intent>> get() = emptySet()

    /** Creates the state before the first fact. A restored state takes priority over the initial one. */
    public fun initial(): S

    /**
     * Builds the first [Decision] of the capability while the runtime handles [Started], before any other fact.
     *
     * The runtime passes the initial or the restored state. Works don’t survive a restore, so this is the place
     * to start the initial load and the subscriptions again. Returns `null` when the capability has nothing to start.
     */
    public fun bootstrap(state: S): Decision<S, Signal>? = null

    /**
     * Builds a [Decision] for a `fact` from [handles].
     *
     * The function stays pure: it performs no I/O, reads no clock, and launches no coroutines.
     * [Decision.works] and [Decision.effects] describe the outside work. If the function throws,
     * the runtime reports [CapsaViolation.DecideFailed] and keeps the previous state.
     */
    public fun decide(state: S, fact: Fact): Decision<S, Signal>
}

/**
 * Builds a [Capability] from a bootstrap and reactions to intents and signals.
 *
 * The type arguments name what the capability owns: its slice [S], its intents [I], and its signals [O].
 * State them explicitly. A reaction handles only the capability’s own intents, and its decision sends only
 * the capability’s own signals and starts works that answer with them, so the compiler checks the ownership.
 * A capability without intents or without signals of its own uses `Nothing`.
 *
 * ```
 * context(_: CapabilityScope)
 * fun likes(ports: LikePorts) = capability<LikeSlice, LikeIntent, LikeSignal>(name = "likes", initial = LikeSlice()) {
 *     val works = object {
 *         fun save(liked: Boolean) = work(key = SAVE, onError = { LikeSignal.Failed }) { LikeSignal.Saved(ports.setLiked(liked)) }
 *     }
 *
 *     onIntent<LikeIntent.Clicked> { state, _ ->
 *         Decision(state = state.copy(liked = !state.liked), work = works.save(!state.liked))
 *     }
 *     onSignal<LikeSignal.Saved> { state, saved -> Decision(state = state.copy(likes = saved.likes)) }
 *     onSignal<LikeSignal.Failed> { state, _ -> Decision(state = state.copy(liked = !state.liked), effect = LikeEffect.ShowError) }
 * }
 * ```
 *
 * The block runs once, when the capability is built. Keep it declarative: works, keys, and reactions.
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
 * @param build the block that declares the bootstrap and the reactions
 * @throws IllegalArgumentException if the block registers two reactions to the same fact class
 * or to a supertype and its subtype, a reaction to [Started], or a second bootstrap
 */
public fun <S : Any, I : Intent, O : Signal> capability(
    name: String,
    initial: S,
    build: CapabilityBuilder<S, I, O>.() -> Unit,
): Capability<S> {
    val builder = CapabilityBuilder<S, I, O>().apply(build)
    return ReactiveCapability(name, initial, builder.bootstrap, builder.reactions.toList())
}

/**
 * Declares the bootstrap and the reactions inside the [capability] block.
 *
 * [S] is the slice, [I] the intents the capability owns, and [O] the signals it owns.
 */
public class CapabilityBuilder<S : Any, I : Intent, O : Signal> internal constructor() {

    internal var bootstrap: ((S) -> Decision<S, O>)? = null
        private set

    internal val reactions: MutableList<Reaction<S>> = mutableListOf()

    /**
     * Declares the first decision of the capability: the initial load, the subscriptions, the first effects.
     *
     * The runtime runs it once, before any other fact, with the initial or the restored slice. Every bootstrap
     * of the screen runs in the order of installation, then the signals they send, and only then the next fact.
     * The works it starts run on their own and block nothing. A screen rotation doesn’t run it again,
     * a restore after the process died does.
     *
     * ```
     * bootstrap { state -> Decision(state = state, work = works.observeOrders()) }
     * ```
     *
     * @param react a pure function from the initial or the restored slice to a [Decision]
     * @throws IllegalArgumentException on a second bootstrap in the same capability
     */
    public fun bootstrap(react: (state: S) -> Decision<S, O>) {
        require(bootstrap == null) { "The bootstrap is already declared. Keep one and start everything in it" }
        bootstrap = react
    }

    /**
     * Registers a reaction to the capability’s own intents of type [F] and its subtypes.
     *
     * Only the UI sends intents, and each intent has one owner on the screen.
     *
     * @param react a pure function from the state and the intent to a [Decision]
     * @throws IllegalArgumentException if a reaction to [F], its supertype, or its subtype is already registered
     */
    public inline fun <reified F : I> onIntent(noinline react: (state: S, intent: F) -> Decision<S, O>) {
        addReaction(F::class, intent = true, react)
    }

    /**
     * Registers a reaction to signals of type [F] and its subtypes.
     *
     * A signal comes from anywhere but the UI: the answer of the capability’s own work, a neighbour’s signal,
     * or the host. The type of a neighbour’s signal comes from the neighbour’s package, so the import shows the link.
     *
     * @param react a pure function from the state and the signal to a [Decision]
     * @throws IllegalArgumentException if a reaction to [F], its supertype, or its subtype is already registered,
     * or if [F] is [Started]: declare a [bootstrap] instead
     */
    public inline fun <reified F : Signal> onSignal(noinline react: (state: S, signal: F) -> Decision<S, O>) {
        addReaction(F::class, intent = false, react)
    }

    /**
     * Creates a one-shot work that answers with this capability’s signals [O], like the top-level [com.mairwunnx.capsa.work].
     *
     * Inside the [capability] block the signal type is already known, so Kotlin doesn’t have to infer it from `onError`
     * and `block`, which fails when they return different subtypes through a builder such as `flow { }`.
     */
    @Suppress("LongParameterList")
    public fun work(
        key: WorkKey,
        onError: (Throwable) -> O,
        policy: WorkPolicy = WorkPolicy.Latest,
        debounce: Duration = Duration.ZERO,
        retry: Retry? = null,
        block: suspend () -> O,
    ): Work<O> = com.mairwunnx.capsa.work(key = key, onError = onError, policy = policy, debounce = debounce, retry = retry, block = block)

    /** Creates a subscription that answers with this capability’s signals [O], like the top-level [com.mairwunnx.capsa.stream]. */
    @Suppress("LongParameterList")
    public fun stream(
        key: WorkKey,
        onError: (Throwable) -> O,
        policy: WorkPolicy = WorkPolicy.Latest,
        whileVisible: Boolean = false,
        retry: Retry? = null,
        block: () -> Flow<O>,
    ): Work<O> = com.mairwunnx.capsa.stream(key = key, onError = onError, policy = policy, whileVisible = whileVisible, retry = retry, block = block)

    @PublishedApi
    @Suppress("UNCHECKED_CAST")
    internal fun <F : Fact> addReaction(type: KClass<F>, intent: Boolean, react: (S, F) -> Decision<S, O>) {
        require(type != Started::class) { "A reaction to Started: declare bootstrap { state -> ... } instead" }
        require(reactions.none { it.type == type }) { "A reaction to ${type.simpleName} is already registered" }
        val overlap = reactions.firstOrNull { it.type.overlaps(type) }
        require(overlap == null) {
            "Reactions to ${overlap?.type?.simpleName} and ${type.simpleName} overlap: one type inherits the other. " +
                "Keep one reaction and sort out the subtypes inside it with when"
        }
        reactions += Reaction(type, intent, react as (S, Fact) -> Decision<S, Signal>)
    }
}

internal class Reaction<S : Any>(
    val type: KClass<out Fact>,
    val intent: Boolean,
    val react: (S, Fact) -> Decision<S, Signal>,
)

private class ReactiveCapability<S : Any>(
    override val name: String,
    private val initial: S,
    private val bootstrap: ((S) -> Decision<S, Signal>)?,
    private val reactions: List<Reaction<S>>,
) : Capability<S> {

    override val handles: Set<KClass<out Fact>> = reactions.map { it.type }.toSet()

    @Suppress("UNCHECKED_CAST")
    override val intents: Set<KClass<out Intent>> = reactions.filter { it.intent }.map { it.type as KClass<out Intent> }.toSet()

    override fun initial(): S = initial

    override fun bootstrap(state: S): Decision<S, Signal>? = bootstrap?.invoke(state)

    // The reaction for each class of fact, filled on the first fact of that class. The map is immutable and replaced whole,
    // so a capability shared between threads at worst searches for the same class twice.
    @Volatile
    private var reactionsByClass: Map<KClass<out Fact>, Reaction<S>?> = emptyMap()

    override fun decide(state: S, fact: Fact): Decision<S, Signal> {
        val type = fact::class
        val known = reactionsByClass
        val reaction = known[type] ?: if (type in known) null else findReaction(fact).also { reactionsByClass = known + (type to it) }
        return (reaction ?: error("Capability '$name' does not handle $fact")).react(state, fact)
    }

    private fun findReaction(fact: Fact): Reaction<S>? {
        val matching = reactions.filter { it.type.isInstance(fact) }
        // The build catches inheritance only. A fact that implements two unrelated interfaces is caught here.
        check(matching.size <= 1) {
            "Capability '$name': fact $fact matches several reactions at once: ${matching.joinToString { it.type.simpleName.orEmpty() }}"
        }
        return matching.firstOrNull()
    }

    override fun toString(): String = "Capability($name)"
}
