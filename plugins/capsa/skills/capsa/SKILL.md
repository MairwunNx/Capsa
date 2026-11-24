---
name: capsa
description: Capsa, the Kotlin Multiplatform engine for screens built from capabilities, and Capability Slice Architecture (CSA), the rules behind it. Use when code imports com.mairwunnx.capsa, uses CapsaViewModel, rememberCapsa, CapsaComposable, capability, onIntent, onSignal, bootstrap, Decision, slice, Intent, Signal, Effect, work, stream, WorkKey, mapState, combineState, CollectEffects, or capsaHarness, or when the user asks to build, change, test, migrate, or review a screen on Capsa or CSA (“экран на Capsa”). Skip it for code that doesn’t use Capsa.
---

# Capsa

Capsa runs a screen as a set of small capabilities. Each capability owns one slice of state and reacts to facts. It describes async work and side effects as data, and the runtime carries them out. Capsa implements Capability Slice Architecture (CSA): when this skill and the CSA spec disagree, the spec wins.

These rules apply to code on Capsa only. Leave screens on other architectures alone unless the user asks to migrate them.

## The model in one pass

1. The UI sends an `Intent`. A work or the outside world sends a `Signal`. Both are facts.
2. The runtime puts every fact into one queue and handles them one at a time.
3. Each capability that handles the fact runs `decide(slice, fact)`, a pure function. It returns a `Decision(state = …, work = …, cancel = …, effect = …, signal = …)`: the new slice, works to start, work keys to cancel, effects, and signals.
4. The runtime stores the slice, cancels and starts works, queues signals before the next fact, and hands effects to the host.
5. Projections turn slices into one UI model. The host performs effects: navigation, snackbars, analytics.

| Term | What it is |
|---|---|
| Fact | Something that happened: `Intent` from the UI, `Signal` from a work, a neighbour, or the outside. Each intent has one owner |
| Slice | The state of one capability: domain data, no formatting |
| Capability | `(Slice, Fact) -> Decision`, built with `capability<Slice, Intents, Signals>(name, initial) { bootstrap { … } onIntent<…> { … } onSignal<…> { … } }`. It owns its slice, its intents, and its signals |
| Work | An async job as data: `work` (one answer) or `stream` (a flow), with a `WorkKey` and a `WorkPolicy`. It answers with its capability’s signals |
| Effect | A command for the host. The capability never performs it |
| Ports | What a capability needs from the world, as a class of lambdas |
| Projection | A pure function from slices to a UI model |
| Host | Installs capabilities, exposes the UI model, performs effects |

## What the runtime guarantees

- One coroutine handles facts strictly in order. A capability needs no locks.
- Each capability’s `bootstrap` runs once, before any other fact, with the initial or the restored slice. Start the initial load and subscriptions there. Its works run on their own and block nothing.
- Signals from a decision reach every capability that handles them, neighbours included, before the next queued fact.
- A cancel or a `Latest` restart revokes the old run: its queued signals get dropped, so a stale answer never overwrites a new one.
- A broken contract (`UnhandledFact`, `SignalCycle`, `DecideFailed`, `WorkCrashed`) is a bug. It crashes a debug build and goes to the logger in a release build, and the screen keeps working on its last valid state.
- Build errors stay fatal everywhere: a duplicate capability name, overlapping reactions, a reaction to `Started` or a second `bootstrap`, two capabilities that own one intent, `install` after the runtime started.

## Rules you never break

1. **Slice by scenario.** A capability owns one user scenario: a like, a search, a timer. Never a layout block.
2. **The host assembles, it doesn’t think.** No product conditions in the host, the wiring, the projection, or a composable. Decisions belong to `decide`.
3. **The runtime is the only bus.** A capability never imports another capability or reads its slice. It listens to the neighbour’s signals.
4. **Side effects live at the edge.** `decide` does no I/O, reads no clock, launches no coroutines, and touches no DI. Time, randomness, and data arrive as facts or through ports.
5. **Keep the decider small.** About 150 lines or 12 facts. Past that, split the capability.

