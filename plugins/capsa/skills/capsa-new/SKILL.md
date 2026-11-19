---
name: capsa-new
description: Create a new screen on Capsa, or a new capability on an existing Capsa screen, from scratch – facts, slice, capability, ports, wiring, projection, host, and tests. Use when the user asks for a new screen or capability on Capsa or Capability Slice Architecture.
argument-hint: "<screen or capability, what it does, which module, which host>"
---

# capsa-new

Create a screen or a capability on Capsa. The user’s request:

> $ARGUMENTS

If the line above shows no request, or the placeholder as it is, take the request from the user’s message that called this command.

## Start

1. Find the platform of the project. Gradle build files that apply the Kotlin Multiplatform plugin (`org.jetbrains.kotlin.multiplatform`, `kotlin("multiplatform")`) or an Android plugin (`com.android.application`, `com.android.library`, `com.android.kotlin.multiplatform.library`) mean Kotlin. Look in `settings.gradle.kts`, the `build.gradle.kts` files, and `gradle/libs.versions.toml`.
2. If there are no Gradle build files, the platform is something else, or you can’t tell, say that Capsa doesn’t support this project yet, name what you found, and stop. Don’t guess.
3. Load the `capsa` skill and read its `SKILL.md`, then the platform file: `references/kotlin.md` for Kotlin. If the skill isn’t installed, tell the user that this command needs it and stop.

## 1. Read the request

Write out, and show the user briefly:

- **What to create:** a new screen, or a capability on an existing Capsa screen.
- **The scenarios:** what the user of the app does and what the screen answers. One scenario is one capability: a like, a search, a timer, a form.
- **Where:** the module and the package. Extend an existing module, don’t create a new one.
- **The host,** for a new screen: `CapsaViewModel`, `rememberCapsa` with a `CapsaComposable` class, or `rememberCapsa` with a lambda. A new capability goes into the host its screen already has: never move a screen to another host on your own.
- **The data:** which use cases, repositories, or APIs of the project the ports wrap.

If the user named no host for a new screen, stop before any code and recommend one as “Choosing a host” in `SKILL.md` describes: `CapsaViewModel` for a module without Compose, otherwise the three hosts with the reason taken from the project. Wait for the answer. If a scenario or the data source is unclear, ask one question and wait. If the user asked you not to wait, pick the simplest option and list it under open decisions. Decide the rest yourself.

## 2. Look around

Read `references/rules.md`, `references/testing.md`, and `references/recipes.md` of the `capsa` skill. Read `references/pitfalls.md` when you get to works and the host.

Read one or two neighbouring screens of the same module: how the project creates view models or screens, injects dependencies, navigates, shows messages, and logs analytics. The new code follows the same conventions. If the project already has a screen on Capsa, follow its structure, but don’t copy its models.

## 3. Show the plan

Show the list of files before you write them, and wait for the user only when the screen has more than three capabilities. The structure comes from `references/rules.md`:

```text
<screen>/
  business/
    capabilities/<name>/          <Name>Facts, <Name>Slice, <Name>Ports, <Name>Capability, <Name>Track if it logs analytics
    wiring/                       <Name>PortsWire
  presentation/
    <Screen> host and the screen’s UI
    state/  projection/  effects/
```

Create only the folders that get files. For a capability on an existing screen, the plan names the new capability folder, the changes to the host, the projector, and the screen, and the signals it exchanges with its neighbours.

## 4. Build it in this order

Run the checks that fit after every step and fix errors in your code before the next one.

1. **Capsa in the module.** If the module lacks it, add the artifacts from `references/kotlin.md` the same way the project adds other libraries, with the Capsa version the project already uses. A project without Capsa at all needs `capsa-setup` first: tell the user and stop.
2. **Facts and the slice.** Facts and effects are `data class` or `data object`. The slice holds domain data with defaults, `Load<T>` for loaded data, and no ready strings.
3. **Ports:** a class of lambdas in the capability’s terms.
4. **The capability:** a function named by a noun with the context parameter `CapabilityScope`. One reaction per fact class. Start the initial load in the reaction to `Started`. Every work has a private `WorkKey`, a policy, and an `onError` that returns a signal, which a reaction turns into state. Debounce and retries are parameters of the work. Take the closest example from `references/recipes.md`.
5. **Tests of the capability,** next to the code, with the tools from `references/kotlin.md`: every intent, every effect where it leaves the capability, errors as state, stale answers, and visibility for `whileVisible` streams.
6. **The wiring:** `<Name>PortsWire` builds the ports from the project’s real implementations. It only translates types and calls. If a port needs to join several sources, add a use case in the domain layer and test it.
7. **Projections:** pure functions from slices to the UI model, total on the initial slice, with formatters passed in as lambdas. Tests as plain functions, often a table of cases.
8. **The host:** installs the capabilities, builds the projections on `capsaScope`, keeps the handlers of service effects. Use `installSaved` with a `SliceSaver` for input that must survive process death, never for network data. With `rememberCapsa`, pass the `Application`, never the activity or `LocalContext.current`.
9. **The screen:** collects state with a lifecycle-aware collector, collects effects once with `CollectEffects`, and sends intents. It holds no product conditions. Reuse the project’s UI components.
10. **Navigation and DI:** register the screen the way the neighbours do.

If the project has no UI, navigation, or DI yet, skip those steps and say so in the report. An effect meant for the screen then has no handler: name it under “isn’t checked”.
11. **Boundaries in the linter.** If the project has an architecture linter or import rules, add the new packages to them.

## 5. Check

- Find how the project runs its tests and its linter, as `references/kotlin.md` describes, and don’t guess. Run the narrowest tasks for the module first, then what CI runs.
- Go through “Before you finish” in `SKILL.md`: every intent and signal has a reaction and a test, every effect has a handler, projections don’t fail on the initial slice.
- Name what you didn’t check: a device, the DI graph, a manual run.

## 6. Report

Answer briefly:

1. What you created: the capabilities, the host, the screen, and the files.
2. Which scenarios the tests cover.
3. Which tests and linters passed, and what isn’t checked.
4. Open decisions for the user: texts, design, analytics events you named yourself.

## Example requests

- `/capsa-new a timer screen in :feature:quiz: a 30-second countdown that pauses in the background, on rememberCapsa with a lambda`
- `/capsa-new a capability for the like button on the post screen, the API is LikesRepository.setLiked`
- `/capsa-new a sign-up form with name and email validation and a submit button, CapsaViewModel, keep the input after process death`
