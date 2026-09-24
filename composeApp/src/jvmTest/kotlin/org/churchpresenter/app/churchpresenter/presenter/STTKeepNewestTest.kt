package org.churchpresenter.app.churchpresenter.presenter

import kotlin.test.Test
import kotlin.test.assertEquals

/** How many caption segments an output keeps: the newest N, or every one at 0. */
class STTKeepNewestTest {

    @Test
    fun `only the newest are kept`() {
        assertEquals(listOf(4, 5), keepNewest(listOf(1, 2, 3, 4, 5), 2))
    }

    @Test
    fun `zero keeps every segment`() {
        assertEquals(listOf(1, 2, 3), keepNewest(listOf(1, 2, 3), 0))
    }

    @Test
    fun `a cap above the count keeps them all`() {
        assertEquals(listOf(1, 2), keepNewest(listOf(1, 2), 5))
    }
}
