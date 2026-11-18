package com.mairwunnx.capsa

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LoadTest {

    @Test
    fun valueIsAvailableOnlyWhenLoaded() {
        assertEquals(5, Load.Loaded(5).valueOrNull())
        assertNull(Load.Loading.valueOrNull())
        assertNull(Load.Failed.valueOrNull())
    }

    @Test
    fun loadingAndFailedFitAnyType() {
        val loads: List<Load<String>> = listOf(Load.Loading, Load.Loaded("x"), Load.Failed)

        assertEquals(listOf(null, "x", null), loads.map { it.valueOrNull() })
    }
}
