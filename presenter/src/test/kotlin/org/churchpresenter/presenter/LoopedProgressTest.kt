package org.churchpresenter.presenter

import kotlin.test.Test
import kotlin.test.assertEquals

class LoopedProgressTest {

    @Test
    fun `a file with no length stays where it was held`() {
        assertEquals(0.4f, loopedProgress(0.4f, 1_000_000_000L, 0f))
    }

    @Test
    fun `a loop runs on past the end and wraps to the start`() {
        assertEquals(0.5f, loopedProgress(0.25f, 250L, 1_000f), 1e-4f)
        assertEquals(0.25f, loopedProgress(0.75f, 500L, 1_000f), 1e-4f)
    }

    @Test
    fun `a hold before the start still lands inside the loop`() {
        assertEquals(0.75f, loopedProgress(-0.25f, 0L, 1_000f), 1e-4f)
    }
}
