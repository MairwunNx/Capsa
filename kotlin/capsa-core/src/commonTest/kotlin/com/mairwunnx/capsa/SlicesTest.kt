package com.mairwunnx.capsa

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SlicesTest {

    private val scope = TestScope(UnconfinedTestDispatcher())

    @Test
    fun mapStateStartsFromTheCurrentValueAndFollowsChanges() {
        val source = MutableStateFlow(1)
        val mapped = source.mapState(scope.backgroundScope) { it * 10 }

        assertEquals(10, mapped.value)
        source.value = 2
        assertEquals(20, mapped.value)
    }

    @Test
    fun combineStateOfThreeAndFourSlicesReactsToEachOfThem() {
        val a = MutableStateFlow(1)
        val b = MutableStateFlow(2)
        val c = MutableStateFlow(3)
        val d = MutableStateFlow(4)
        val three = combineState(scope = scope.backgroundScope, a = a, b = b, c = c) { x, y, z -> "$x$y$z" }
        val four = combineState(
            scope = scope.backgroundScope,
            a = a,
            b = b,
            c = c,
            d = d,
            started = SharingStarted.Eagerly,
        ) { x, y, z, w ->
            "$x$y$z$w"
        }

        assertEquals("123", three.value)
        assertEquals("1234", four.value)
        d.value = 9
        c.value = 7
        assertEquals("127", three.value)
        assertEquals("1279", four.value)
    }

    @Test
    fun failingTransformSkipsTheValueAndTheProjectionKeepsFollowing() {
        val errors = mutableListOf<Throwable>()
        val scope = CoroutineScope(
            UnconfinedTestDispatcher() + SupervisorJob() + CoroutineExceptionHandler { _, e -> errors += e },
        )
        val a = MutableStateFlow(1)
        val b = MutableStateFlow(0)
        val mapped = a.mapState(scope) {
            require(it != 2) { "bad slice" }
            it * 10
        }
        val combined = combineState(scope, a, b) { x, y ->
            require(x != 2) { "bad pair" }
            x + y
        }

        a.value = 2
        assertEquals(10, mapped.value)
        assertEquals(1, combined.value)

        a.value = 3
        assertEquals(30, mapped.value)
        assertEquals(3, combined.value)
        assertEquals(listOf("bad slice", "bad pair"), errors.map { it.message })
        scope.cancel()
    }
}
