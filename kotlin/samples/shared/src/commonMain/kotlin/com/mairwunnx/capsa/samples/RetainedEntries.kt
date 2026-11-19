package com.mairwunnx.capsa.samples

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.retain.retainRetainedValuesStoreRegistry
import androidx.navigation3.runtime.NavEntryDecorator

/**
 * Gives every entry of the back stack a `RetainedValuesStore` of its own.
 *
 * `rememberCapsa` keeps its runtime with `retain`. Without this decorator a screen under another one leaves the composition
 * and loses its runtime. With it the runtime lives until the entry leaves the back stack.
 */
@Composable
fun <T : Any> rememberRetainedValuesStoreNavEntryDecorator(): NavEntryDecorator<T> {
    val registry = retainRetainedValuesStoreRegistry()
    return remember(registry) {
        NavEntryDecorator(onPop = registry::clearChild) { entry ->
            registry.LocalRetainedValuesStoreProvider(entry.contentKey) { entry.Content() }
        }
    }
}
