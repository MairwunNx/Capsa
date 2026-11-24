# Kotlin

The Kotlin Multiplatform implementation of Capsa. Read this file when the project builds with Gradle and applies the Kotlin Multiplatform plugin (`org.jetbrains.kotlin.multiplatform`) or an Android plugin (`com.android.application`, `com.android.library`, `com.android.kotlin.multiplatform.library`).

## Modules and coordinates

The group is `com.mairwunnx.capsa`. All modules share one version. If the project already has Capsa, a release or a local build, use the version it pins and don’t change it on your own. Only when you add Capsa to a project for the first time, take the latest release of `capsa-bom`: `<release>` in `https://repo1.maven.org/maven2/com/mairwunnx/capsa/capsa-bom/maven-metadata.xml`, or the latest release of `MairwunNx/Capsa` on GitHub. Never guess the version.

| Artifact | Package | What it holds | Depends on |
|---|---|---|---|
| `capsa-core` | `com.mairwunnx.capsa` | The runtime: capabilities, facts, works, effects, projections | kotlinx-coroutines |
| `capsa-host` | `com.mairwunnx.capsa` | What every host shares: `Capsa.failFast`, `CapsaLogger`, `LoggingCapsaObserver`, `SliceSaver`, `CapsaEffects`, the foreground of the app | `capsa-core`, AndroidX savedstate, kotlinx-serialization |
| `capsa-viewmodel` | `com.mairwunnx.capsa.viewmodel` | `CapsaViewModel`, the host on AndroidX `ViewModel` | `capsa-host`, AndroidX lifecycle |
| `capsa-compose` | `com.mairwunnx.capsa.compose` | `rememberCapsa` and `CapsaComposable`, the host right in the composition, plus `CollectEffects` and `Slot` | `capsa-host`, Compose runtime and UI |
| `capsa-test` | `com.mairwunnx.capsa.test` | `capsaHarness`, `replay`, `inCapabilityScope` | `capsa-core`, kotlinx-coroutines-test |
| `capsa-bom` | | Aligns the versions of the modules above | |

Targets: Android, `iosArm64`, `iosSimulatorArm64`, `macosArm64`, and the JVM. Capsa builds with Kotlin 2.4, where context parameters are stable, so the project needs Kotlin 2.4 or newer.

Capsa has no `js` and no `wasmJs` targets. A module that also builds for the web can’t take Capsa in `commonMain`: its dependencies and code go into a shared source set of the supported targets. Don’t drop the web targets on your own.

```kotlin
// build.gradle.kts of a module with js or wasmJs
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

kotlin {
    // Every target but the web: Android, iOS, macOS, and the JVM.
    applyDefaultHierarchyTemplate {
        common {
            group("capsa") {
                withCompilations { it.platformType != KotlinPlatformType.js && it.platformType != KotlinPlatformType.wasm }
            }
        }
    }
    sourceSets {
        getByName("capsaMain").dependencies { implementation(libs.capsa.compose) }
        getByName("capsaTest").dependencies { implementation(libs.capsa.test) }
    }
}
```

Screens on Capsa live in `src/capsaMain`. The web app keeps its own screens in `commonMain` or `webMain`.

A typical Compose Multiplatform screen module:

```toml
# gradle/libs.versions.toml
[versions]
capsa = "<version>"

[libraries]
capsa-bom = { module = "com.mairwunnx.capsa:capsa-bom", version.ref = "capsa" }
capsa-core = { module = "com.mairwunnx.capsa:capsa-core", version.ref = "capsa" }
capsa-viewmodel = { module = "com.mairwunnx.capsa:capsa-viewmodel", version.ref = "capsa" }
capsa-compose = { module = "com.mairwunnx.capsa:capsa-compose", version.ref = "capsa" }
capsa-test = { module = "com.mairwunnx.capsa:capsa-test", version.ref = "capsa" }
```

```kotlin
// build.gradle.kts of the screen module
plugins {
    // Only if the screen saves slices with installSaved: snapshots are @Serializable.
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.capsa.compose) // brings capsa-host and capsa-core
            implementation(libs.capsa.viewmodel) // only for screens on CapsaViewModel
        }
        commonTest.dependencies {
            implementation(libs.capsa.test)
        }
    }
}
```

