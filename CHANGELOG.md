# Changelog

The “Release” workflow takes the notes for a version from its section here, unless you type them into its `changelog` field.

## 0.99.0

The first preview of Capsa. The API may still change before `1.0.0`: try it on a screen and tell me what gets in your way.

- 🧩 **A screen of small capabilities.** Each capability owns one scenario of the screen, a like or a search, with its own slice of state. It is a pure function from a slice and a fact to a decision, so it knows nothing of the UI, the host, or its neighbours.
- 📦 **A decision is one value.** A reaction returns everything it decided at once: `Decision(state = …, effect = …, work = …, cancel = …, signal = …)`. Nothing happens halfway through a reaction, and the names of the arguments tell what it does.
- 🏷️ **Ownership the compiler checks.** `capability<Slice, Intent, Signal>` names what a capability owns. It reacts to its own intents with `onIntent`, hears any signal with `onSignal`, and sends only its own signals, while each intent has one owner on the screen.
- 🚀 **A bootstrap for the start.** `bootstrap { state -> … }` starts the initial load and the subscriptions once, before any other fact, and gets the restored slice after the process died.
- ⚙️ **Async work as data.** A capability returns the works it wants, and the runtime starts them, cancels them, and drops the late answer of a cancelled one. `WorkPolicy` decides what a second start with the same key does: `Latest` restarts it, `KeepExisting` keeps the running one, `Parallel` runs both.
- ⏱️ **Debounce and retry in one argument.** `debounce = 300.milliseconds` waits for a pause in typing, and each new letter cancels the wait. `Retry(times, delay, factor)` repeats a failed call with a backoff, and `onError` turns the last failure into state.
- 👀 **Streams that pause off screen.** A `stream` with `whileVisible = true` stops while nobody watches the screen or the app sits in the background, and starts again when the screen comes back. Nobody reports visibility by hand: the runtime counts the collectors of the screen.
- 🚪 **Side effects through one door.** Navigation, snackbars, and analytics leave a capability as effects, and the host performs them. `CollectEffects` handles them in Compose while the screen is started.
- 🖼️ **Projections for the UI.** `mapState` and `combineState` turn slices into a UI model with formatted text and resources, and the slices stay plain domain data.
- 🛟 **Crashes in debug, crash-free in release.** A broken contract, such as a fact nobody handles or a loop of signals, crashes a debug build at once. A release build logs it, keeps the previous slice, and the screen lives on. `Capsa.failFast` detects the build on its own, and `CapsaLogger` sends the errors wherever you like.
- 📜 **The history in every crash report.** A violation carries the last 32 events of the runtime: which facts came, what they started, what failed. R8 keeps the names of facts and effects on Android, so the report stays readable.
- 💾 **State that survives the process.** A `SliceSaver` with kotlinx.serialization keeps the part of a slice you choose when the system saves the screen.
- 🧪 **A harness for tests.** `replay` feeds facts straight into `decide`, and `capsaHarness` runs the real runtime on virtual time: you move the clock and read slices, effects, and works.
- 🧪 **View models on virtual time.** `capsaViewModel { … }` from `capsa-viewmodel-test` replaces the dispatchers for a test and resets them when it ends, and `collectAsScreen` shows the screen to the runtime.
- 🔍 **Rules for detekt.** `capsa-detekt` reports a fact without `data`, a `Decision` without argument names, an eager `stateIn` that keeps the screen visible, and `advanceUntilIdle` next to the harness. It needs detekt 2.
- 📡 **Your observer next to the journal.** `CapsaObserver.of` passes every event to several observers, so your metrics don’t replace the log of the host.
- 📱 **Compose Multiplatform on Android and iOS.** Pick a host: `CapsaViewModel` on AndroidX `ViewModel`, which SwiftUI can use too, or `rememberCapsa` right in the composition. Capsa runs on Android, iOS, macOS, and the JVM, and `capsa-bom` keeps the versions of the modules in line.
- 🤖 **A plugin for coding agents.** The `capsa` skill teaches an agent the rules, and `capsa-setup`, `capsa-migrate`, `capsa-new`, and `capsa-review` add Capsa to a project, move a screen to it, write a new screen or capability, and review code against the rules. The skills follow the Agent Skills standard, so agents beyond Claude Code read them too.
- ⬆️ **Fresh dependencies.** Builds on Android Gradle Plugin 9.5.2.
