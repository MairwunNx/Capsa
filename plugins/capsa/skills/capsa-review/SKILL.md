---
name: capsa-review
description: Review code on Capsa against the rules of Capability Slice Architecture and the known pitfalls, and list each violation with its file and line. Use when the user asks to review, check, or audit a diff, a branch, files, or a screen on Capsa.
argument-hint: "[files, a directory, a branch, or a commit range – the uncommitted changes by default]"
---

# capsa-review

Review code on Capsa. What to review:

> $ARGUMENTS

If the line above shows nothing, or the placeholder as it is, take the target from the user’s message that called this command.

## Start

1. Find the platform of the project. Gradle build files that apply the Kotlin Multiplatform plugin (`org.jetbrains.kotlin.multiplatform`, `kotlin("multiplatform")`) or an Android plugin (`com.android.application`, `com.android.library`, `com.android.kotlin.multiplatform.library`) mean Kotlin. Look in `settings.gradle.kts`, the `build.gradle.kts` files, and `gradle/libs.versions.toml`.
2. If there are no Gradle build files, the platform is something else, or you can’t tell, say that Capsa doesn’t support this project yet, name what you found, and stop. Don’t guess.
3. Load the `capsa` skill and read its `SKILL.md`, then the platform file: `references/kotlin.md` for Kotlin. If the skill isn’t installed, tell the user that this command needs it and stop.

Then read `references/rules.md` and `references/pitfalls.md` of the `capsa` skill in full.

The review reads code. It doesn’t build the project or run tests unless the user asks.

## 1. Pick the code

- Files or a directory: review them.
- A branch or a commit range: review its diff against the base, and read the whole files the diff touches.
- Nothing named: review the uncommitted changes: staged, unstaged, and new untracked files. If there are none, ask the user what to review and stop.

Review only code on Capsa: files that import `com.mairwunnx.capsa`, and the wiring, projections, and screens of those hosts. Say which files you skipped and why. Leave the rest of the project alone.

## 2. Go through the checklist

### Structure

- `business/capabilities` imports no UI framework, navigation, resources, DI annotations, use cases, repositories, or the data layer.
- `business/wiring` imports nothing from `presentation`, no UI framework and no host.
- A capability imports no other capability file and reads no neighbour’s slice. It may import a neighbour’s facts.
- No new module for a screen, no capability moved into a shared module before a second screen needs it.

### Capabilities and facts

- `decide` is pure: no I/O, no clock, no randomness, no DI, no coroutines, no state in closures or objects.
- One reaction per fact class, and no reaction to a supertype next to one to its subtype. No fact that implements two interfaces with a reaction each.
- A sealed slice states its type in `capability<…>(…)`.
- Facts and effects are `data class` or `data object`.
- Every `Intent` and `Signal` has a reaction. A new signal for a neighbour comes with the neighbour’s reaction.
- No chain of signals that loops back.
- No screen-wide fact with fewer than two listeners.
- The decider stays small: about 150 lines or 12 facts.
- Static input of the screen is a parameter of the capability function.

### Works

- Every work has its own `onError` that returns a signal, and a reaction turns that signal into state.
- `WorkKey` is a private constant of the capability file.
- Jobs that must start, stop, or fail apart have keys of their own. A work that makes several calls, or only waits and sends a signal, breaks no rule.
- `KeepExisting` isn’t used for a work that depends on input, such as a reload with a new filter.
- No manual `delay` for debounce, no retry loop, no request token to guard against stale answers.
- A timer or a frequent subscription is a `stream` with `whileVisible = true`, and its flow starts from the current value, not from the moment of subscription.
- The initial load and subscriptions start in the reaction to `Started`. Nothing relies on `Started` to refresh on return.
- No `CancellationException` used to stop a work: cancel by the key.

### Slices, ports, wiring

- Slices hold domain data, not formatted strings or resources. `Load<T>` holds no exception.
- Ports are lambdas in the capability’s terms.
- The wiring only translates types and calls, without product conditions.
- Network data isn’t saved with `installSaved`.

### Projections

- Pure functions with formatters passed in, total on the initial slice.
- `mapState` and `combineState` get `capsaScope` of the host, not `viewModelScope` or a scope of their own.
- At most four sources in one `combineState`.

### Host and screen

- The host installs, projects, and handles effects. It holds no product conditions, and neither do the wiring, the projection, or the UI.
- Every effect has a handler. `effects` has one collector per screen.
- `install` and `installSaved` run before the runtime starts: no `send`, `signal`, or access to `effects` before the last `install`.
- Effect handlers do no long work in place.

### Visibility

These break the visibility of the screen, so `whileVisible` streams run in the background or never pause:

- `collectAsState()` without a lifecycle on a Capsa screen. Use `collectAsStateWithLifecycle()`.
- `stateIn(viewModelScope, SharingStarted.Eagerly)` or `SharingStarted.Lazily` over a slice or a projection in a view model.
- `launch { slice.collect { … } }`, or any other endless collection of a slice, inside the host.

### `rememberCapsa`

- No activity, `LocalContext.current`, or other short-lived context passed to a `CapsaComposable` or captured by the lambda of `rememberCapsa`. Only the `Application` or `applicationContext`.
- The values that should create a new runtime, such as an id, go to `keys`.
- A screen that must keep running on the back stack has a `RetainedValuesStore` per navigation entry, or stays on `CapsaViewModel`.
- The lambda either ends with `expose(…)` or returns a `CapsaComposable`, never both.

### Tests

- Every new intent, signal, and effect has a test.
- No `advanceUntilIdle()` to drive the runtime: `runCurrent()` and `advanceTimeBy(…)`.
- No real `delay` in tests. Ports are fake lambdas, no mock framework for them.
- A view model test replaces and resets both dispatchers, and collects the state when it tests `whileVisible` streams.
- `strict = false` only in tests of violations.

## 3. Report

List the violations, most harmful first: broken contracts that crash a debug build, then release crashes and broken visibility, then structure and boundaries, then the rest, then missing tests. One entry each:

```text
path/to/File.kt:42 – rule – what is wrong and what it leads to. Fix: what to change.
```

- Give the exact line. Quote the code when the line alone doesn’t make it clear.
- Name the rule or pitfall from `references/rules.md` or `references/pitfalls.md`.
- Report only what breaks a rule or a pitfall, no matters of taste.
- Mark a finding you aren’t sure of as a question.
- Without violations, say so in one line and name what you checked.

Don’t change any code unless the user asks.

## Example requests

- `/capsa-review` – the uncommitted changes
- `/capsa-review feature/search/src/commonMain/kotlin/com/example/search`
- `/capsa-review main..HEAD`
