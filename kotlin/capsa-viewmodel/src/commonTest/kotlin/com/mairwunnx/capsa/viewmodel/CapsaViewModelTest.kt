package com.mairwunnx.capsa.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.mairwunnx.capsa.Capability
import com.mairwunnx.capsa.Capsa
import com.mairwunnx.capsa.CapsaLogger
import com.mairwunnx.capsa.CapsaViolationException
import com.mairwunnx.capsa.Effect
import com.mairwunnx.capsa.EffectHandlers
import com.mairwunnx.capsa.Intent
import com.mairwunnx.capsa.SliceSaver
import com.mairwunnx.capsa.Started
import com.mairwunnx.capsa.capability
import com.mairwunnx.capsa.commit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class CapsaViewModelTest {

    private data class Counter(val value: Int = 0, val started: Boolean = false)
    private data object Increment : Intent
    private data object Explode : Intent
    private data class Reached(val value: Int) : Effect

    private val counter = capability("counter", Counter()) {
        on<Started> { state, _ -> state.copy(started = true).commit() }
        on<Increment> { state, _ ->
            val next = state.copy(value = state.value + 1)
            next.commit(effects = if (next.value == 2) listOf(Reached(2)) else emptyList())
        }
    }

    private data class CounterSaved(val value: Int)

    /** Written by hand, so the tests don’t need the serialization compiler plugin. */
    private object CounterSavedSerializer : KSerializer<CounterSaved> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("CounterSaved") { element<Int>("value") }

        override fun serialize(encoder: Encoder, value: CounterSaved) {
            encoder.encodeStructure(descriptor) { encodeIntElement(descriptor, 0, value.value) }
        }

        override fun deserialize(decoder: Decoder): CounterSaved = decoder.decodeStructure(descriptor) {
            var value = 0
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> value = decodeIntElement(descriptor, 0)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index $index")
                }
            }
            CounterSaved(value)
        }
    }

    private object CounterSaver : SliceSaver<Counter, CounterSaved> {
        override val serializer: KSerializer<CounterSaved> = CounterSavedSerializer
        override fun save(state: Counter): CounterSaved? = CounterSaved(state.value).takeIf { state.value > 0 }
        override fun restore(saved: CounterSaved): Counter = Counter(value = saved.value)
    }

    private val logger = RecordingLogger()
    private var failFastBefore = Capsa.failFast

    private class TestViewModel(
        logger: CapsaLogger,
        handle: SavedStateHandle,
        capability: Capability<Counter>,
        saver: SliceSaver<Counter, CounterSaved>,
        private val handled: MutableList<Effect> = mutableListOf(),
        private val failOnHandled: Boolean = false,
    ) : CapsaViewModel(logger, handle) {
        val failures = mutableListOf<Pair<Effect, Exception>>()

        val counter: StateFlow<Counter> = installSaved(capability, saver, key = KEY)

        override fun EffectHandlers.Builder.handleEffects() {
            on<Handled> {
                handled += it
                if (failOnHandled) error("handler failed")
            }
        }

        override suspend fun onEffectFailed(effect: Effect, error: Exception) {
            failures += effect to error
        }
    }

    private data class Handled(val value: Int) : Effect

    /** Doesn’t override `onEffectFailed`, so a failing handler goes through the Capsa error policy. */
    private class DefaultPolicyViewModel(logger: CapsaLogger, capability: Capability<Counter>) : CapsaViewModel(logger) {
        init {
            @Suppress("IgnoredReturnValue")
            install(capability)
        }

        override fun EffectHandlers.Builder.handleEffects() {
            on<Handled> { error("analytics is down") }
        }
    }

    private class LateInstallViewModel(logger: CapsaLogger) : CapsaViewModel(logger) {
        fun installPublic(capability: Capability<Counter>) {
            @Suppress("IgnoredReturnValue")
            install(capability)
        }
    }

    /** Debug builds, where the original tests ran, fail fast. A JVM test process wouldn’t on its own. */
    @BeforeTest
    fun setUp() {
        failFastBefore = Capsa.failFast
        Capsa.failFast = true
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        Capsa.failFast = failFastBefore
    }

    /** Does what the system does when the process dies: saves the handle and builds a new one from the result. */
    private fun SavedStateHandle.afterProcessDeath(): SavedStateHandle = SavedStateHandle.createHandle(savedStateProvider().saveState(), null)

    /** Returns a handle that comes back after process death with the counter at `value`. */
    private fun TestScope.handleSavedAt(value: Int): SavedStateHandle {
        val handle = SavedStateHandle()
        val vm = TestViewModel(logger = RecordingLogger(), handle = handle, capability = counter, saver = CounterSaver)
        repeat(value) { vm.send(Increment) }
        testScheduler.advanceUntilIdle()
        return handle.afterProcessDeath()
    }

    @Test
    fun runtimeStartsOnItsOwnTheIntentChangesTheSliceAndTheEffectIsDelivered() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = TestViewModel(logger = logger, handle = SavedStateHandle(), capability = counter, saver = CounterSaver)

        vm.send(Increment)
        vm.send(Increment)
        testScheduler.advanceUntilIdle()

        assertEquals(Counter(value = 2, started = true), vm.counter.value)
        assertEquals(Reached(2), vm.effects.flow.first())
    }

    @Test
    fun runtimeStartsAfterConstructionEvenIfNobodyTouchesIt() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = TestViewModel(logger = logger, handle = SavedStateHandle(), capability = counter, saver = CounterSaver)

        assertEquals(false, vm.counter.value.started)
        testScheduler.advanceUntilIdle()

        assertEquals(true, vm.counter.value.started)
    }

    @Test
    fun snapshotIsSavedWithTheScreenAndLeftOutWhenThereIsNothingToSave() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val handle = SavedStateHandle()
        val vm = TestViewModel(logger = logger, handle = handle, capability = counter, saver = CounterSaver)
        testScheduler.advanceUntilIdle()

        val untouched = TestViewModel(logger = logger, handle = handle.afterProcessDeath(), capability = counter, saver = CounterSaver)
        assertEquals(Counter(), untouched.counter.value)

        vm.send(Increment)
        testScheduler.advanceUntilIdle()

        val restored = TestViewModel(logger = logger, handle = handle.afterProcessDeath(), capability = counter, saver = CounterSaver)
        assertEquals(Counter(value = 1), restored.counter.value)
    }

    @Test
    fun snapshotIsEncodedOnlyWhenTheSystemSavesTheScreen() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var encoded = 0
        val counting = object : SliceSaver<Counter, CounterSaved> by CounterSaver {
            override val serializer: KSerializer<CounterSaved> = object : KSerializer<CounterSaved> by CounterSavedSerializer {
                override fun serialize(encoder: Encoder, value: CounterSaved) {
                    encoded++
                    CounterSavedSerializer.serialize(encoder, value)
                }
            }
        }
        val handle = SavedStateHandle()
        val vm = TestViewModel(logger = logger, handle = handle, capability = counter, saver = counting)

        repeat(3) { vm.send(Increment) }
        testScheduler.advanceUntilIdle()
        assertEquals(0, encoded)

        handle.savedStateProvider().saveState()
        assertEquals(1, encoded)
    }

    @Test
    fun inReleaseAFailingSaveIsLoggedAndTheOtherKeysAreStillSaved() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        Capsa.failFast = false
        val failing = object : SliceSaver<Counter, CounterSaved> by CounterSaver {
            override fun save(state: Counter): CounterSaved = error("disk full")
        }
        val handle = SavedStateHandle(mapOf("note" to "kept"))
        TestViewModel(logger = logger, handle = handle, capability = counter, saver = failing)
        testScheduler.advanceUntilIdle()

        val reborn = handle.afterProcessDeath()

        assertEquals("kept", reborn.get<String>("note"))
        assertEquals(listOf("disk full"), logger.errors.map { it?.cause?.message })
    }

    @Test
    fun sliceIsRestoredAfterProcessDeath() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val handle = handleSavedAt(5)

        val vm = TestViewModel(logger = logger, handle = handle, capability = counter, saver = CounterSaver)

        assertEquals(Counter(value = 5), vm.counter.value)
        vm.send(Increment)
        testScheduler.advanceUntilIdle()
        assertEquals(Counter(value = 6, started = true), vm.counter.value)
    }

    @Test
    fun installingAfterTheRuntimeHasStartedFailsWithAClearMessage() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = LateInstallViewModel(logger)
        vm.installPublic(counter)
        vm.send(Increment)

        val error = assertFailsWith<IllegalStateException> {
            vm.installPublic(capability("other", Counter()) {})
        }

        assertContains(error.message.orEmpty(), "install() after the runtime started")
    }

    @Test
    fun aFailingHandlerIsLoggedAndTheNextEffectStillArrives() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val handled = mutableListOf<Effect>()
        val failing = capability("failing", Counter()) {
            on<Increment> { state, _ -> state.commit(effects = listOf(Handled(1), Handled(2), Reached(3))) }
        }
        val vm = TestViewModel(
            logger = logger,
            handle = SavedStateHandle(),
            capability = failing,
            saver = CounterSaver,
            handled = handled,
            failOnHandled = true,
        )

        vm.send(Increment)
        testScheduler.advanceUntilIdle()

        assertEquals(listOf<Effect>(Handled(1), Handled(2)), handled)
        assertEquals(listOf<Effect>(Handled(1), Handled(2)), vm.failures.map { it.first })
        assertEquals(Reached(3), vm.effects.flow.first())
    }

    @Test
    fun handledEffectsDoNotReachTheScreen() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val handled = mutableListOf<Effect>()
        val emitting = capability("emitting", Counter()) {
            on<Increment> { state, _ -> state.commit(effects = listOf(Handled(1), Reached(2))) }
        }
        val vm = TestViewModel(
            logger = logger,
            handle = SavedStateHandle(),
            capability = emitting,
            saver = CounterSaver,
            handled = handled,
        )

        vm.send(Increment)
        testScheduler.advanceUntilIdle()

        assertEquals(listOf<Effect>(Handled(1)), handled)
        assertEquals(Reached(2), vm.effects.flow.first())
    }

    @Test
    fun inReleaseAFailingDecideIsLoggedAndTheScreenKeepsWorking() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        Capsa.failFast = false
        val fragile = capability("fragile", Counter()) {
            on<Explode> { _, _ -> error("bug") }
            on<Increment> { state, _ -> state.copy(value = state.value + 1).commit() }
        }
        val vm = TestViewModel(logger = logger, handle = SavedStateHandle(), capability = fragile, saver = CounterSaver)

        vm.send(Explode)
        vm.send(Increment)
        testScheduler.advanceUntilIdle()

        assertEquals(1, vm.counter.value.value)
        assertEquals(1, logger.errors.size)
        assertIs<CapsaViolationException>(logger.errors.single())
    }

    @Test
    fun inReleaseABrokenSnapshotFallsBackToTheInitialSlice() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        Capsa.failFast = false
        val handle = handleSavedAt(5)
        val broken = object : SliceSaver<Counter, CounterSaved> {
            override val serializer: KSerializer<CounterSaved> = CounterSavedSerializer
            override fun save(state: Counter): CounterSaved? = null
            override fun restore(saved: CounterSaved): Counter = error("old format")
        }

        val vm = TestViewModel(
            logger = logger,
            handle = handle,
            capability = counter,
            saver = broken,
        )

        assertEquals(Counter(), vm.counter.value)
        assertEquals(listOf("old format"), logger.errors.map { it?.cause?.message })
    }

    @Test
    fun inReleaseAScreenHandlerFailureIsLoggedAndEffectsKeepComing() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        Capsa.failFast = false
        val vm = TestViewModel(logger = logger, handle = SavedStateHandle(), capability = counter, saver = CounterSaver)

        vm.effects.fail(IllegalStateException("screen handler"))
        vm.send(Increment)
        vm.send(Increment)
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("screen handler"), logger.errors.map { it?.message })
        assertEquals(Reached(2), vm.effects.flow.first())
    }

    @Test
    fun inReleaseAFailingViewModelHandlerGoesToTheLoggerByDefault() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        Capsa.failFast = false
        val emitting = capability("emitting", Counter()) {
            on<Increment> { state, _ -> state.commit(Handled(1)) }
        }
        val vm = DefaultPolicyViewModel(logger, emitting)

        vm.send(Increment)
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("analytics is down"), logger.errors.map { it?.cause?.message })
    }

    /** Its own effect error handler throws, so the error reaches the stream of effects. */
    private class BrokenRecoveryViewModel(logger: CapsaLogger, capability: Capability<Counter>) : CapsaViewModel(logger) {
        init {
            @Suppress("IgnoredReturnValue")
            install(capability)
        }

        override fun EffectHandlers.Builder.handleEffects() {
            on<Handled> { error("analytics is down") }
        }

        override suspend fun onEffectFailed(effect: Effect, error: Exception): Unit = throw IllegalStateException("recovery failed", error)
    }

    @Test
    fun inReleaseALoggerThatFailsOnAnErrorHandsItsErrorToTheCapsaPolicy() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        Capsa.failFast = false
        val picky = object : CapsaLogger {
            val errors = mutableListOf<Throwable?>()

            override fun log(level: CapsaLogger.Level, error: Throwable?, message: () -> String) {
                if (level != CapsaLogger.Level.Error) return
                check(message() != "Capsa: the effect stream stopped") { "logger is down" }
                errors += error
            }
        }
        val emitting = capability("emitting", Counter()) {
            on<Increment> { state, _ -> state.commit(Handled(1)) }
        }
        val vm = BrokenRecoveryViewModel(picky, emitting)

        vm.send(Increment)
        testScheduler.advanceUntilIdle()

        val reported = picky.errors.single()
        assertEquals("logger is down", reported?.message)
        assertEquals(listOf("recovery failed"), reported?.suppressedExceptions?.map { it.message })
    }

    @Test
    fun inReleaseALoggerThatAlwaysFailsDoesNotCrashTheApp() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        Capsa.failFast = false
        var attempts = 0
        val broken = CapsaLogger { level, _, _ ->
            if (level == CapsaLogger.Level.Error) {
                attempts++
                error("logger is down")
            }
        }
        val emitting = capability("emitting", Counter()) {
            on<Increment> { state, _ -> state.commit(Handled(1)) }
        }
        val vm = BrokenRecoveryViewModel(broken, emitting)

        vm.send(Increment)
        testScheduler.advanceUntilIdle()

        // The stream of effects stopped: one attempt to log it, one to report the failure of the logger.
        assertEquals(2, attempts)
    }

    private companion object {
        const val KEY = "counter"
    }
}