A module with capabilities only (`business`) needs just `capsa-core`. A screen on `rememberCapsa` needs only `capsa-compose`, a screen on `CapsaViewModel` adds `capsa-viewmodel`, and a host without Compose, such as SwiftUI, needs just `capsa-viewmodel`. The two hosts don’t depend on each other. On Android, `capsa-core` ships R8 rules that keep the names of facts and effects, so crash reports stay readable.

On the JVM desktop, `capsa-compose` brings `kotlinx-coroutines-swing` at runtime, so `Dispatchers.Main` is the Swing event thread. `capsa-viewmodel` alone doesn’t: without a main dispatcher, decisions run on `Dispatchers.Default`, so add `kotlinx-coroutines-swing` to a desktop app without Compose.

## Facts, slices, capabilities

```kotlin
import com.mairwunnx.capsa.*

data class LikeSlice(val liked: Boolean = false, val likes: Int = 0)

sealed interface LikeIntent : Intent               // the intents the capability owns, from the UI
data object LikeClicked : LikeIntent

sealed interface LikeSignal : Signal               // the signals it owns: answers of its works, news for neighbours
data class LikesLoaded(val liked: Boolean, val likes: Int) : LikeSignal
data class LikeSaved(val likes: Int) : LikeSignal
data object LikeFailed : LikeSignal

data object ShowLikeError : Effect                 // for the host

class LikePorts(
    val load: suspend () -> Pair<Boolean, Int>,
    val setLiked: suspend (liked: Boolean) -> Int,
)

private val LOAD = WorkKey("load")
private val SAVE = WorkKey("save")

context(_: CapabilityScope)
fun likes(ports: LikePorts) = capability<LikeSlice, LikeIntent, LikeSignal>(name = "likes", initial = LikeSlice()) {
    val works = object {
        fun load() = work(key = LOAD, onError = { LikeFailed }, policy = WorkPolicy.KeepExisting) {
            val (liked, likes) = ports.load()
            LikesLoaded(liked, likes)
        }
        fun save(liked: Boolean) = work(key = SAVE, onError = { LikeFailed }) { LikeSaved(ports.setLiked(liked)) }
    }

    bootstrap { state -> Decision(state = state, work = works.load()) }

    onIntent<LikeClicked> { state, _ ->
        val liked = !state.liked
        Decision(state = state.copy(liked = liked, likes = state.likes + if (liked) 1 else -1), work = works.save(liked))
    }
    onSignal<LikesLoaded> { state, loaded -> Decision(state = state.copy(liked = loaded.liked, likes = loaded.likes)) }
    onSignal<LikeSaved> { state, saved -> Decision(state = state.copy(likes = saved.likes)) }
    onSignal<LikeFailed> { state, _ ->
        Decision(state = state.copy(liked = !state.liked, likes = state.likes + if (state.liked) -1 else 1), effect = ShowLikeError)
    }
}
```

| API | What it does |
|---|---|
| `Fact`, `Intent`, `Signal` | Marker interfaces. Give the intents and the signals of each capability a sealed interface of their own |
| `Started` | The first fact of the runtime. No capability reacts to it: the runtime runs the bootstraps while it handles `Started` |
| `Effect` | Marker interface for commands to the host. Effects stay untyped |
| `capability<S, I, O>(name, initial) { … }` | Builds a `Capability<S>`. `S` is the slice, `I : Intent` the intents it owns, `O : Signal` the signals it owns. Always state all three, `Nothing` for none. A capability with `O = Nothing` starts no works |
| `bootstrap { state -> Decision(…) }` | The first decision, at most one per capability. Runs once, before any other fact, with the initial or the restored slice |
| `onIntent<F : I> { state, intent -> … }` | A reaction to the capability’s own intents. One owner per intent on the screen: a second capability that handles it, its supertype, or its subtype throws `IllegalArgumentException` at `install` |
| `onSignal<F : Signal> { state, signal -> … }` | A reaction to any signal: answers of its works, a neighbour’s signals, signals from the host. A reaction to `Started` throws: declare `bootstrap` |
| `Decision(state, effect, effects, work, works, cancel, cancels, signal, signals)` | The new slice and what follows. Name every argument, `state =` too. The single items go before the lists, a `null` single item is skipped. `work` takes `Work<O>`, `signal` takes `O`. The runtime applies effects, then cancels, then works, then signals |
| `Decision<S, O>` | The decision type: `state`, `effects`, `works`, `cancels`, `signals` |
| `CapabilityScope` | The context parameter that limits who creates a capability: `CapsaViewModel`, `CapsaComposable`, the lambda of `rememberCapsa`, and `TestCapabilityScope` provide it |
| `Capability<S>` | The interface, for a custom way of dispatching: `name`, `handles`, `intents`, `initial()`, `bootstrap(state)`, `decide(state, fact)` returning `Decision<S, Signal>` |
| `Load<T>`: `Loading`, `Loaded(value)`, `Failed`, `valueOrNull()` | Loaded data in a slice |

