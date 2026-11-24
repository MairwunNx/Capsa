package com.mairwunnx.capsa.viewmodel.test

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mairwunnx.capsa.AppForeground
import com.mairwunnx.capsa.viewmodel.CapsaViewModel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.reflect.KClass

/**
 * Creates a [CapsaViewModel] that runs on the virtual time of this test.
 *
 * A view model makes decisions on `Dispatchers.Main` and runs works on its work dispatcher. The function replaces both
 * with a dispatcher of `testScheduler`, so works, debounce, retries, and streams move with `advanceTimeBy` and `runCurrent`.
 * It also keeps the app in the foreground on every platform, so the visibility of the screen depends only on its collectors.
 * When the test ends, the function clears the view model and brings back both dispatchers and the foreground of the platform:
 * the test needs no `@AfterTest`.
 *
 * Like a screen in the background, a view model that nobody collects keeps its `whileVisible` streams paused.
 * Collect what the screen collects with [collectAsScreen]:
 *
 * ```
 * @Test fun theClockTicksWhileTheScreenCollectsIt() = runTest {
 *     val viewModel = capsaViewModel { ClockViewModel() }
 *     collectAsScreen(viewModel.time)
 *     advanceTimeBy(3.seconds)
 *     runCurrent()
 *     assertEquals("00:03", viewModel.time.value)
 * }
 * ```
 *
 * Use one view model per test: a second call replaces the dispatchers of the first.
 *
 * @param create creates the view model. It runs after the dispatchers are replaced
 * @return the view model, held in a `ViewModelStore` until the test ends
 */
@OptIn(ExperimentalCoroutinesApi::class)
public fun <VM : CapsaViewModel> TestScope.capsaViewModel(create: () -> VM): VM {
    val dispatcher = StandardTestDispatcher(testScheduler)
    Dispatchers.setMain(dispatcher)
    CapsaViewModel.setWorkDispatcher(dispatcher)
    AppForeground.replace(null)

    val viewModel = create()
    val store = ViewModelStore()
    store.hold(viewModel)
    backgroundScope.coroutineContext.job.invokeOnCompletion {
        store.clear()
        AppForeground.reset()
        CapsaViewModel.resetWorkDispatcher()
        Dispatchers.resetMain()
    }
    return viewModel
}

/**
 * Collects `flow` until the test ends, as a shown screen collects it.
 *
 * The runtime counts the screen as visible while someone collects its slices, projections, or `effects.flow`.
 * The collection starts at once. Cancel the returned job to hide the screen: after the grace of the view model,
 * one second by default, the runtime pauses its `whileVisible` streams.
 *
 * @param flow the state, a projection, or `effects.flow` of a view model
 * @return the job of the collection
 */
public fun TestScope.collectAsScreen(flow: Flow<*>): Job = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { flow.collect {} }

/** Puts `viewModel` into the store, so `clear()` clears it as the platform would. */
private fun <VM : CapsaViewModel> ViewModelStore.hold(viewModel: VM) {
    @Suppress("UNCHECKED_CAST")
    val type = viewModel::class as KClass<VM>
    ViewModelProvider.create(this, viewModelFactory { addInitializer(type) { viewModel } })[type]
}
