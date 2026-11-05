package org.churchpresenter.sharedui.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [simulatedOutputCount]: which outputs open as windows because nothing real drives them. */
class SimulatedOutputsTest {

    @Test
    fun `simulating fills up to the configured count after the real outputs`() {
        assertEquals(2, simulatedOutputCount(1, 3, developmentFallback = false, simulateMissing = true))
        assertEquals(1, simulatedOutputCount(0, 1, developmentFallback = false, simulateMissing = true))
    }

    @Test
    fun `a reconnected monitor takes its slot back from the simulation`() {
        assertEquals(0, simulatedOutputCount(1, 1, developmentFallback = false, simulateMissing = true))
        assertEquals(0, simulatedOutputCount(3, 2, developmentFallback = false, simulateMissing = true))
    }

    @Test
    fun `a release build in dev mode without the setting simulates nothing`() {
        assertEquals(0, simulatedOutputCount(0, 2, developmentFallback = false, simulateMissing = false))
    }

    @Test
    fun `the count is offered whenever simulating is on, even with nothing to simulate`() {
        assertTrue(simulatesOutputs(realWindowCount = 2, developmentFallback = false, simulateMissing = true))
        assertTrue(simulatesOutputs(realWindowCount = 0, developmentFallback = true, simulateMissing = false))
        assertFalse(simulatesOutputs(realWindowCount = 1, developmentFallback = true, simulateMissing = false))
    }
}
