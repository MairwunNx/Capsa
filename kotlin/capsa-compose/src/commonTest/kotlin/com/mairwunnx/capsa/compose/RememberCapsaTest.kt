package com.mairwunnx.capsa.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.retain.LocalRetainedValuesStoreProvider
import androidx.compose.runtime.retain.ManagedRetainedValuesStore
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mairwunnx.capsa.Capsa
import com.mairwunnx.capsa.CapsaConfig
import com.mairwunnx.capsa.CapsaLogger
import com.mairwunnx.capsa.Effect
import com.mairwunnx.capsa.EffectHandlers
import com.mairwunnx.capsa.Intent
import com.mairwunnx.capsa.Signal
import com.mairwunnx.capsa.SliceSaver
import com.mairwunnx.capsa.Started
import com.mairwunnx.capsa.WorkKey
import com.mairwunnx.capsa.capability
import com.mairwunnx.capsa.commit
import com.mairwunnx.capsa.effectHandlers
import com.mairwunnx.capsa.stream
import kotlinx.coroutines.CoroutineScope
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
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/**
 * Checks [rememberCapsa] with a [CapsaComposable] and with a lambda in a real composition: rotation, the end of the screen, keys, effects,
 * failing effect handlers, saving, visibility, and a scope used the wrong way. A rotation is a [ManagedRetainedValuesStore] that keeps the values while the content
 * leaves the composition and comes back, as the Android host of Compose does during a configuration change.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RememberCapsaTest : CompositionTest() {

    private data object Increment : Intent
    private data object Ping : Intent
    private data object Pong : Effect
    private data object Tracked : Effect
    private data object Failed : Signal

    private object CountSaver : SliceSaver<Int, Int> {
        override val serializer: KSerializer<Int> = Int.serializer()
        override fun save(state: Int): Int = state
        override fun restore(saved: Int): Int = saved
    }

    /** A counter that saves its value, keeps a stream from the start, and a stream that runs only while the screen is visible. */
    private class Counter(scope: CapsaScope, log: MutableList<String>, private val tracked: MutableList<Effect>) : CapsaComposable(scope) {
        val count: StateFlow<Int> = installSaved(counting(log), CountSaver, key = "count")

        override fun EffectHandlers.Builder.handleEffects() {
            on<Tracked> { tracked += it }
        }
    }

    /** A counter whose service handler throws. With `toSignal` the class turns the error into [Failed], otherwise the error policy gets it. */
    private class FailingCounter(scope: CapsaScope, log: MutableList<String>, private val toSignal: Boolean) : CapsaComposable(scope) {
        val count: StateFlow<Int> = install(counting(log))

        override fun EffectHandlers.Builder.handleEffects() {
            on<Tracked> { error(ANALYTICS_DOWN) }
        }

        override suspend fun onEffectFailed(effect: Effect, error: Exception) {
            if (toSignal) signal(Failed) else super.onEffectFailed(effect, error)
        }
    }

    private class TestOwner(initial: Lifecycle.State) : LifecycleOwner {
        override val lifecycle: LifecycleRegistry = LifecycleRegistry.createUnsafe(this).apply { currentState = initial }
    }

    private val logger = RecordingLogger()
    private val log = mutableListOf<String>()
    private val tracked = mutableListOf<Effect>()
    private val created = mutableListOf<Counter>()
    private var failFastBefore = Capsa.failFast

    @BeforeTest
    fun setUp() {
        failFastBefore = Capsa.failFast
        Capsa.failFast = false
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        Capsa.failFast = failFastBefore
    }

    private fun TestScope.config(): CapsaConfig {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        return CapsaConfig(workContext = StandardTestDispatcher(testScheduler))
    }

    @Composable
    private fun counter(config: CapsaConfig, vararg keys: Any?): Counter =
        rememberCapsa(*keys, logger = logger, config = config) { Counter(this, log, tracked).also(created::add) }

    /** The same counter without a class. */
    @Composable
    private fun counterHandle(config: CapsaConfig): CapsaHandle<StateFlow<Int>> = rememberCapsa(logger = logger, config = config) {
        val count = installSaved(counting(log), CountSaver, key = "count")
        handleEffects { on<Tracked> { tracked += it } }
        expose(count)
    }

    @Composable
    private fun saved(registry: SaveableStateRegistry, config: CapsaConfig): Counter {
        var counter: Counter? = null
        CompositionLocalProvider(LocalSaveableStateRegistry provides registry) { counter = counter(config) }
        return checkNotNull(counter)
    }

    private fun TestScope.settle(host: HeadlessComposition) {
        Snapshot.sendApplyNotifications()
        testScheduler.advanceUntilIdle()
        host.frame()
        testScheduler.advanceUntilIdle()
    }

    @Test
    fun aRotationKeepsTheSameObjectAndItsWorks() = runTest {
        val config = config()
        val store = ManagedRetainedValuesStore()
        val owner = TestOwner(Lifecycle.State.RESUMED)
        val shown = mutableStateOf(true)
        val host = compose {
            // The whole content leaves and comes back, as with the activity on a rotation.
            if (shown.value) {
                LocalRetainedValuesStoreProvider(store) {
                    counter(config).count.collectAsStateWithLifecycle(lifecycleOwner = owner)
                }
            }
        }
        settle(host)

        // The old activity goes, the new one comes, and the store keeps what left the composition in between.
        // A rotation is quicker than the grace: frames only, no time passes.
        store.enableRetainingExitedValues()
        shown.value = false
        Snapshot.sendApplyNotifications()
        host.frame()
        shown.value = true
        Snapshot.sendApplyNotifications()
        host.frame()
        store.disableRetainingExitedValues()
        settle(host)

        assertEquals(1, created.size)
        assertEquals(listOf("work start", "visible start"), log)
    }

    @Test
    fun aScreenThatLeavesTheCompositionCancelsTheWorks() = runTest {
        val config = config()
        val shown = mutableStateOf(true)
        val host = compose {
            if (shown.value) counter(config)
        }
        settle(host)
        assertEquals(listOf("work start"), log)

        shown.value = false
        settle(host)

        assertEquals(listOf("work start", "work stop"), log)
    }

    @Test
    fun newKeysCreateANewObjectAndRetireTheOldOne() = runTest {
        val config = config()
        val product = mutableIntStateOf(1)
        val seen = mutableListOf<Counter>()
        val host = compose {
            seen += counter(config, product.intValue)
        }
        settle(host)

        product.intValue = 2
        settle(host)

        assertEquals(2, created.size)
        assertNotSame(created[0], created[1])
        assertSame(created[1], seen.last())
        assertEquals(listOf("work start", "work stop", "work start"), log)
    }

    @Test
    fun theScreenGetsItsEffectsAndTheObjectHandlesTheServiceOnes() = runTest {
        val config = config()
        val handled = mutableListOf<Effect>()
        var capsa: Counter? = null
        val host = compose {
            val counter = counter(config).also { capsa = it }
            CompositionLocalProvider(LocalLifecycleOwner provides TestOwner(Lifecycle.State.STARTED)) {
                CollectEffects(counter.effects, effectHandlers { on<Pong> { handled += it } })
            }
        }
        settle(host)

        checkNotNull(capsa).send(Ping)
        settle(host)

        assertEquals(listOf<Effect>(Pong), handled)
        assertEquals(listOf<Effect>(Tracked), tracked)
        assertEquals(emptyList(), logger.errors)
    }

    @Test
    fun savedSlicesComeBackAfterTheProcessDies() = runTest {
        val config = config()
        val before = SaveableStateRegistry(restoredValues = null) { true }
        var registry = before
        var latest: Counter? = null
        // One content for both compositions, so the object sits at the same place, as after a real restart.
        val content: @Composable () -> Unit = { latest = saved(registry, config) }
        val host = compose(content)
        val first = latest
        settle(host)
        checkNotNull(first).send(Increment)
        checkNotNull(first).send(Increment)
        settle(host)

        registry = SaveableStateRegistry(restoredValues = before.performSave()) { true }
        val again = compose(content)
        settle(again)
        val second = latest

        assertNotSame(first, second)
        assertEquals(2, checkNotNull(second).count.value)
    }

    @Test
    fun withoutARegistryTheObjectWorksAndSavesNothing() = runTest {
        val config = config()
        var capsa: Counter? = null
        val host = compose {
            CompositionLocalProvider(LocalSaveableStateRegistry provides null) { capsa = counter(config) }
        }
        settle(host)

        checkNotNull(capsa).send(Increment)
        settle(host)

        assertEquals(1, checkNotNull(capsa).count.value)
        assertEquals(emptyList(), logger.errors)
    }

    @Test
    fun theScreenIsVisibleWhileItCollectsAndHiddenAfterTheGrace() = runTest {
        val config = config()
        val owner = TestOwner(Lifecycle.State.STARTED)
        val host = compose {
            val count by counter(config).count.collectAsStateWithLifecycle(lifecycleOwner = owner)
            count
        }
        settle(host)
        assertEquals(listOf("work start", "visible start"), log)

        owner.lifecycle.currentState = Lifecycle.State.CREATED
        settle(host)

        assertEquals(listOf("work start", "visible start", "visible stop"), log)
    }

    @Test
    fun anObjectWorksWithoutCompose() = runTest {
        val config = config()
        val scope = CoroutineScope(backgroundScope.coroutineContext + StandardTestDispatcher(testScheduler))
        val counter = Counter(CapsaScope(scope, CapsaLogger { _, _, _ -> }, config), log, tracked)

        counter.send(Increment)
        testScheduler.runCurrent()

        assertEquals(1, counter.count.value)
    }

    @Test
    fun aLambdaExposesItsStateTheScreenGetsItsEffectsAndTheLambdaTheServiceOnes() = runTest {
        val config = config()
        val handled = mutableListOf<Effect>()
        var capsa: CapsaHandle<StateFlow<Int>>? = null
        val host = compose {
            val handle = counterHandle(config).also { capsa = it }
            CompositionLocalProvider(LocalLifecycleOwner provides TestOwner(Lifecycle.State.STARTED)) {
                CollectEffects(handle.effects, effectHandlers { on<Pong> { handled += it } })
            }
        }
        settle(host)

        val handle = checkNotNull(capsa)
        handle.send(Increment)
        handle.send(Ping)
        settle(host)

        assertEquals(1, handle.state.value)
        assertEquals(listOf<Effect>(Pong), handled)
        assertEquals(listOf<Effect>(Tracked), tracked)
        assertEquals(listOf("work start", "visible start"), log)
    }

    @Test
    fun aLambdaKeepsItsSlicesThroughTheDeathOfTheProcessAndStopsWithTheScreen() = runTest {
        val config = config()
        val before = SaveableStateRegistry(restoredValues = null) { true }
        var registry = before
        val shown = mutableStateOf(true)
        var latest: CapsaHandle<StateFlow<Int>>? = null
        val content: @Composable () -> Unit = {
            CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {
                if (shown.value) latest = counterHandle(config)
            }
        }
        val host = compose(content)
        settle(host)
        checkNotNull(latest).send(Increment)
        settle(host)

        registry = SaveableStateRegistry(restoredValues = before.performSave()) { true }
        settle(compose(content))
        assertEquals(1, checkNotNull(latest).state.value)

        shown.value = false
        settle(host)
        assertEquals(listOf("work start", "work start", "work stop"), log)
    }

    // The call fails before it returns anything.
    @Suppress("IgnoredReturnValue")
    @Test
    fun aLambdaThatInstallsAndReturnsAClassFailsAtOnce() = runTest {
        val config = config()
        val error = assertFailsWith<IllegalStateException> {
            compose {
                rememberCapsa(logger = logger, config = config) {
                    install(capability("extra", 0) {})
                    Counter(this, log, tracked)
                }
            }
        }
        assertContains(error.message.orEmpty(), "expose()")
    }

    // The call fails before it returns anything.
    @Suppress("IgnoredReturnValue")
    @Test
    fun aLambdaThatReturnsNeitherAClassNorExposeFailsAtOnce() = runTest {
        val config = config()
        val error = assertFailsWith<IllegalStateException> {
            compose {
                rememberCapsa(logger = logger, config = config) { install(counting(log)) }
            }
        }
        assertContains(error.message.orEmpty(), "expose()")
    }

    @Test
    fun aFailingServiceHandlerOfAClassGoesToTheErrorPolicyAndTheRuntimeGoesOn() = runTest {
        val config = config()
        var capsa: FailingCounter? = null
        val host = compose {
            capsa = rememberCapsa(logger = logger, config = config) { FailingCounter(this, log, toSignal = false) }
        }
        settle(host)

        val counter = checkNotNull(capsa)
        counter.send(Ping)
        counter.send(Increment)
        settle(host)

        assertEquals(1, counter.count.value)
        val error = logger.errors.single()
        assertContains(error?.message.orEmpty(), "failed in the CapsaComposable")
        assertEquals(ANALYTICS_DOWN, error?.cause?.message)
    }

    @Test
    fun aClassTurnsAFailingServiceHandlerIntoASignal() = runTest {
        val config = config()
        var capsa: FailingCounter? = null
        val host = compose {
            capsa = rememberCapsa(logger = logger, config = config) { FailingCounter(this, log, toSignal = true) }
        }
        settle(host)

        checkNotNull(capsa).send(Ping)
        settle(host)

        assertEquals(FAILED, checkNotNull(capsa).count.value)
        assertEquals(emptyList(), logger.errors)
    }

    @Test
    fun aFailingServiceHandlerOfALambdaGoesToTheErrorPolicyAndTheRuntimeGoesOn() = runTest {
        val config = config()
        var capsa: CapsaHandle<StateFlow<Int>>? = null
        val host = compose {
            capsa = rememberCapsa(logger = logger, config = config) {
                val count = install(counting(log))
                handleEffects { on<Tracked> { error(ANALYTICS_DOWN) } }
                expose(count)
            }
        }
        settle(host)

        val handle = checkNotNull(capsa)
        handle.send(Ping)
        handle.send(Increment)
        settle(host)

        assertEquals(1, handle.state.value)
        val error = logger.errors.single()
        assertContains(error?.message.orEmpty(), "failed in rememberCapsa")
        assertEquals(ANALYTICS_DOWN, error?.cause?.message)
    }

    @Test
    fun aLambdaHandlesTheFailureOfItsServiceHandlerItself() = runTest {
        val config = config()
        val failures = mutableListOf<Pair<Effect, String?>>()
        var capsa: CapsaHandle<StateFlow<Int>>? = null
        val host = compose {
            capsa = rememberCapsa(logger = logger, config = config) {
                val count = install(counting(log))
                handleEffects { on<Tracked> { error(ANALYTICS_DOWN) } }
                onEffectFailed { effect, error -> failures += effect to error.message }
                expose(count)
            }
        }
        settle(host)

        checkNotNull(capsa).send(Ping)
        settle(host)

        assertEquals(listOf<Pair<Effect, String?>>(Tracked to ANALYTICS_DOWN), failures)
        assertEquals(emptyList(), logger.errors)
    }

    @Test
    fun aHandlePassesAnOutsideSignalToTheRuntime() = runTest {
        val config = config()
        var capsa: CapsaHandle<StateFlow<Int>>? = null
        val host = compose { capsa = counterHandle(config) }
        settle(host)

        checkNotNull(capsa).signal(Failed)
        settle(host)

        assertEquals(FAILED, checkNotNull(capsa).state.value)
    }

    @Test
    fun aSnapshotWithoutTheKeyOfACapabilityStartsItFromTheInitialState() = runTest {
        val config = config()
        val before = SaveableStateRegistry(restoredValues = null) { true }
        var registry = before
        // A new version of the app saves the slice under another key, and the snapshot of the old one has no such key.
        var key = "count"
        var latest: CapsaHandle<StateFlow<Int>>? = null
        val content: @Composable () -> Unit = {
            CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {
                latest = rememberCapsa(logger = logger, config = config) { expose(installSaved(counting(log), CountSaver, key = key)) }
            }
        }
        val host = compose(content)
        settle(host)
        checkNotNull(latest).send(Increment)
        settle(host)

        registry = SaveableStateRegistry(restoredValues = before.performSave()) { true }
        key = "renamed"
        settle(compose(content))

        assertEquals(0, checkNotNull(latest).state.value)
        assertEquals(emptyList(), logger.errors)
    }

    @Test
    fun aScreenWithoutSavedSlicesLeavesNothingInTheRegistry() = runTest {
        val config = config()
        val registry = SaveableStateRegistry(restoredValues = null) { true }
        var capsa: CapsaHandle<StateFlow<Int>>? = null
        val host = compose {
            CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {
                capsa = rememberCapsa(logger = logger, config = config) { expose(install(counting(log))) }
            }
        }
        settle(host)
        checkNotNull(capsa).send(Increment)
        settle(host)

        assertEquals(emptyMap(), registry.performSave())
    }

    @Test
    fun aCompositionThatFailsAfterCreatingTheObjectStopsIt() = runTest {
        val config = config()
        assertFailsWith<IllegalStateException> {
            compose {
                counter(config)
                error("the screen failed")
            }
        }
        testScheduler.advanceUntilIdle()

        assertEquals(1, created.size)
        assertEquals(emptyList(), log)
    }

    @Test
    fun aScopeWithTheDefaultConfigWritesTheJournalToItsLogger() = runTest {
        config()
        val journal = mutableListOf<String>()
        val scope = CoroutineScope(backgroundScope.coroutineContext + StandardTestDispatcher(testScheduler))
        val capsaScope = CapsaScope(scope, CapsaLogger { level, _, message -> if (level == CapsaLogger.Level.Debug) journal += message() })
        val handle = capsaScope.expose(capsaScope.install(capability("plain", 0) { on<Increment> { state, _ -> (state + 1).commit() } }))

        handle.send(Increment)
        testScheduler.runCurrent()

        assertEquals(1, handle.state.value)
        assertContains(journal, "Intent Increment")
        assertContains(journal, "  plain: state 1")
    }

    // The call fails before it returns anything.
    @Suppress("IgnoredReturnValue")
    @Test
    fun aLambdaThatInstallsAfterCreatingAClassFailsAtOnce() = runTest {
        val config = config()
        val error = assertFailsWith<IllegalStateException> {
            compose {
                rememberCapsa(logger = logger, config = config) {
                    Counter(this, log, tracked).also { install(capability("extra", 0) {}) }
                }
            }
        }
        assertContains(error.message.orEmpty(), "install() on the scope of a CapsaComposable")
    }

    // The call fails before it returns anything.
    @Suppress("IgnoredReturnValue")
    @Test
    fun oneScopeServesOneClass() = runTest {
        val config = config()
        val error = assertFailsWith<IllegalStateException> {
            compose {
                rememberCapsa(logger = logger, config = config) {
                    Counter(this, log, tracked)
                    Counter(this, log, tracked)
                }
            }
        }
        assertContains(error.message.orEmpty(), "serves one CapsaComposable")
    }

    private companion object {
        const val ANALYTICS_DOWN = "analytics is down"
        const val FAILED = -1
        val WORK = WorkKey("work")
        val VISIBLE = WorkKey("visible")

        fun counting(log: MutableList<String>) = capability("count", 0) {
            on<Started> { state, _ ->
                state.commit(
                    works = listOf(
                        stream(WORK, onError = { Failed }) { recorded(log, "work") },
                        stream(VISIBLE, onError = { Failed }, whileVisible = true) { recorded(log, "visible") },
                    ),
                )
            }
            on<Increment> { state, _ -> (state + 1).commit() }
            on<Ping> { state, _ -> state.commit(effects = listOf(Pong, Tracked)) }
            on<Failed> { _, _ -> FAILED.commit() }
        }

        fun recorded(log: MutableList<String>, name: String) = flow<Signal> { awaitCancellation() }
            .onStart { log += "$name start" }
            .onCompletion { log += "$name stop" }
    }
}
