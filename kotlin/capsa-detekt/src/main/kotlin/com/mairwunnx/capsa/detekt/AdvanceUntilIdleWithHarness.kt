package com.mairwunnx.capsa.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.psi.KtFile

/**
 * Reports `advanceUntilIdle()` in a file that tests with `capsaHarness`.
 *
 * The harness starts the runtime in `backgroundScope`, and `advanceUntilIdle()` stops as soon as only background tasks
 * remain. So it runs nothing: the runtime doesn’t even take the queued facts, and a test that checks little passes
 * with nothing done. `harness.runCurrent()` runs what is due now, and `harness.advanceTimeBy(…)` moves the clock.
 */
internal class AdvanceUntilIdleWithHarness(config: Config) : Rule(
    config,
    "advanceUntilIdle() runs nothing in a test with capsaHarness: use harness.runCurrent() or harness.advanceTimeBy(…).",
) {

    override fun visit(root: KtFile) {
        if (!root.sees("capsaHarness", "$CAPSA.test")) return
        val calls = root.calls()
        if (calls.none { it.calleeName == "capsaHarness" }) return
        calls.filter { it.calleeName == "advanceUntilIdle" }.forEach { call ->
            report(
                Finding(
                    Entity.from(call),
                    "advanceUntilIdle() doesn’t run the Capsa runtime, which lives in backgroundScope. Use harness.runCurrent() or harness.advanceTimeBy(…).",
                ),
            )
        }
    }
}
