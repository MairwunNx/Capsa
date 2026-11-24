---
name: capsa-migrate
description: Move an existing screen, or a part of it, to Capsa – capabilities, slices, ports, wiring, projections, and a host – without changing its behaviour. Also moves Capsa code from the old API (commit, on<Fact>, on<Started>) to the current one. Use when the user asks to migrate, port, or rewrite a screen on Capsa or Capability Slice Architecture, or to update Capsa code to the new API.
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

First tell which migration this is. Code that already imports `com.mairwunnx.capsa` and calls `commit(`, `on<…>` inside `capability(…) { }`, or `on<Started>` is on the old Capsa API: follow “Moving from the old Capsa API” below instead of steps 1 to 4. Everything else is a screen on another architecture.

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

If the user named no depth, take **A** and say so. If the user named no host, pick it as “Choosing a host” in `SKILL.md` describes. In a module without Compose, take `CapsaViewModel` without asking and say why. With Compose, stop before any code, name the three hosts, and recommend one with the reason taken from the project. If the project has no way to toggle features and the user named no switch, ask about it, in the same message as the host question if there is one. If you asked anything, wait for the answers. If the request contradicts itself or the screen is unclear, ask one question and wait. Decide the rest yourself.

## 2. Study the screen

Read `references/rules.md`, `references/testing.md`, and `references/pitfalls.md` of the `capsa` skill. If the project already has a screen on Capsa, use it as an example of the structure, but don’t copy its models or dependencies into this screen.

Read the view model, its mappers, the UI of the screen, and everything they call. Then make an inventory for the plan:

| What to look for | Becomes in Capsa |
|---|---|
| UI events: clicks, the screen shown, pull to refresh | `Intent` of the capability that owns the scenario, under its sealed intent interface |
| One UI event that changes several parts of the screen | An intent of one owner. The owner sends its own signal, and the other capabilities react to it |
| Flows and use cases the view model reads | `Signal` from a work through a port, under the capability’s sealed signal interface |
| Navigation, analytics, dialogs, snackbars, toasts | `Effect` |
| State fields that only this logic changes | A slice |
| Rules in mappers, `remember`, and the view model: formatting, columns, conditions to show something | A projection or `decide` |
| `combine` of several flows | `combineState` in a projector, or separate slices |
| Resources, platform context, or the router used by the logic | A port, an effect, or the projector |
| Loading in the view model’s `init` | The `bootstrap` of the capability with a `work` or a `stream`. It runs again with the restored slice after process death |
| `try`/`catch`, `runCatching`, error handling of a load | `onError` of the work → a signal of the capability → a field of the slice (`Load.Failed`) |
| A private method that several handlers call to load or save | A work in `val works = object { … }` at the top of the capability block |
| A state update the view model posts to itself, such as `_events.emit` handled in the same class | A change of the slice in the same reaction, not a signal |
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

- A table of capabilities: name, slice, the intents it owns, the signals it owns, the signals of neighbours it handles, which effects it sends, and what its `bootstrap` starts.
- The packages `<screen>/{business/{capabilities,wiring},presentation}`, as `references/rules.md` describes them.
- Ports and wiring: which use cases they wrap. Where a port joins several sources, a new use case goes into the domain layer.
- How the new screen gets switched on.
- The bugs and odd behaviour you found, with a proposal for each. The user decides.

## 4. Build it in this order

Run the checks that fit after every step. Fix an error in the code you changed before the next step. If the environment or a known problem of the project blocks a check, write down why, and go on only where the result doesn’t depend on it.

1. **Capsa in the module.** If the module lacks it, add the artifacts from `references/kotlin.md` the same way the project adds other libraries, with the Capsa version the project already uses. A project without Capsa at all needs `capsa-setup` first: tell the user and stop.
2. **The pure layer:** facts, slice, capability, ports, and the analytics effects. Each capability is `capability<Slice, Intents, Signals>(name = …, initial = …)`, its reactions return `Decision(state = …, …)` with named arguments, and its works sit inside its block. Write tests for each capability at once, with the test tools from `references/kotlin.md`.
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