## Works

```kotlin
work(key = SEARCH, onError = { SearchFailed }, debounce = 300.milliseconds) { ResultsLoaded(ports.search(query)) }

work(key = SEND, onError = { SendFailed }, policy = WorkPolicy.KeepExisting, retry = Retry(times = 3, delay = 1.seconds, factor = 2.0)) {
    ports.send(form)
    Sent
}

stream(key = TICK, onError = { TimerFailed }, whileVisible = true) { ticks(loaded.deadlineMillis, ports.nowMillis) }
```

The whole timer, with `ticks` and an effect on zero, is the recipe “Timer: a stream that pauses in the background” in `recipes.md`.

| API | Parameters |
|---|---|
| `work(key, onError, policy, debounce, retry) { suspend -> O }` | One run, one signal. The block may make several calls. Returns `Work<O>`: the block and `onError` return the capability’s own signals |
| `stream(key, onError, policy, whileVisible, retry) { Flow<O> }` | A subscription. No debounce parameter |
| `val works = object { fun load() = work(…) }` | Groups the works of a capability at the top of its block, so reactions call `works.load()` |

Inside the `capability { }` block, a local `object` there included, `work` and `stream` are members of the builder, typed to the capability’s `O`. Call them without type arguments and without importing `com.mairwunnx.capsa.work` or `stream`: the member wins, so `stream(key = TICK, onError = { TimerFailed }) { flow { emit(Ticked) } }` compiles. That’s why works belong inside the block. The top-level `work` and `stream` serve code outside it, where the signal type may need an explicit argument: `work<LikeSignal>(…)`.
| `WorkKey(name)` | A value class. Keep it in a `private val` of the capability file |
| `WorkPolicy.Latest`, `KeepExisting`, `Parallel` | `Latest` is the default |
| `Retry(times, delay, factor = 1.0, maxDelay = INFINITE, retryIf = { true })` | The delay before retry `n` is `delay * factor^n`, capped by `maxDelay` |
| `Work<O>.signals()` | The flow of a work with `onError` attached. A test can collect it without the runtime |

## Projections

```kotlin
val ui: StateFlow<PostUi> = combineState(capsaScope, like, comments) { like, comments -> postUi(like, comments, format) }
val likeUi: StateFlow<LikeUi> = like.mapState(capsaScope) { it.toUi() }
```

`mapState` and `combineState` (two to four sources) compute the first value at once and start eagerly by default. A failing transform skips that value and goes to the scope’s error handler. Always pass `capsaScope`. A projection reads its slices past the visibility counter, so its own subscription doesn’t keep the screen visible. Whoever collects the projection counts.

## Host: `CapsaViewModel`

`CapsaViewModel` hosts a screen on the multiplatform AndroidX `ViewModel`, on Android, iOS, and the JVM. It is the `CapabilityScope`, so the capability functions are called inside it.

```kotlin
class PostViewModel(
    likePorts: LikePorts,
    formPorts: FormPorts,
    savedStateHandle: SavedStateHandle,
    private val analytics: Analytics,
) : CapsaViewModel(savedStateHandle = savedStateHandle) {

    private val like = install(likes(likePorts))
    private val form = installSaved(form(formPorts), FormSaver, key = "form")

    val ui: StateFlow<PostUi> = combineState(capsaScope, like, form) { like, form -> postUi(like, form) }

    override fun EffectHandlers.Builder.handleEffects() {
        on<LikeTracked> { analytics.log(it.event) } // service effects stay here, the rest goes to the screen
    }
}
```

