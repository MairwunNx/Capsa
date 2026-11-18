package com.mairwunnx.capsa

import kotlin.reflect.KClass

/**
 * Routes an [Effect] to a handler of the host.
 *
 * A screen doesn’t gather the effects of different capabilities into one sealed hierarchy, so the compiler
 * can’t check an exhaustive `when`. [EffectHandlers] passes an unknown effect to `onUnhandled`,
 * which throws by default.
 *
 * Handlers aren’t `suspend` functions. The collector calls them one after another, so a long call
 * delays the next effects. An async scenario launches a coroutine inside the handler.
 */
public class EffectHandlers internal constructor(
    private val handlers: List<Handler<*>>,
    private val onUnhandled: (Effect) -> Unit,
) {
    internal class Handler<E : Effect>(val type: KClass<E>, val handle: (E) -> Unit) {
        @Suppress("UNCHECKED_CAST")
        fun handleUnchecked(effect: Effect) {
            handle(effect as E)
        }
    }

    /**
     * Passes `effect` to its handler. Throws if the effect implements two interfaces that both have handlers.
     *
     * If no handler fits, calls `onUnhandled`.
     */
    public fun handle(effect: Effect) {
        val matching = handlers.filter { it.type.isInstance(effect) }
        check(matching.size <= 1) {
            "Effect $effect matches several handlers at once: ${matching.joinToString { it.type.simpleName.orEmpty() }}"
        }
        val handler = matching.firstOrNull()
        if (handler == null) onUnhandled(effect) else handler.handleUnchecked(effect)
    }

    /** Registers handlers inside the [effectHandlers] block. */
    public class Builder internal constructor() {
        internal val handlers: MutableList<Handler<*>> = mutableListOf()

        /**
         * Registers a handler for effects of type [E] and its subtypes.
         *
         * On Kotlin/Native the builder can’t compare classes, so it only catches two handlers for the same class.
         * An effect that matches two handlers still fails in [handle].
         *
         * @param handle a synchronous handler of the effect
         * @throws IllegalArgumentException if a handler for [E], its supertype, or its subtype is already registered
         */
        public inline fun <reified E : Effect> on(noinline handle: (E) -> Unit) {
            addHandler(E::class, handle)
        }

        @PublishedApi
        internal fun <E : Effect> addHandler(type: KClass<E>, handle: (E) -> Unit) {
            require(handlers.none { it.type == type }) { "A handler for ${type.simpleName} is already registered" }
            val overlap = handlers.firstOrNull { it.type.overlaps(type) }
            require(overlap == null) {
                "Handlers for ${overlap?.type?.simpleName} and ${type.simpleName} overlap: one type inherits the other"
            }
            handlers += Handler(type, handle)
        }
    }
}

/**
 * Builds [EffectHandlers] from an `on<E> { ... }` block.
 *
 * ```
 * val handlers = effectHandlers {
 *     on<OpenProfile> { router.navigateTo(...) }
 *     on<Track> { analytics.send(it.event) }
 * }
 * ```
 *
 * `onUnhandled` decides what happens to an unknown effect. The default handler
 * throws `IllegalStateException`, so the mistake doesn’t get lost.
 *
 * @param onUnhandled the handler of an unknown effect
 * @param build the block that registers the handlers
 * @return the ready set of effect handlers
 */
public fun effectHandlers(
    onUnhandled: (Effect) -> Unit = {
        error("No handler for effect $it. Add on<${it::class.simpleName}> on the screen or in the ViewModel.")
    },
    build: EffectHandlers.Builder.() -> Unit,
): EffectHandlers = EffectHandlers(EffectHandlers.Builder().apply(build).handlers.toList(), onUnhandled)