## Moving from the old Capsa API

The old API built a capability with `capability(name, initial) { on<Fact> { … } }` and returned `state.commit(…)`. The current one names what the capability owns and builds decisions with `Decision(…)`. The behaviour of the screen stays: only the declarations change. Effect handlers (`effectHandlers { on<E> { … } }`, `handleEffects`) don’t change, `on<E>` stays right there.

1. **The version.** The project needs a Capsa release with `onIntent`, `onSignal`, and `bootstrap`. If it pins an older one, ask the user before you update it.
2. **Sealed facts.** For each capability, add `sealed interface <Name>Intent : Intent` and `sealed interface <Name>Signal : Signal` to its facts file. Keep the names and the packages of the facts, only change their parent: `data object LikeClicked : LikeIntent`.
3. **Ownership.** Find intents that several capabilities react to. Keep the reaction in the capability that owns the scenario, and give it a signal of its own that the others handle. Find decisions that send a neighbour’s signal: the signal moves to the capability that sends it, or the sender gets a signal of its own. Both are build or install errors now, so none may stay.
4. **The rewrite**, one capability at a time:

| Old API | Current API |
|---|---|
| `capability("likes", LikeSlice()) { … }` | `capability<LikeSlice, LikeIntent, LikeSignal>(name = "likes", initial = LikeSlice()) { … }`. `Nothing` for no intents or no signals |
| `on<Started> { state, _ -> state.commit(load()) }` | `bootstrap { state -> Decision(state = state, work = works.load()) }`. At most one per capability |
| `on<LikeClicked> { state, fact -> … }` | `onIntent<LikeClicked> { state, clicked -> … }` for the capability’s own intents |
| `on<LikeSaved> { state, fact -> … }` | `onSignal<LikeSaved> { state, saved -> … }` for any signal |
| `state.commit()` | `Decision(state = state)` |
| `state.copy(…).commit(effect)`, `commit(work)`, `commit(signal)` | `Decision(state = state.copy(…), effect = …)`, `work = …`, `signal = …` |
| `commit(effects = …, works = …, cancel = listOf(K), signals = …)` | `Decision(state = …, effects = …, works = …, cancel = K, signals = …)`. Several keys go to `cancels` |
| `private fun load() = work(LOAD, …)` outside the block | `val works = object { fun load() = work(key = LOAD, …) }` inside the block, typed to the capability’s signals |
| `Flow<Signal>` of a stream helper | `Flow<LikeSignal>` |
| `Decision<S>`, `Work`, `onDecision(…, decision: Decision<*>)` | `Decision<S, O>`, `Work<O>`, `onDecision(…, decision: Decision<*, *>)` |

5. **Signals you no longer need.** Drop a signal a capability sent only to itself: change the slice in the same reaction. Drop a signal that only forwards the answer of a work: neighbours listen to the answer itself. A signal that asks the host to act becomes an effect. Each such change keeps the behaviour, so check it with the existing tests.
6. **Tests.** `replay(Started, …)` runs the bootstrap, and the harness still lists `Started` among the handled facts. A test where two capabilities reacted to one intent installs the owner and the neighbour and checks the neighbour through the owner’s signal. Keep what each test checked.
7. **Check and report** as in steps 5 and 6 above. Under “What I found”, list each place where ownership forced a new signal.

## Example requests

- `/capsa-migrate the search screen in :feature:search, depth A, CapsaViewModel, keep the layout`
- `/capsa-migrate the product details screen: move loading and the like into capabilities, leave the rest, don’t touch the composables`
- `/capsa-migrate the recommendations block on the home screen, fully, with the layout, on rememberCapsa with a class`
- `/capsa-migrate move :feature:search from commit and on<Started> to the new API`
