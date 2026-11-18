package com.mairwunnx.capsa.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import com.mairwunnx.capsa.Intent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * Checks [Slot] on the project’s Compose compiler without a UI toolkit.
 *
 * The tests run a real composition with an empty `Applier` and check SAM conversion, the overloads, and recomposition
 * of a `fun interface` with a `@Composable` method.
 */
class SlotTest : CompositionTest() {

    private data class Clicked(val id: Int) : Intent

    @Test
    fun lambdaConvertsToSlotAndReceivesUiOnIntentAndModifier() = runTest {
        val seen = mutableListOf<String>()
        var capturedIntent: ((Intent) -> Unit)? = null
        var capturedModifier: Modifier? = null
        val slot = Slot<String> { ui, onIntent, modifier ->
            seen += ui
            capturedIntent = onIntent
            capturedModifier = modifier
        }
        val sent = mutableListOf<Intent>()

        compose {
            slot("hello", { sent += it }, Modifier)
        }

        assertEquals(listOf("hello"), seen)
        checkNotNull(capturedIntent).invoke(Clicked(7))
        assertEquals(listOf<Intent>(Clicked(7)), sent)
        assertSame(Modifier, capturedModifier)
    }

    @Test
    fun callWithoutModifierPassesTheDefaultModifier() = runTest {
        var capturedModifier: Modifier? = null
        val slot = Slot<Int> { _, _, modifier -> capturedModifier = modifier }

        compose {
            slot(1, {})
        }

        assertSame(Modifier, capturedModifier)
    }

    @Test
    fun aSlotRecomposesWhenTheUiModelChanges() = runTest {
        val rendered = mutableListOf<Int>()
        val slot = Slot<Int> { ui, _, _ -> rendered += ui }
        val state = mutableIntStateOf(1)

        val host = compose {
            slot(state.intValue, {})
        }
        state.intValue = 2
        Snapshot.sendApplyNotifications()
        host.frame()

        assertEquals(listOf(1, 2), rendered)
    }

    @Test
    fun slotCanBeContravariantInItsUiType() = runTest {
        val rendered = mutableListOf<Any>()
        val anySlot: Slot<Any> = Slot { ui, _, _ -> rendered += ui }
        val stringSlot: Slot<String> = anySlot

        compose {
            stringSlot("x", {})
        }

        assertEquals(listOf<Any>("x"), rendered)
    }

    /** Repeats [Slot] and checks the overload with a default body inside a `fun interface`. */
    private fun interface SlotWithDefaultOverload<in U> {
        @Composable
        operator fun invoke(ui: U, onIntent: (Intent) -> Unit, modifier: Modifier)

        @Composable
        operator fun invoke(ui: U, onIntent: (Intent) -> Unit) = invoke(ui, onIntent, Modifier)
    }

    @Test
    fun funInterfaceWithAComposableDefaultBodiedOverloadAlsoWorks() = runTest {
        var capturedModifier: Modifier? = null
        val slot = SlotWithDefaultOverload<Int> { _, _, modifier -> capturedModifier = modifier }

        compose {
            slot(1, {})
        }

        assertSame(Modifier, capturedModifier)
    }
}
