package com.mairwunnx.capsa.compose

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope

/** A composition without a UI toolkit: an empty `Applier`, a [Recomposer], and a frame clock the test drives. */
internal class HeadlessComposition(
    val recomposer: Recomposer,
    private val clock: BroadcastFrameClock,
    private val scope: TestScope,
) {
    /** Runs the pending coroutines, sends one frame, and runs what the frame started. */
    fun frame() {
        scope.testScheduler.runCurrent()
        clock.sendFrame(0L)
        scope.testScheduler.runCurrent()
    }
}

private object UnitApplier : AbstractApplier<Unit>(Unit) {
    override fun insertTopDown(index: Int, instance: Unit) = Unit
    override fun insertBottomUp(index: Int, instance: Unit) = Unit
    override fun remove(index: Int, count: Int) = Unit
    override fun move(from: Int, to: Int, count: Int) = Unit
    override fun onClear() = Unit
}

/**
 * Composes `content` in the background scope of the test and returns after the first frame.
 *
 * The test scope disposes the composition and cancels the recomposer at the end.
 */
internal fun TestScope.compose(content: @Composable () -> Unit): HeadlessComposition {
    val clock = BroadcastFrameClock()
    val recomposer = Recomposer(backgroundScope.coroutineContext + clock)
    backgroundScope.launch(clock) { recomposer.runRecomposeAndApplyChanges() }
    // A composition set before the recomposer runs gets one more recomposition when it starts.
    testScheduler.runCurrent()
    val composition = Composition(UnitApplier, recomposer)
    composition.setContent(content)
    val host = HeadlessComposition(recomposer, clock, this)
    host.frame()
    backgroundScope.launch {
        try {
            awaitCancellation()
        } finally {
            composition.dispose()
            recomposer.cancel()
        }
    }
    return host
}
