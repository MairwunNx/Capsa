package com.mairwunnx.capsa.compose

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mairwunnx.capsa.Capsa
import com.mairwunnx.capsa.CapsaLogger
import com.mairwunnx.capsa.Decision
import com.mairwunnx.capsa.Effect
import com.mairwunnx.capsa.Intent
import com.mairwunnx.capsa.Signal
import com.mairwunnx.capsa.WorkKey
import com.mairwunnx.capsa.capability
import com.mairwunnx.capsa.effectHandlers
import com.mairwunnx.capsa.viewmodel.CapsaViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.milliseconds

/**
 * Checks [CollectEffects] in a real composition with a [CapsaViewModel] behind it. The lifecycle comes from
 * [LocalLifecycleOwner], as on a real screen.
 *
 * The view model runs with the release policy (`Capsa.failFast` off), so a failing handler lands in [RecordingLogger]
 * instead of crashing the test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CollectEffectsCompositionTest : CompositionTest() {

    private data class Open(val id: Int) : Intent
    private data class Navigate(val id: Int) : Effect
    private data object Ticked : Signal

    private class TestOwner(initial: Lifecycle.State) : LifecycleOwner {
        override val lifecycle: LifecycleRegistry = LifecycleRegistry.createUnsafe(this).apply { currentState = initial }
    }

    /** Turns `Open(id)` into `Navigate(id)` and keeps a `whileVisible` stream that writes down when it starts and stops. */
    private class ScreenViewModel(logger: CapsaLogger, subscriptions: MutableList<String>) : CapsaViewModel(logger) {
        val screen: StateFlow<Unit> = install(
            capability<Unit, Open, Ticked>(name = "screen", initial = Unit) {
                bootstrap { state ->
                    Decision(
                        state = state,
                        work = stream(key = TICKS, onError = { Ticked }, whileVisible = true) {
                            flow<Ticked> { awaitCancellation() }
                                .onStart { subscriptions += "start" }
                                .onCompletion { subscriptions += "stop" }
                        },
                    )
                }
                onIntent<Open> { state, open -> Decision(state = state, effect = Navigate(open.id)) }
            },
        )
    }

    private val logger = RecordingLogger()
    private val subscriptions = mutableListOf<String>()
    private var failFastBefore = Capsa.failFast

    @BeforeTest
    fun setUp() {
        failFastBefore = Capsa.failFast
        Capsa.failFast = false
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        CapsaViewModel.resetWorkDispatcher()
        Capsa.failFast = failFastBefore
    }

    private fun TestScope.screen(): ScreenViewModel {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        CapsaViewModel.setWorkDispatcher(StandardTestDispatcher(testScheduler))
        return ScreenViewModel(logger, subscriptions)
    }

    /** Runs everything due, a frame, and what the frame started: a collector that left in the frame starts the grace. */
    private fun TestScope.settle(host: HeadlessComposition) {
        testScheduler.advanceUntilIdle()
        host.frame()
        testScheduler.advanceUntilIdle()
    }

    @Test
    fun anEffectReachesTheHandlerExactlyOnceAndRecompositionKeepsTheSubscription() = runTest {
        val vm = screen()
        val owner = TestOwner(Lifecycle.State.STARTED)
        val handled = mutableListOf<String>()
        val generation = mutableIntStateOf(0)
        val host = compose {
            val current = generation.intValue
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                CollectEffects(vm.effects, effectHandlers { on<Navigate> { handled += "$current:${it.id}" } })
            }
        }

        vm.send(Open(1))
        settle(host)
        assertEquals(listOf("0:1"), handled)

        generation.intValue = 1
        Snapshot.sendApplyNotifications()
        settle(host)
        assertEquals(listOf("0:1"), handled)

        vm.send(Open(2))
        settle(host)
        assertEquals(listOf("0:1", "1:2"), handled)
        // A restarted subscription would report the screen hidden and visible again and restart the stream.
        assertEquals(listOf("start"), subscriptions)
        assertEquals(emptyList(), logger.errors)
    }

    @Test
    fun aFailingHandlerGoesToTheErrorPolicyAndTheCompositionKeepsWorking() = runTest {
        val vm = screen()
        val owner = TestOwner(Lifecycle.State.STARTED)
        val handled = mutableListOf<Effect>()
        val recompositions = mutableIntStateOf(0)
        var composed = 0
        val host = compose {
            recompositions.intValue
            composed++
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                CollectEffects(
                    vm.effects,
                    effectHandlers {
                        on<Navigate> {
                            check(it.id != 1) { "navigation is down" }
                            handled += it
                        }
                    },
                )
            }
        }

        vm.send(Open(1))
        settle(host)
        val error = assertIs<IllegalStateException>(logger.errors.single())
        assertEquals("Capsa effect Navigate(id=1) failed on the screen", error.message)
        assertEquals("navigation is down", error.cause?.message)

        vm.send(Open(2))
        settle(host)
        assertEquals(listOf<Effect>(Navigate(2)), handled)

        recompositions.intValue = 1
        Snapshot.sendApplyNotifications()
        host.frame()
        assertEquals(2, composed)
        assertEquals(Recomposer.State.Idle, host.recomposer.currentState.value)
    }

    @Test
    fun theRuntimeSeesTheScreenOnlyFromStartedAndHidesItAfterTheGrace() = runTest {
        val vm = screen()
        val owner = TestOwner(Lifecycle.State.CREATED)
        val host = compose {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                CollectEffects(vm.effects, effectHandlers {})
            }
        }
        settle(host)
        // Below STARTED nobody collects the screen, so it stays hidden.
        assertEquals(emptyList(), subscriptions)

        owner.lifecycle.currentState = Lifecycle.State.STARTED
        settle(host)
        assertEquals(listOf("start"), subscriptions)

        owner.lifecycle.currentState = Lifecycle.State.CREATED
        settle(host)
        assertEquals(listOf("start", "stop"), subscriptions)

        owner.lifecycle.currentState = Lifecycle.State.STARTED
        settle(host)
        assertEquals(listOf("start", "stop", "start"), subscriptions)
    }

    @Test
    fun aStopShorterThanTheGraceKeepsTheStreamRunning() = runTest {
        val vm = screen()
        val owner = TestOwner(Lifecycle.State.STARTED)
        val host = compose {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                CollectEffects(vm.effects, effectHandlers {})
            }
        }
        settle(host)

        // A rotation: the old screen stops, and the new one starts a moment later.
        owner.lifecycle.currentState = Lifecycle.State.CREATED
        host.frame()
        testScheduler.advanceTimeBy(300.milliseconds)
        owner.lifecycle.currentState = Lifecycle.State.STARTED
        settle(host)

        assertEquals(listOf("start"), subscriptions)
    }

    @Test
    fun aScreenThatLeavesTheCompositionIsHidden() = runTest {
        val vm = screen()
        val owner = TestOwner(Lifecycle.State.STARTED)
        val shown = mutableStateOf(true)
        val host = compose {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                if (shown.value) CollectEffects(vm.effects, effectHandlers {})
            }
        }
        settle(host)
        assertEquals(listOf("start"), subscriptions)

        // The back stack: navigation stops composing the screen.
        shown.value = false
        Snapshot.sendApplyNotifications()
        settle(host)

        assertEquals(listOf("start", "stop"), subscriptions)
    }

    @Test
    fun aScreenWithoutEffectsThatCollectsItsStateIsVisible() = runTest {
        val vm = screen()
        val shown = mutableStateOf(true)
        val host = compose {
            if (shown.value) vm.screen.collectAsState()
        }
        settle(host)
        assertEquals(listOf("start"), subscriptions)

        shown.value = false
        Snapshot.sendApplyNotifications()
        settle(host)

        assertEquals(listOf("start", "stop"), subscriptions)
    }

    @Test
    fun effectsSentWhileTheScreenIsStoppedArriveOnceItStartsAgain() = runTest {
        val vm = screen()
        val owner = TestOwner(Lifecycle.State.STARTED)
        val handled = mutableListOf<Effect>()
        val host = compose {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                CollectEffects(vm.effects, effectHandlers { on<Navigate> { handled += it } })
            }
        }
        settle(host)

        owner.lifecycle.currentState = Lifecycle.State.CREATED
        settle(host)
        vm.send(Open(1))
        vm.send(Open(2))
        settle(host)
        assertEquals(emptyList(), handled)

        owner.lifecycle.currentState = Lifecycle.State.STARTED
        settle(host)
        assertEquals(listOf<Effect>(Navigate(1), Navigate(2)), handled)
    }

    private companion object {
        val TICKS = WorkKey("ticks")
    }
}
