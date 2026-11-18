package com.mairwunnx.capsa

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EffectHandlersTest {

    private data class Open(val id: Int) : Effect
    private data class Track(val name: String) : Effect
    private data object Unknown : Effect

    @Test
    fun effectGoesToTheHandlerOfItsType() {
        val opened = mutableListOf<Int>()
        val tracked = mutableListOf<String>()
        val handlers = effectHandlers {
            on<Open> { opened += it.id }
            on<Track> { tracked += it.name }
        }

        handlers.handle(Open(1))
        handlers.handle(Track("shown"))
        handlers.handle(Open(2))

        assertEquals(listOf(1, 2), opened)
        assertEquals(listOf("shown"), tracked)
    }

    @Test
    fun effectWithoutAHandlerFailsByDefault() {
        val handlers = effectHandlers { on<Open> { } }

        assertFailsWith<IllegalStateException> { handlers.handle(Unknown) }
    }

    @Test
    fun unhandledEffectCanBeRedirected() {
        val unhandled = mutableListOf<Effect>()
        val handlers = effectHandlers(onUnhandled = { unhandled += it }) { on<Open> { } }

        handlers.handle(Unknown)

        assertEquals(listOf<Effect>(Unknown), unhandled)
    }

    @Test
    fun duplicateHandlerIsRejectedAtBuildTime() {
        assertFailsWith<IllegalArgumentException> {
            effectHandlers {
                on<Open> { }
                on<Open> { }
            }
        }
    }

    @Test
    fun handlersOnASupertypeAndItsSubtypeAreRejected() {
        if (Effect::class.overlaps(Open::class)) {
            assertFailsWith<IllegalArgumentException> {
                effectHandlers {
                    on<Effect> { }
                    on<Open> { }
                }
            }
        } else {
            // Kotlin/Native can’t compare classes, so the overlap fails when the effect arrives.
            val handlers = effectHandlers {
                on<Effect> { }
                on<Open> { }
            }
            assertFailsWith<IllegalStateException> { handlers.handle(Open(1)) }
        }
    }

    private interface Navigation : Effect
    private interface Tracking : Effect
    private data object OpenAndTrack : Navigation, Tracking

    @Test
    fun effectThatMatchesTwoUnrelatedHandlersIsRejected() {
        val handlers = effectHandlers {
            on<Navigation> {}
            on<Tracking> {}
        }

        assertFailsWith<IllegalStateException> { handlers.handle(OpenAndTrack) }
    }
}