| Member | What it does |
|---|---|
| `CapsaViewModel(logger, savedStateHandle, config)` | All optional. `savedStateHandle` is needed only for `installSaved` |
| `install(capability): StateFlow<S>` | Installs a capability. Only while the constructor runs |
| `installSaved(capability, saver, key)` | The same, and keeps a snapshot in `SavedStateHandle` through a `SliceSaver` |
| `send(intent)` | From the UI, any thread |
| `signal(signal)` | Protected: the result of another screen or an outside event, any thread |
| `effects: CapsaEffects` | The effects the view model didn’t take. One consumer |
| `handleEffects()` | Protected: handlers that run in the view model |
| `onEffectFailed(effect, error)` | Protected: override only to turn a failing handler into `signal(...)` |
| `capsaScope` | Protected: the scope for projections, with Capsa’s error policy |
| `setWorkDispatcher(d)`, `resetWorkDispatcher()` | Companion: replace `Dispatchers.Default` for works in tests |

The runtime starts after the constructor, or earlier on the first `send` or access to `effects`. Works run on `Dispatchers.Default`, decisions on the thread of `viewModelScope`.

## Host in Compose: `rememberCapsa`

`rememberCapsa` hosts a screen right in the composition, without a `ViewModel`, on Android, iOS, macOS, and the JVM desktop. Its lambda runs once with `CapsaScope` as the receiver and returns either a class or `expose(...)`.

A class extends `CapsaComposable`. Its body is the same as a `CapsaViewModel`, the constructor takes the `CapsaScope` and whatever the screen needs, here the Android `Application`:

```kotlin
class LikeCapsule(scope: CapsaScope, app: Application) : CapsaComposable(scope) {
    private val likesApi = LikesApi(app)
    private val analytics = Analytics(app)

    private val like = installSaved(likes(LikePorts(likesApi::setLiked)), LikeSaver, key = "like")
    val ui: StateFlow<LikeUi> = like.mapState(capsaScope) { it.toUi() }

    override fun EffectHandlers.Builder.handleEffects() {
        on<LikeTracked> { analytics.log(it.event) }
    }
}

@Composable
fun LikeScreen(onLikeError: () -> Unit) {
    val app = LocalContext.current.applicationContext as Application
    val like = rememberCapsa { LikeCapsule(this, app) }
    val ui by like.ui.collectAsStateWithLifecycle()
    CollectEffects(like.effects, effectHandlers { on<ShowLikeError> { onLikeError() } })
    LikeContent(ui, onIntent = like::send)
}
```

Without a class, the lambda installs itself and ends with `expose`:

```kotlin
val like = rememberCapsa {
    val like = installSaved(likes(likePorts), LikeSaver, key = "like")
    handleEffects { on<LikeTracked> { analytics.log(it.event) } }
    expose(like.mapState(capsaScope) { it.toUi() })
}
val ui by like.state.collectAsStateWithLifecycle()
CollectEffects(like.effects, effectHandlers { on<ShowLikeError> { onLikeError() } })
LikeContent(ui, onIntent = like::send)
```

| API | What it does |
|---|---|
| `rememberCapsa(keys, logger, config) { … }` | Creates the runtime once for this place in the composition. New `keys` create a new one and stop the old one |
| `CapsaComposable(scope)` | The members of `CapsaViewModel`: `install`, `installSaved`, `send`, `signal`, `effects`, `handleEffects()`, `onEffectFailed`, `capsaScope`, `logger` |
| `CapsaScope` | The receiver of the lambda: `install`, `installSaved`, `handleEffects { }`, `onEffectFailed { }`, `capsaScope`, `expose(state)` |
| `CapsaHandle<T>` | What `expose` returns: `state`, `send`, `signal`, `effects` |
| `CapsaScope(scope, logger, config)` | A scope without Compose, for a test: it lives as long as `scope` and saves nothing |

- The lambda returns a `CapsaComposable` or ends with `expose`. A lambda that installs and also returns a class, or returns anything else, throws at once.
- What the lambda reads is fixed when it runs, like `viewModel { }`. Pass the values that should create a new runtime, such as the id of a product, as `keys`.
- The runtime outlives the activity on Android. Pass the `Application`, never the activity or its `Context`.
- A rotation keeps the runtime: `retain` holds it through a configuration change on Android, and the works keep running. iOS, macOS, and the desktop don’t recreate the composition.
- `installSaved` goes to `LocalSaveableStateRegistry`, the store of `rememberSaveable`. Without a registry the capability works and nothing is saved.
- When the screen leaves the composition for good, the runtime and its works stop. A screen on the back stack keeps its runtime only if the navigation wraps the content of each entry in a `RetainedValuesStore`, as a `NavEntryDecorator` over `RetainedValuesStoreRegistry` does in Navigation 3. Otherwise the works stop, and the saved slices come back on return. A `CapsaViewModel` lives as long as its `ViewModelStore`.
- In a test, decisions run on `Dispatchers.Main.immediate`, so set `Dispatchers.setMain(dispatcher)`. Works take `config = CapsaScope.defaultConfig().copy(workContext = dispatcher)`.

