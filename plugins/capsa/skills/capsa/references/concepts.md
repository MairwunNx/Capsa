# Concepts

Capsa is the Kotlin implementation of Capability Slice Architecture (CSA). This file describes the entities and the flow without platform details. Exact names and signatures live in the platform file, such as `kotlin.md`.

## Entities

### Fact

A fact is something that happened. It lives for one pass of the pipeline: the memory of the screen lives in slices.

- **Intent** comes from the UI: a click, a typed character, an item shown on screen, a measured size. Only the UI creates intents. Each intent has one owner: one capability on the screen reacts to it.
- **Signal** comes from everything else: the answer of a work, a decision of a neighbour capability, the result of another screen, a lifecycle event. A capability sends only the signals it owns, and any capability may listen to them.
- **Started** marks the start of the runtime, the first fact it handles. No capability reacts to it: the runtime runs the bootstraps while it handles `Started`. A rotation or a recomposition doesn’t send it again.

Facts are immutable values with a readable `toString()`: the runtime history and crash reports print them.

### Slice

A slice is the state of one capability. It holds domain data: `likes: Int`, `deadline: Instant`, `results: Load<List<Item>>`. It never holds formatted strings, colours, or resources: the projection makes those.

A field that only the UI needs belongs in the projection, not in the slice.

### Capability

A capability is a pure function `(Slice, Fact) -> Decision` with a name and an initial slice. It declares which fact classes it handles, one reaction per class, and the runtime never delivers the rest.

A capability owns three things, and its type names them: its slice, its intents, and its signals. It reacts to its own intents and to any signal, and it sends only its own signals. So an intent has one owner on the screen. When a neighbour needs to know about the user’s action, the owner sends a signal of its own.

A capability is the unit of a user scenario. It doesn’t know the UI, the host, or other capabilities. What it needs from the world comes through ports, what it wants done goes out as works and effects.

### Bootstrap

The bootstrap is the first decision of a capability: the initial load, the subscriptions, the first effects. A capability has at most one.

- The runtime runs it once, while it handles `Started`, before any other fact. A rotation doesn’t run it again.
- It gets the initial slice, or the restored one after the process died. Works don’t survive a restore, so the bootstrap starts them again.
- The bootstraps of a screen run in install order, then the signals they send, then the next fact.
- It doesn’t wait for its works: they run on their own, and their answers arrive as signals later.

### Decision

A decision is what a capability returns for a fact or from its bootstrap:

- the new slice
- works to start
- work keys to cancel (the runtime cancels before it starts, so a cancel and a start with one key restart the work)
- effects for the host
- signals for the runtime to handle right after the current fact.

A single item and a list of each kind are separate arguments, and a missing single item means “nothing”. Name every argument, so a reader sees what the decision does.

A signal is news for the neighbours. A capability changes its own slice in the same decision, without a signal to itself. The answer of a work is already a signal, so nobody forwards it. What the host must do leaves as an effect.

### Work

A work is an async job described as data: a key, a policy, a source, and an error handler. It answers with the signals of the capability that starts it, its answers and its error alike.

- **work** runs a suspending block and turns its result into one signal. The block may make several calls or only wait.
- **stream** subscribes to a flow and turns each value into a signal.
- **WorkKey** names the work inside its capability. Equal keys of different capabilities don’t clash.
- **WorkPolicy** decides what a second start with the same key does: `Latest` cancels the old run (the default), `KeepExisting` ignores the new one, `Parallel` runs both.
- **onError** turns an exception into a signal, so a failure becomes state instead of disappearing.
- **debounce** delays a one-shot work. With `Latest`, a new start cancels the wait of the old one.
- **retry** repeats a failed work with a delay and a backoff. `onError` sees the error only after the last attempt.
- **whileVisible** pauses a stream while the screen is hidden and starts it again when the screen comes back.

### Effect

An effect is a command for the host: navigate, show a snackbar, share, send an analytics event. A capability returns effects and never performs them. Every effect needs a handler in the host.

### Ports and wiring

Ports describe what a capability needs from the world, in the capability’s own terms: `isPremium: suspend () -> Boolean`, not “call the billing use case”. A port class is a class of lambdas.

The wiring sits on the other side: it builds ports from the project’s real use cases and repositories. It only translates types and calls. A condition that means something for the product belongs in `decide`.

### Projection

A projection is a pure function from one or more slices to a UI model. It formats, picks resources, and joins slices. Formatters arrive as parameters, so a projection tests without DI.

### Host

The host is the entry point of the screen. It installs capabilities, builds projections, sends intents and outside signals into the runtime, and performs effects. It holds no product decisions. Capsa ships two hosts: one on AndroidX `ViewModel` for Android, iOS, and the JVM, and one right in the Compose composition, `rememberCapsa`, for every platform of Compose Multiplatform. SKILL.md says how to choose.

### Runtime

The runtime owns the queue of facts, the slices, the running works, and the outbox of effects. Capabilities share data only through it.

## The flow of one fact

```text
UI ──Intent──▶ queue ──▶ capability.decide(slice, fact) ──Decision──▶ runtime
                ▲                                                   │
                │◀──────── signals (before the next queued fact) ◀──┤
                │◀──────── Signal from a work ◀── work / stream ◀───┤ starts and cancels
                                                                    │
UI ◀── UI model ◀── projection ◀── new slice ◀──────────────────────┤
host ◀── effect ◀───────────────────────────────────────────────────┘
```

1. A fact enters the queue from the UI, a work, or the host.
2. The runtime takes the next fact and passes it to every capability that handles its class, in install order. For `Started` it runs every bootstrap instead.
3. Each capability returns a decision. The runtime stores the slice, puts the effects into the outbox for the host, cancels the listed works, and starts the new ones.
4. Signals from the decision get handled right after, with a depth one greater, before the next fact from the queue. A chain deeper than the limit (8 by default) is a cycle, and the runtime breaks it.
5. A work emits a signal into the queue. If its run was revoked meanwhile, the runtime drops the signal.
6. Projections see the new slice and emit a new UI model.

## Errors

- **Expected errors** (no network, a validation failure) go through `onError` into a signal and then into the slice. The UI shows them.
- **Contract violations** are bugs:
  - `UnhandledFact`: no capability handles the fact
  - `SignalCycle`: a chain of signals went too deep
  - `DecideFailed`: `decide` threw, or a fact matched two reactions of one capability through unrelated interfaces
  - `WorkCrashed`: `onError` itself threw

  The runtime keeps the previous slice and goes on. The report carries the last 32 events of the runtime.
- **Fail fast in debug, crash-free in release.** A violation, a failing projection, a broken snapshot, or a failing effect handler crashes a debug build at once and goes to the logger in a release build.
- **Build errors** stay fatal everywhere: a duplicate capability name, overlapping reactions, a reaction to `Started` or a second bootstrap, two capabilities that own one intent, installing a capability after the runtime started.

## Screens and their runtimes

Each screen has its own runtime and its own queue. Facts never leave a screen. Screens share data through a common source in the app, such as a repository or a shared flow, and each capability sees that source as a port.
