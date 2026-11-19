---
name: capsa-migrate
description: Move an existing screen, or a part of it, to Capsa – capabilities, slices, ports, wiring, projections, and a host – without changing its behaviour. Use when the user asks to migrate, port, or rewrite a screen on Capsa or Capability Slice Architecture.
argument-hint: "<screen, what to move, how deep, which host, whether to touch the layout>"
---

# capsa-migrate

Move a screen to Capsa. The user’s request:

> $ARGUMENTS

If the line above shows no request, or the placeholder as it is, take the request from the user’s message that called this command.

## Start

1. Find the platform of the project. Gradle build files that apply the Kotlin Multiplatform plugin (`org.jetbrains.kotlin.multiplatform`, `kotlin("multiplatform")`) or an Android plugin (`com.android.application`, `com.android.library`, `com.android.kotlin.multiplatform.library`) mean Kotlin. Look in `settings.gradle.kts`, the `build.gradle.kts` files, and `gradle/libs.versions.toml`.
2. If there are no Gradle build files, the platform is something else, or you can’t tell, say that Capsa doesn’t support this project yet, name what you found, and stop. Don’t guess.
3. Load the `capsa` skill and read its `SKILL.md`, then the platform file: `references/kotlin.md` for Kotlin. If the skill isn’t installed, tell the user that this command needs it and stop.

## 1. Read the request

Write out, and show the user briefly:

- **The screen** and the module it lives in.
- **What moves:** the whole screen or a part, such as one block, one tab, or only the loading of data. A request that names only a class or a screen means the whole screen.
- **Depth**, one of three:
  - **A. Business logic only.** The logic of the view model and its mappers moves into capabilities, wiring, and projections. The new host gives the existing UI the same models as the old screen.
  - **B. Logic and screen models.** The same, plus UI models and projections of their own. The existing UI stays, behind an adapter if needed. The layout isn’t rewritten.
  - **C. Logic and layout.** The UI is rewritten too. Only when the user asks for it.
- **The host:** `CapsaViewModel`, `rememberCapsa` with a `CapsaComposable` class, or `rememberCapsa` with a lambda.
- **What stays untouched:** other screens, DTOs, repositories, existing use cases, analytics.
- **The switch:** how the new screen gets turned on, next to the old one, in the way the project already toggles features. The old screen stays.

A slot doesn’t define the depth. Add one only for a real boundary of the UI: a shared host, several UI implementations, or a public module API.

If the user named no depth, take **A** and say so. If the user named no host, pick it as “Choosing a host” in `SKILL.md` describes. In a module without Compose, take `CapsaViewModel` without asking and say why. With Compose, stop before any code, name the three hosts, and recommend one with the reason taken from the project. If the project has no way to toggle features and the user named no switch, ask about it, in the same message as the host question if there is one. Wait for the answers. If the request contradicts itself or the screen is unclear, ask one question and wait. Decide the rest yourself.

## 2. Study the screen

Read `references/rules.md`, `references/testing.md`, and `references/pitfalls.md` of the `capsa` skill. If the project already has a screen on Capsa, use it as an example of the structure, but don’t copy its models or dependencies into this screen.

Read the view model, its mappers, the UI of the screen, and everything they call. Then make an inventory for the plan:

| What to look for | Becomes in Capsa |
|---|---|
| UI events: clicks, the screen shown, pull to refresh | `Intent` |
| Flows and use cases the view model reads | `Signal` from a work through a port |
| Navigation, analytics, dialogs, snackbars, toasts | `Effect` |
| State fields that only this logic changes | A slice |
| Rules in mappers, `remember`, and the view model: formatting, columns, conditions to show something | A projection or `decide` |
| `combine` of several flows | `combineState` in a projector, or separate slices |
| Resources, platform context, or the router used by the logic | A port, an effect, or the projector |
| Loading in the view model’s `init` | A reaction to `Started` with a `work` or a `stream` |
| `try`/`catch`, `runCatching`, error handling of a load | `onError` of the work → a signal → a field of the slice (`Load.Failed`) |
| Timers, `delay`, debounce, manual jobs and their cancelling | A `stream` or a `work` with a `WorkKey` and a policy, `debounce`, `cancel`. A timer gets `whileVisible = true`. A search that keeps the old results until the user stops typing: the recipe “Search: loading starts after a pause” in `references/recipes.md` of the `capsa` skill |
| Manual retries of a request | `retry = Retry(…)` of the work |
| A check that an answer is still current, such as comparing request ids | Nothing: `cancel` and `Latest` drop stale answers |
| The result of another screen, a return from a dialog | `signal(…)` in the host → a signal |
| Flows and buses shared between screens | A port over a shared state flow or shared flow from an API module |
| Saved state (`SavedStateHandle`, `rememberSaveable`) | `installSaved` and a `SliceSaver` |