## Every host

### When the screen is visible

Nobody reports visibility by hand. The runtime counts the screen as visible while someone collects a slice, a projection, or `effects.flow`, and the app is in the foreground. It pauses `whileVisible` streams otherwise.

- `collectAsStateWithLifecycle()` and `CollectEffects` stop collecting below `STARTED`, so the back stack and the background hide the screen.
- A rotation doesn’t restart the streams: after the last collector leaves, the runtime waits `CapsaVisibility.Watched(grace)`, one second by default.
- Until the first collector the screen is hidden, so a `whileVisible` stream from the `bootstrap` waits for the UI.
- The foreground comes from `ProcessLifecycleOwner` on Android, from the app state on iOS, and on macOS from the windows of the app: ⌘H or all windows minimized to the Dock or closed, while the process keeps running, hide the screen. A window that another app covers doesn’t. The JVM has no foreground layer: Compose Desktop drops a minimized or hidden window to `CREATED`, and the lifecycle-aware collectors stop.
- Inside a host read slices through `.value` or build projections. Your own `stateIn(viewModelScope, SharingStarted.Eagerly)` or `launch { slice.collect {} }` over a slice never stops collecting and keeps the screen visible for good. `SharingStarted.WhileSubscribed` passes the UI collectors through.
- `CapsaConfig(visibility = CapsaVisibility.Host)` turns the counting off: only `CapsaRuntime.onVisibilityChanged` decides, and the screen starts visible.

### Saving slices

```kotlin
@Serializable
data class FormSaved(val name: String, val email: String)

object FormSaver : SliceSaver<FormSlice, FormSaved> {
    override val serializer = FormSaved.serializer()
    override fun save(state: FormSlice) = FormSaved(state.name, state.email)
    override fun restore(saved: FormSaved) = FormSlice(name = saved.name, email = saved.email)
}
```

`save` runs only when the system saves the screen: `CapsaViewModel` keeps the snapshot in `SavedStateHandle`, `rememberCapsa` in the saveable state registry. `null` saves nothing. A broken snapshot goes to the error policy, and the capability starts from its initial slice.

### Errors and logs

- `Capsa.failFast` decides what a caught bug does: crash (`true`) or log and go on (`false`). Capsa detects it: the `debuggable` flag on Android, `Platform.isDebugBinary` on iOS and macOS, `false` on the JVM. The app may set it, for example `Capsa.failFast = BuildConfig.DEBUG` in `Application.onCreate`.
- `CapsaLogger` is a `fun interface` with a lazy message. `CapsaLogger.Default` writes to Logcat, `NSLog`, or `System.err` with the tag `Capsa`: errors always, the runtime journal only while `failFast` is on. Pass your own logger to send errors to a crash reporter.
- `LoggingCapsaObserver` writes the journal: each fact and, indented, what it caused.
- `CapsaViolationException` carries the `CapsaViolation` and the last 32 events of the runtime.
- To change one setting of a host, pass its default with a copy: `CapsaViewModel(config = defaultConfig().copy(visibility = CapsaVisibility.Watched(grace = 2.seconds)))`, or `CapsaScope.defaultConfig().copy(…)` to `rememberCapsa`. A `CapsaConfig(…)` built from scratch drops the journal and the work dispatcher of the host, `CapsaViewModel.setWorkDispatcher` of tests included. A host with a logger of its own passes it: `defaultConfig(logger)`.
- `CapsaConfig(maxSignalDepth = 8, onViolation, workContext, observer, visibility)` and `CapsaObserver` configure the runtime when you build a host of your own. `CapsaObserver.onDecision(capability, fact, decision: Decision<*, *>)` sees every decision, bootstraps included, with `Started` as the fact.

## Screen: `capsa-compose`

```kotlin
@Composable
fun PostScreen(viewModel: PostViewModel, onClose: () -> Unit, onLikeError: () -> Unit) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    CollectEffects(
        viewModel.effects,
        effectHandlers {
            on<CloseForm> { onClose() }
            on<ShowLikeError> { onLikeError() }
        },
    )
    PostContent(ui, onIntent = viewModel::send)
}
```

