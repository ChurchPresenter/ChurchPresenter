package org.churchpresenter.signage.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A screen's health in its status, whether the GPU drew the web test, and the rotation feature. */
class HealthWireTest {
    @Test
    fun aStatusCarriesItsHealthAndAnOldOneReadsWithout() {
        val status =
            PlayerMessage.Status(cpuPercent = 23, memoryPercent = 61, temperatureC = 54.5, uptimeSeconds = 90_000)
        val text = SignageJson.encodeToString(PlayerMessage.serializer(), status)
        assertEquals(status, SignageJson.decodeFromString(PlayerMessage.serializer(), text))
        val old = SignageJson.decodeFromString(PlayerMessage.serializer(), """{"type":"status","cec":"working"}""")
        assertNull((old as PlayerMessage.Status).temperatureC)
    }

    @Test
    fun aWebTestSaysWhetherTheGpuDrewIt() {
        val result = PlayerMessage.WebTestResult(listOf(WebTestRun(15, 15.0)), gpu = true)
        val text = SignageJson.encodeToString(PlayerMessage.serializer(), result)
        assertEquals(result, SignageJson.decodeFromString(PlayerMessage.serializer(), text))
        assertEquals("rotation", Features.ROTATION)
    }
}