List separately what Capsa lacks for this screen and what the screen does wrong. Don’t fix it on the way: bugs, races, dead branches, and logic hidden in the UI go to “What I found”. The migration keeps the behaviour. Where the runtime can’t repeat the old behaviour, such as a race on a stale answer or a cancelled request that showed an error, the difference goes to “What I found” too.

## 3. Show the plan

Show the plan before any code when there are more than three capabilities or the depth is B or C. For depth A and a small screen, go on without stopping.

The plan holds:

- A table of capabilities: name, slice, what it owns, which intents and signals it handles, which effects it sends.
- The packages `<screen>/{business/{capabilities,wiring},presentation}`, as `references/rules.md` describes them.
- Ports and wiring: which use cases they wrap. Where a port joins several sources, a new use case goes into the domain layer.
- How the new screen gets switched on.
- The bugs and odd behaviour you found, with a proposal for each. The user decides.

## 4. Build it in this order

Run the checks that fit after every step. Fix an error in the code you changed before the next step. If the environment or a known problem of the project blocks a check, write down why, and go on only where the result doesn’t depend on it.

1. **Capsa in the module.** If the module lacks it, add the artifacts from `references/kotlin.md` the same way the project adds other libraries, with the Capsa version the project already uses. A project without Capsa at all needs `capsa-setup` first: tell the user and stop.
2. **The pure layer:** facts, slice, capability, ports, and the analytics effects. Write tests for each capability at once, with the test tools from `references/kotlin.md`.
3. **A use case in the domain layer**, if a port needs to join several sources. Test it on its own.
4. **The wiring:** `<Name>PortsWire`, which builds the ports from the project’s real implementations. It only translates types and calls.
5. **Projections** and the projector. The projector gets `capsaScope` of the host, not the scope of the view model. Test projections as plain functions.
6. **The host** you agreed on, with the handlers of service effects such as analytics.
7. **The screen,** by depth. With depth A, connect the existing UI to the new model. The screen collects state with a lifecycle-aware collector and effects with `CollectEffects`. If the module has no UI, the new host keeps the public members of the old view model, so its callers switch in one line. Say that no screen was connected.
8. **Boundaries in the linter.** If the project has an architecture linter or import rules, add the new packages to them. Fix the structure instead of suppressing a finding.
9. **The switch** that turns the new screen on.

The project’s rules apply in full: small changes in place, no cleanup on the way, no new modules, no dependency on another feature’s implementation module.

## 5. Check

- Find how the project runs its tests and its linter, as `references/kotlin.md` describes, and don’t guess. Run the narrowest tasks for the module first, then what CI runs.
- The test harness is strict: a fact without a reaction or a cycle of signals fails the test at once. Turn strict mode off only to test such violations.
- Facts are `data class` or `data object`, or a debug build fails as soon as such a fact reaches the runtime. Make effects `data` too, so the runtime journal stays readable.
- Match the analytics of the old and the new screen: name the intent behind each event.
- If you left the layout alone, check that the new model gives the UI the same values as before.
- Name what you didn’t check: a device, the DI graph, a manual run.

## 6. Report

Answer in this order, briefly:

1. What moved and at which depth. The list of capabilities and the host.
2. What you deliberately left alone.
3. **What I found:** bugs and odd behaviour of the old code, with a proposal for each. Fix nothing of it without the user’s decision.
4. Which tests and linters passed.
5. What isn’t checked.
6. How to switch the new screen on.

## Example requests

- `/capsa-migrate the search screen in :feature:search, depth A, CapsaViewModel, keep the layout`
- `/capsa-migrate the product details screen: move loading and the like into capabilities, leave the rest, don’t touch the composables`
- `/capsa-migrate the recommendations block on the home screen, fully, with the layout, on rememberCapsa with a class`