- `CollectEffects(effects, handlers, lifecycleOwner)` collects effects while the lifecycle is at least `STARTED`, on `Dispatchers.Main.immediate`. Like any collector of the screen, it keeps the screen visible while it collects. A failing handler or an effect without a handler goes to the error policy.
- `effectHandlers(onUnhandled) { on<E> { … } }` builds `EffectHandlers`. By default an unknown effect throws.
- `Flow<Effect>.collectWhileStarted(lifecycleOwner, onEffect)` is the same loop without Compose.
- `Slot<U>` is a typed UI boundary: `Slot<LikeUi> { ui, onIntent, modifier -> … }`. Use it only for a real boundary.

A host without Compose, such as SwiftUI through Kotlin/Native, collects `effects.flow` and calls `effects.fail(error)` when its handler fails. Collect the effects and the state only while the view is on screen, for example in `.task {}`: the collection is what makes the screen visible.

## Tests: `capsa-test`

```kotlin
class LikesTest {

    private fun likesWith(setLiked: suspend (Boolean) -> Int) = inCapabilityScope { likes(LikePorts(load = { false to 0 }, setLiked = setLiked)) }

    @Test
    fun `a click marks the like at once`() {
        val replay = likesWith { 1 }.replay(LikeClicked)
        assertEquals(LikeSlice(liked = true, likes = 1), replay.state)
        assertEquals(1, replay.works.size)
    }

    @Test
    fun `the bootstrap loads the likes`() {
        val replay = likesWith { 1 }.replay(Started)
        assertEquals(1, replay.works.size)
    }

    @Test
    fun `a failed save rolls the like back`() = runTest {
        val likes = likesWith { error("offline") }
        val harness = capsaHarness(likes)
        harness.runCurrent() // the bootstrap loads the likes

        harness.append(LikeClicked)
        harness.runCurrent()

        assertEquals(LikeSlice(liked = false, likes = 0), harness.stateOf(likes))
        assertEquals(listOf(ShowLikeError), harness.takeEffects())
    }
}
```

| API | What it does |
|---|---|
| `inCapabilityScope { … }` | Gives a test the `CapabilityScope` to create a capability |
| `capability.replay(vararg facts, from)` | Feeds facts straight into `decide`, and `Started` into the bootstrap. `state`, `states`, `effects`, `works`, `signals`, `ignored`. Starts no works |
| `TestScope.capsaHarness(vararg capabilities, maxSignalDepth, strict = true)` | A real runtime on virtual time in `backgroundScope`. Sends `Started`, which runs the bootstraps |
| `harness.append(fact)` | Queues a fact. Nothing happens until the clock moves |
| `runCurrent()`, `advanceTimeBy(duration)` | Run what is due now, move the clock and run what became due. The harness has no `advanceUntilIdle()` on purpose |
| `stateOf(capability)`, `effects`, `takeEffects()` | The slice, the effects so far, the effects since the last take |
| `facts`, `workEvents` | Handled facts, `Started` first, and `WorkEvent.Started`, `Cancelled`, `Failed`, `Dropped` |
| `setVisible(visible)` | Hides or shows the screen for `whileVisible` streams. The harness screen starts visible, collectors don’t count in it |
| `violations`, `assertNoViolations()` | With `strict = false`, the violations collected instead of a failure |

A fake clock for a port is the test scheduler: `nowMillis = { testScheduler.currentTime }`.

A test of a `CapsaViewModel` without UI must collect its state, for example `backgroundScope.launch { vm.ui.collect {} }`. Otherwise nobody watches the screen, and `whileVisible` streams never start.

## Build and test commands

Never guess them. Look, in this order:

1. `CLAUDE.md`, `AGENTS.md`, `CONTRIBUTING.md`, or the README of the project.
2. The CI config (`.github/workflows`, `.gitlab-ci.yml`, and the like): the commands it runs on every change.
3. The build itself: `./gradlew tasks --all` or `./gradlew :<module>:tasks`.

The usual Gradle tasks of a Kotlin Multiplatform module are `jvmTest`, `testDebugUnitTest` (Android), `iosSimulatorArm64Test`, `allTests`, and `check`. Linters often run as part of `check` or have their own tasks, such as `detekt`, `ktlintCheck`, or `lint`. Run the narrowest task that covers your change first, then what CI runs.
