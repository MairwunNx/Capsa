# Pitfalls

Mistakes that compile and then hurt. Each entry says what goes wrong, why, and what to do. Kotlin names are used where they make the point clearer, `kotlin.md` has the rest.

## Tests

### `advanceUntilIdle()` doesn’t run the runtime

The test harness runs the runtime in the test’s background scope, so `runTest` doesn’t wait for it forever. `advanceUntilIdle()` of `runTest` stops as soon as only background tasks are left, and the whole runtime is a background task. After it the runtime hasn’t handled even `Started`: no fact, no work, no debounce, no timer. That’s why the harness has no `advanceUntilIdle()` of its own.

Use `harness.runCurrent()` to handle what is due right now, such as queued facts and works without delays. Use `harness.advanceTimeBy(300.milliseconds)` to move the clock for debounce, retries, and timers.

### Facts wait for the scheduler

`harness.append(fact)` only queues the fact. Nothing happens until `runCurrent()` or `advanceTimeBy(...)`. A check right after `append` sees the old slice.

### Testing a whole view model

A test of a `CapsaViewModel` on virtual time replaces both dispatchers: `Dispatchers.setMain(dispatcher)` and `CapsaViewModel.setWorkDispatcher(dispatcher)`. Reset both in `@AfterTest` with `Dispatchers.resetMain()` and `CapsaViewModel.resetWorkDispatcher()`, or the next test runs on a dead scheduler. `capsaViewModel { … }` of `capsa-viewmodel-test` does both and resets them when the test ends. With `StandardTestDispatcher` the runtime starts after the constructor, with `UnconfinedTestDispatcher` it may start earlier.

## Reactions and handlers

### A fact that matches two reactions

Reactions in one capability must not overlap. Capsa catches two kinds of overlap at different times:

- A reaction to a supertype and one to its subtype (`onSignal<SearchSignal>` and `onSignal<ResultsLoaded>` where `ResultsLoaded : SearchSignal`) fail when the capability is built, on the JVM and Android. Kotlin/Native can’t compare classes, so there the build only catches two reactions to the same class.
- A fact that implements two unrelated interfaces with a reaction each (`data object Reset : SearchSignal, FilterSignal`) passes the build everywhere. It fails in `decide` the moment it arrives, as `DecideFailed`, and the slice stays as it was.

Give each fact one place in the hierarchy, or handle the supertype once and sort out subtypes with `when`. Effect handlers follow the same rules: an effect that matches two handlers throws when it arrives.

### An intent with two owners

Two capabilities with `onIntent` for the same intent class, or for a supertype and its subtype, fail at `install` with `IllegalArgumentException`. The old habit of letting every capability take its part of a click doesn’t work anymore. Keep the reaction in the capability that owns the scenario, and let it send its own signal that the others handle with `onSignal`. The same holds in tests: install the owner and the neighbour, not two owners.

### Missing type arguments

`capability<S, I, O>` needs all three type arguments. Leave them out, and Kotlin either can’t infer them or infers the wrong ones: a sealed slice narrows to the initial subtype, such as `DownloadSlice.Idle`, and no reaction can move to `Running`. State them: `capability<DownloadSlice, DownloadIntent, DownloadSignal>(name = "download", initial = DownloadSlice.Idle) { … }`.

### A neighbour’s signal in a decision

`signal = HeaderRefreshed` in the like capability doesn’t compile when `HeaderRefreshed` belongs to the header: a capability sends only its own signals. Don’t widen `O` to `Signal` to get around it. Send a signal of your own, and let the header react to it.

### A reaction to `Started`

`onSignal<Started>` throws when the capability is built, and so does a second `bootstrap`. Put the initial load and the subscriptions into one `bootstrap`.

### A fact without a readable `toString()`

A plain `class` or `object` prints as `a.b@1f2e` in a release crash report. In a debug build, the journal of `CapsaViewModel` (`LoggingCapsaObserver`) throws on such a fact. Make facts and effects `data class` or `data object`.

### A signal nobody handles

A signal from a decision that no capability handles is an `UnhandledFact` violation. When you add a signal for a neighbour, add the neighbour’s reaction in the same change.

### A chain of signals that loops

A capability that answers a signal with a signal that leads back to it loops. The runtime stops the chain at depth 8 with `SignalCycle`. Break the loop: one of the reactions must return a decision without a signal.

### A signal to yourself

A capability that sends a signal only to handle it itself takes an extra pass of the pipeline and brings the chain closer to `SignalCycle`. Change the slice in the same reaction. Forwarding the answer of a work as a new signal is the same mistake: the answer already reaches every capability that handles it.

## Works

### `KeepExisting` against `Latest`