And in practice:

- State the three type arguments: `capability<LikeSlice, LikeIntent, LikeSignal>(…)`. `Nothing` stands for no intents or no signals.
- An intent has one owner: one `onIntent` on the screen. A neighbour that cares listens to the owner’s signal.
- A capability sends only its own signals, and its works answer with them. It listens to any signal with `onSignal`.
- Build a decision with `Decision(state = …, …)` and name every argument.
- Send a signal only for a neighbour. Change your own slice in the same reaction, let the answer of a work arrive by itself, and send an effect for the host.
- One reaction per fact class. Reactions in one capability never overlap: sort out subtypes with `when` inside one reaction.
- Facts are `data class` or `data object`. Every `Intent` and `Signal` has at least one reaction.
- Every work has an `onError` that returns a `Signal`, and a reaction turns it into state, such as `Load.Failed`.
- Debounce and retries are parameters of a work: `debounce = 300.milliseconds`, `retry = Retry(…)`. No manual `delay` or retry loops.
- No state in closures or objects. All state lives in slices.

## Choosing a host

A screen runs on one of three hosts. They differ only in where the runtime lives: capabilities, projections, and tests stay the same. `references/kotlin.md` has the API.

| Host | Fits when |
|---|---|
| `CapsaViewModel` | The project already creates view models, through DI or `viewModel { }`. The screen must keep its runtime on the back stack and the navigation keeps no `RetainedValuesStore` per entry. The UI isn’t Compose, such as SwiftUI or Android Views |
| `rememberCapsa` with a `CapsaComposable` class | Compose without view models, for example on the desktop. A big screen: many capabilities, its own effect handlers. A screen that moves off `CapsaViewModel`: the body of the class stays |
| `rememberCapsa` with a lambda | A small Compose screen: one to three capabilities and little wiring |

When you migrate a screen or create a new one and the user hasn’t named the host, first check whether the module uses Compose: the Compose compiler plugin (`org.jetbrains.kotlin.plugin.compose`), the Compose Multiplatform plugin (`org.jetbrains.compose`), or Compose dependencies. Without Compose, write the screen on `CapsaViewModel` without asking, and tell the user why in one sentence: both `rememberCapsa` hosts need `capsa-compose` and Compose in the module. With Compose, ask before you write code: name the three options and recommend one, with the reason taken from the project: what the neighbouring screens use, whether DI creates view models, whether the navigation keeps a store per entry, which platforms the screen runs on. Never move an existing screen to another host on your own. One project may use several hosts.

## What to read next

Read only the file the task needs.

| File | Read it when |
|---|---|
| `references/concepts.md` | You meet Capsa for the first time or need the exact flow of a fact |
| `references/rules.md` | You write or review a capability, ports, a projection, or a host |
| `references/testing.md` | You write tests for a capability, a projection, or a host |
| `references/recipes.md` | You build a search, a like, a timer, a form, or a link between screens |
| `references/pitfalls.md` | A test hangs, a timer runs in the background, a reaction fails at runtime, or you review code |
| `references/kotlin.md` | The project builds with Gradle and the Kotlin Multiplatform or Android plugin: coordinates, API, hosts, `capsa-test`, build and test commands |

The first five files hold no platform API beyond short Kotlin examples. The platform file holds the exact names. Today Capsa has one platform, Kotlin. When the project uses something else, say that Capsa doesn’t support it yet and stop.

## Before you finish

- Each new `Intent` and `Signal` has a reaction and a test, and each `bootstrap` has a test.
- Each `Effect` has a handler in the host, and a test checks it leaves the capability.
- Projections don’t fail on the initial slice.
- The project’s own tests and linter pass. Find their commands in the build files, `CLAUDE.md`, `AGENTS.md`, or CI, and don’t guess.
