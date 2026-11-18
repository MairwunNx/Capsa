package com.mairwunnx.capsa

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/**
 * Keeps the last events of the runtime for [CapsaViolationException].
 *
 * Entries hold references to facts, and the strings are built only on a violation. On each fact the runtime
 * pays for a write into an array, not for the `toString()` of a big list. Works write here from their own threads,
 * so the access is synchronized.
 */
internal class CapsaHistory(private val capacity: Int = DEFAULT_CAPACITY) {

    private val lock = SynchronizedObject()
    private val entries = arrayOfNulls<Entry>(capacity)
    private var next = 0
    private var size = 0

    fun add(entry: Entry) {
        synchronized(lock) {
            entries[next] = entry
            next = (next + 1) % capacity
            if (size < capacity) size++
        }
    }

    /** Returns the entries, oldest first. */
    fun render(): List<String> = synchronized(lock) {
        List(size) { index ->
            entries[(next - size + index + capacity) % capacity]?.render().orEmpty()
        }
    }

    sealed interface Entry {
        fun render(): String
    }

    class FactHandled(private val fact: Fact, private val handlers: List<CapabilityHolder<*>>) : Entry {
        override fun render(): String =
            "${describeFact(fact)} -> ${handlers.joinToString { it.name }.ifEmpty { "nobody" }}"
    }

    class WorkStarted(private val capability: String, private val key: WorkKey) : Entry {
        override fun render(): String = "$capability: start work \"${key.name}\""
    }

    class WorkCancelled(private val capability: String, private val key: WorkKey) : Entry {
        override fun render(): String = "$capability: cancel work \"${key.name}\""
    }

    class SignalDropped(private val capability: String, private val key: WorkKey, private val signal: Signal) : Entry {
        override fun render(): String = "$capability: drop ${describeFact(signal)} from cancelled work \"${key.name}\""
    }

    class WorkFailed(private val capability: String, private val key: WorkKey, private val error: Throwable) : Entry {
        override fun render(): String = "$capability: work \"${key.name}\" failed with ${error.toString().clip()}"
    }

    private companion object {
        const val DEFAULT_CAPACITY = 32
    }
}

/**
 * Describes a fact for the history and the messages: its kind and its `toString()`.
 *
 * A `data class` and a `data object` keep the text of `toString()` in string literals, so it survives
 * R8 obfuscation. Long text gets clipped, so one big list doesn’t take up the whole report.
 */
internal fun describeFact(fact: Fact): String {
    val kind = when (fact) {
        is Intent -> "Intent"
        is Signal -> "Signal"
        else -> "Fact"
    }
    // A broken toString() must not stop the violation itself from being sent.
    val text = runCatching { fact.toString() }.getOrElse { fact.platformClassName() }
    return "$kind ${text.clip()}"
}

private const val MAX_TEXT_LENGTH = 300

private fun String.clip(): String = if (length <= MAX_TEXT_LENGTH) this else take(MAX_TEXT_LENGTH) + "…"