- `KeepExisting` ignores a new start while the old run is active, even when the new start carries other parameters. A reload with a new filter under `KeepExisting` keeps showing the old filter’s answer. Use it for an initial load and for a submit button, not for anything that depends on input.
- `Latest` cancels the old run and drops its queued answer. Use it for search, refresh, and anything where the newest request wins. It’s the default.
- `Parallel` runs every start. A `cancel` by its key stops all of them at once.
- `KeepExisting` also ignores a start while an earlier run with the same key waits for the screen to come back (`whileVisible`).

### `whileVisible` and the current value

When the screen comes back, the runtime calls the stream’s block again. A stream that counts from the moment of subscription (“30 seconds from now”) restarts the countdown after every pause. Count from a fixed point instead: the deadline minus the current time, read through a port. A stream that has already ended doesn’t start again. The timer recipe in `recipes.md` shows both cases and an effect on zero that leaves once.

### Who reports visibility

Nobody calls anything: the runtime counts who collects the screen. A slice, a projection, or `effects.flow` with a collector makes the screen visible, and the app in the background hides it. Three things break this:

- **`collectAsState()` without a lifecycle.** The composition stays alive below `STARTED` and keeps collecting, for example while another activity of the app covers the screen, so the streams keep going. The foreground layer catches only the background of the whole app. Use `collectAsStateWithLifecycle()`.
- **A collector in the view model.** `stateIn(viewModelScope, SharingStarted.Eagerly)` or `launch { slice.collect {} }` over a slice never stops collecting, so the screen stays visible for good. Read `.value` or build projections with `mapState` and `combineState`: they don’t count.
- **A view model test without UI.** Nobody collects, so `whileVisible` streams never start. Collect the state with `collectAsScreen(…)` of `capsa-viewmodel-test` or in `backgroundScope`, or test the capability with `capsaHarness`, where the screen starts visible.

A short read such as `slice.first()` counts too: the screen turns visible and stays so for the grace, one second by default.

### Debounce belongs to `work`

`work(…, debounce = …)` waits before the call. `stream` has no debounce: debounce the upstream flow inside the block if you need it. A manual `delay` at the start of a `work` block also debounces, but it hides the intent and the runtime can’t show it in the journal.

### Retries in a stream share one counter

`Retry(times = 3)` counts attempts across the whole life of a `stream`. A subscription that emitted values and then failed has fewer attempts left.

### A foreign `CancellationException` is an error

A `CancellationException` thrown while the runtime’s scope is alive, for example from a cancelled `Deferred`, reaches `onError` like any other error. Don’t rely on it to stop a work silently. Cancel by the key instead.

## Projections and the host

### Projections on the wrong scope

`mapState` and `combineState` report a failing transform to the error handler of their scope. On `viewModelScope` or your own scope that crashes a release build. Pass `capsaScope` of the host (`CapsaViewModel`, `CapsaComposable`, or `CapsaScope`): it crashes only a debug build.

### A projection that fails on the initial slice

`mapState` and `combineState` compute the first value at once, in the constructor of the host, outside the error handler. A transform that fails on the initial slice crashes the constructor in every build. Make projections total: handle empty lists, `Load.Loading`, and `null`.

### `install` after the runtime started

`install` and `installSaved` work only while the host is being built: the constructor of a view model or a `CapsaComposable`, or the lambda of `rememberCapsa`. A `send`, a `signal`, or an access to `effects` inside `init` starts the runtime early, and a later `install` throws. Install everything first.

### Effects have one consumer

`effects` is a channel with one consumer. Two collectors split the effects between them, and each sees only some. Collect once per screen. Effects that arrive while nobody collects wait in the buffer, so a navigation effect from the background runs when the screen comes back.

### Effect handlers are synchronous

A handler isn’t `suspend`, and the next effect waits until it returns. For async work in a handler, launch a coroutine there, or better, describe the work in the capability.

### The bootstrap runs once

A rotation, a return from the back stack, or a new composition doesn’t run the `bootstrap` again. Refresh on return through an intent from the UI or a `whileVisible` stream, not through the bootstrap.

A restore after the process died does run it, with the restored slice. A bootstrap that always starts the full load throws away what the slice already holds. Check the slice: `work = works.load().takeIf { state.items is Load.Loading }`.

### A `rememberCapsa` screen that loses its runtime on the back stack

Without a `RetainedValuesStore` per navigation entry, a screen on the back stack leaves the composition, and `rememberCapsa` stops its runtime: works stop, and only the slices of `installSaved` come back on return. Either wrap each entry in a store of its own, as a `NavEntryDecorator` over `RetainedValuesStoreRegistry` does in Navigation 3, or keep that screen on `CapsaViewModel`.

### The activity inside `rememberCapsa`

The runtime of `rememberCapsa` outlives the activity on Android. A `LocalContext.current` passed to a `CapsaComposable` or captured by the lambda leaks the old activity after a rotation. Pass `applicationContext` or the `Application`.
