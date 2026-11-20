package com.mairwunnx.capsa.samples

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.ui.window.ComposeUIViewController
import androidx.savedstate.SavedState
import androidx.savedstate.read
import androidx.savedstate.savedState
import platform.UIKit.UIViewController

/**
 * The saveable state of the app, kept through the end of the process by iOS state restoration.
 *
 * ComposeUIViewController keeps rememberSaveable, the back stack, and every SavedStateHandle only in memory, so an app
 * that iOS ended in the background would start on the home screen. Here the root registry of the composition turns
 * that state into a string: ContentView.swift keeps it in @SceneStorage, and iOS hands it back when it relaunches
 * the scene. When the user closes the app in the switcher, iOS drops the stored state and the app starts afresh.
 */
class SceneState(restored: String?) {
    private val registry = SaveableStateRegistry(restored?.let(::decode)) { true }

    /** The state to store. */
    fun save(): String = encode(registry.performSave())

    /** The iOS entry point: ContentView.swift shows this controller. */
    fun viewController(): UIViewController = ComposeUIViewController {
        CompositionLocalProvider(LocalSaveableStateRegistry provides registry) { App() }
    }
}

private class Unsupported : Exception()

// Each value starts with a tag, numbers end with a semicolon, and a string carries its length: n, b1, i42;, s5:hello.
private fun StringBuilder.write(value: Any?) {
    when (value) {
        null -> append('n')
        is String -> append('s').append(value.length).append(':').append(value)
        is Boolean -> append('b').append(if (value) '1' else '0')
        is Int -> append('i').append(value).append(';')
        is Long -> append('l').append(value).append(';')
        is Float -> append('f').append(value.toRawBits()).append(';')
        is Double -> append('d').append(value.toRawBits()).append(';')
        is SavedState -> writeEntries('S', value.read { toMap() })
        is Map<*, *> -> writeEntries('m', value)
        is List<*> -> {
            append('L').append(value.size).append(';')
            value.forEach { write(it) }
        }
        else -> throw Unsupported()
    }
}

private fun StringBuilder.writeEntries(tag: Char, entries: Map<*, *>) {
    append(tag).append(entries.size).append(';')
    entries.forEach { (key, item) ->
        write(key)
        write(item)
    }
}

private class Reader(private val text: String) {
    private var at = 0

    fun read(): Any? = when (val tag = text[at++]) {
        'n' -> null
        's' -> {
            val length = text.indexOf(':', at).let { end -> text.substring(at, end).toInt().also { at = end + 1 } }
            text.substring(at, at + length).also { at += length }
        }
        'b' -> text[at++] == '1'
        'i', 'l', 'f', 'd' -> number(tag, number())
        'S' -> savedState(entries().mapKeys { it.key as String })
        'm' -> entries()
        'L' -> List(number().toInt()) { read() }
        else -> error("Unknown tag at $at")
    }

    private fun number(tag: Char, digits: String): Any = when (tag) {
        'i' -> digits.toInt()
        'l' -> digits.toLong()
        'f' -> Float.fromBits(digits.toInt())
        else -> Double.fromBits(digits.toLong())
    }

    private fun number(): String = text.substring(at, text.indexOf(';', at)).also { at += it.length + 1 }

    // Mutable, as Compose keeps it: SaveableStateHolder removes the state of an entry once it consumes it.
    private fun entries(): MutableMap<Any?, Any?> = LinkedHashMap<Any?, Any?>().apply { repeat(number().toInt()) { put(read(), read()) } }
}

private fun encode(values: Map<String, List<Any?>>): String = buildString {
    // A key with a value this format can't hold is left out: its screen starts empty, the others come back.
    val kept = values.mapNotNull { (key, items) ->
        try {
            key to buildString { write(items) }
        } catch (_: Unsupported) {
            null
        }
    }
    append(kept.size).append(';')
    kept.forEach { (key, items) -> write(key).also { append(items) } }
}

private fun decode(text: String): Map<String, List<Any?>>? = try {
    val reader = Reader("m$text")
    @Suppress("UNCHECKED_CAST")
    (reader.read() as Map<Any?, Any?>).entries.associate { (key, items) -> key as String to items as List<Any?> }
} catch (_: Exception) {
    null // a state from an older build of the demo: start afresh
}
