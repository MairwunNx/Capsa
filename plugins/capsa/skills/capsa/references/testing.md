# Testing

This file holds the principles. The names of the test tools for each platform live in the platform file, such as `kotlin.md`.

## What to test where

| What | How |
|---|---|
| A reaction without works | Feed facts straight into `decide`, one by one. No coroutines, no runtime. Check the slice, the effects, and how the works and signals are described |
| A capability with works, timers, retries, debounce | Run the real runtime on virtual time with fake ports. Check the slice, the effects, and the work events |
| A chain of signals between capabilities | The same runtime with every capability of the chain installed |
| A projection | A plain function test, often a table of cases |
| A use case in the domain layer | Its own test, outside Capsa |
| The wiring | No test. If it needs one, it holds logic, and the logic belongs in `decide` |
| The whole host | Only when the host itself does something: saving, effect handlers it keeps. Replace the main and the work dispatchers with the test one |

## Principles

- **Every intent has a test without the UI.** A capability is a pure function, so a test sends facts and checks the slice. No emulator, no mock framework.
- **Every effect is checked where it leaves the capability**, not in its handler.
- **Ports are lambdas.** A fake port is a lambda in the test: `search = { query -> listOf(item(query)) }`, `save = { error("offline") }`.
- **Time is virtual.** Timeouts, debounce, retries, order of loading, and timers move the test clock explicitly. A real `delay` in a test is a bug.
- **The runtime is strict in tests.** The first contract violation fails the test. A test that checks a violation on purpose turns strict mode off and reads the collected violations.
- **The runtime lives in the background of the test.** A helper that runs “until idle” skips background work, so it doesn’t run the runtime at all. Run the current tasks, or move the clock by a duration.
- **Visibility is part of the scenario.** For a `whileVisible` stream, hide the screen, move the clock, show it again, and check that the stream started from the current value.
- **Stale answers.** Start a work twice with `Latest` and check that the first answer was dropped and never reached the slice.
- **Errors become state.** Make a port throw and check the slice (`Load.Failed`, a rollback) and the effect (a snackbar), not the exception.
- **Tests sit in the same packages as the code.**
- **Names describe behaviour** in English: `a failed save rolls the like back`, `typing faster than the debounce sends one request`.

## The shape of a test

1. Create the capability with fake ports.
2. Start the runtime with it, or prepare a list of facts for a pure replay.
3. Send facts and move the virtual clock.
4. Check the slice, the effects, and, when it matters, the work events: started, cancelled, failed, dropped.

## What not to test

- That the runtime handles facts in order, drops stale signals, or delivers each effect once. The runtime guarantees it, and Capsa’s own tests check it.
- Handlers that only forward an effect to the project’s router or analytics.
- Private helpers of a capability on their own. Test them through facts.
