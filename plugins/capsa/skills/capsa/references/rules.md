# Rules

These are the rules of Capability Slice Architecture as Capsa applies them. Capsa follows every rule of CSA, so this file may repeat the spec. When they disagree, the spec wins.

## The five rules of CSA

| # | Rule | What it means |
|---|---|---|
| 1 | **Slice by scenario** | A capability owns a user scenario: a like, a search, a timer. Never a layout block |
| 2 | **The host assembles, it doesn’t think** | The host collects slices, sends intents, and performs effects. It holds no product conditions |
| 3 | **The runtime is the only bus** | A capability never imports another one or reads its slice. They talk through facts |
| 4 | **Side effects live at the edge** | The logic describes navigation, snackbars, and analytics as effects, and the host performs them. The router and the snackbar never get into the logic |
| 5 | **Keep the decider small** | About 150 lines or 12 facts. Past that, split the capability |

## Structure of a screen

```text
<screen>/
  business/                       no UI framework, no navigation, no host
    capabilities/<name>/          <Name>Capability, <Name>Facts, <Name>Slice, <Name>Ports, <Name>Track
      Refreshed.kt                a screen-wide fact, only when several capabilities listen to it
    wiring/                       <Name>PortsWire
  presentation/
    <Screen> host and the screen’s UI
    state/  projection/  effects/
```

Dependencies go one way: `presentation` → `business.wiring` → `business.capabilities`. No import goes back. If the project has an architecture linter, keep these boundaries in it, and fix the structure instead of suppressing a finding.

Don’t create a new module for a screen. Extend the existing one. Don’t move a capability into a shared module until a second screen needs it: the package is already isolated, so a later move is a folder move.

## Capabilities

- A capability is a function that returns it, named by a noun without a suffix: `likes(ports)`, `search(ports)`, not `likesCapability`. Only the host and tests call it.
- `decide` is pure: no I/O, no clock, no randomness, no DI, no coroutines. Time and data arrive as facts or through ports. Everything the world must do leaves as a work or an effect.
- One reaction per fact class. Reactions in one capability don’t overlap: a reaction to a supertype and one to its subtype fail the build. Handle subtypes with `when` inside one reaction. Several capabilities may handle the same fact, each takes what it needs.
- One effect, one work, or one signal goes without a list: `commit(OpenRules)`, `commit(load())`. Lists are for several items or for combining kinds.
- A capability never reads a neighbour’s slice and never imports a neighbour’s capability file. It may listen to signals from a neighbour’s facts file. If it needs a neighbour’s data, the neighbour publishes a signal.
- No state in closures or objects. All state is the slice.
- A sealed slice states its type explicitly, otherwise it narrows to the initial subtype.
- Static input of the screen (arguments, a deep link) is a parameter of the capability’s function, not something it reads inside.

## Facts and effects

- Facts and effects are `data class` or `data object`. Their `toString()` lands in the runtime history and in crash reports.
- Every `Intent` and `Signal` has at least one reaction.
- Don’t add a fact “just in case”. A screen-wide fact like `Refreshed` exists only when at least two capabilities listen to it.
- Analytics are effects (`<Name>Track`). The handler calls the project’s analytics as it is.

## Works

- A work is one job under one key. The key is a private constant in the capability file: `private val SEARCH = WorkKey("search")`.
- The key, the policy, and `onError` cover the whole block: it starts, gets cancelled, and fails as one. The block may call several ports, wait, or do both. Jobs that must start, stop, or fail apart get keys of their own.
- A `work` ends with one signal. Signals over time, such as a subscription or a timer, come from a `stream`.
- Policy: `Latest` by default, `KeepExisting` for an initial load, `Parallel` for independent jobs.
- Every work has its own `onError` that returns a signal, and a reaction turns it into state. If the UI needs the reason, put it into the signal: `onError = { SearchFailed(it.toReason()) }`.
- A cancel or a `Latest` restart drops the queued signals of the old run. Don’t add a request token to signals to guard against stale answers.
- Debounce is a parameter of the work. Don’t write a manual `delay` at the start of the block.
- Retries are a parameter of the work. A retry runs the whole block again, every call in it. Don’t write a retry loop inside the block.
- A call that must not run twice, such as sending a form or a payment, gets a work of its own. Its success signal starts the next work from a reaction, and only the work that is safe to repeat gets `retry`.
- A subscription the back stack doesn’t need (a timer, frequent updates) is a `stream` with `whileVisible`. Its flow must start from the current value.
- Start the initial load and subscriptions in the reaction to `Started`.

## Slices

- Domain data, not ready strings. Formatting, resources, and currencies live in the projection.
- A field that only the UI needs belongs in the projection.
- A slice is an immutable value with defaults, so its initial value is obvious.
- Use `Load<T>` (`Loading`, `Loaded`, `Failed`) for loaded data. It holds no exception, so transport details stay out of the slice.

## Ports and wiring

- A port is a class of lambdas in the capability’s terms: `wallet: suspend () -> Boolean`, not “call the wallet use case”.
- A capability never imports use cases, repositories, or the data layer.
- The wiring builds ports from the project’s real implementations. It knows the data layer but nothing of presentation: no UI framework, no host, no navigation.
- The wiring only translates types and calls. A condition that means something for the product moves into `decide`.
- Orchestration of several sources belongs to a use case in the domain layer. The wiring maps its result to a business type, so the port stays one line.
- A port that repeats a use case one to one adds nothing but independence from a module. Add a port when the use case is foreign or shared, or when the capability may move to another screen.

## Projections

- Pure functions in `presentation/projection`. Formatters arrive as lambdas, so a projection tests without DI.
- Names: `XSlice.toUi(...)` for one slice to one UI model, `xUi(...)` for several sources, `toAnalyticsParams()` and similar for a result that isn’t UI.
- Joining slices happens in a projector in `presentation/state`. It gets the host’s error-reporting scope, so a failing projection doesn’t crash a release build.
- A projection must not fail on the initial slice: the first value is computed at once, when the host is created.
- Combine at most four sources at once. For more, build an intermediate projection.
- Logic stays in pure functions. A projector only wires them up.

## Host

- The host installs capabilities, builds projections, and exposes one UI model. It holds no decisions.
- The screen gets one UI model and sends intents. The result of another screen enters the runtime as a signal through the host.
- Every effect has a handler. Service effects (analytics) may stay in the host, screen effects (navigation) go to the screen.
- A user’s input that must survive process death is saved through a slice saver. Network data isn’t saved: loading it again is cheaper. A broken snapshot doesn’t break the screen, the capability starts from its initial slice.
- Reuse the project’s existing UI components. A slot (a typed UI boundary) is only for a real boundary: a shared host, several UI implementations, or a public module API.

## Between screens

- Runtimes of different screens aren’t linked. Facts of a screen never leave it.
- Screens share data through a common source in the app (a scope of the feature in DI), and a capability sees it as a port. The contract lives in an API module in domain terms, not as a fact of a screen.
- State (“how many points”) is a state flow: a screen that opens later gets the value at once. An event (“the review was sent”) is a shared flow with a buffer: whoever isn’t subscribed misses it. When unsure, use state.
- Publish inside an existing work through a port. Subscribe with a `stream` and `KeepExisting` in the reaction to `Started`, and turn the data into the screen’s own signal.
- Order between screens isn’t guaranteed, and the runtime can’t catch a cycle between them: keep the flow one-way. Such flows don’t survive process death, so the source of truth is a repository.

## Don’t

- Don’t call a use case, a repository, or a platform context from a capability, a projection, or a slice.
- Don’t put DI annotations, UI framework types, navigation, or resources into `business/capabilities`.
- Don’t read a neighbour’s slice through its state flow. Agree through facts.
- Don’t link runtimes of different screens or hand a screen’s facts to another screen.
- Don’t hide logic in the wiring, a projection, or a composable.
- Don’t change behaviour while migrating a screen. List the bugs you find and fix them only after the user decides.
