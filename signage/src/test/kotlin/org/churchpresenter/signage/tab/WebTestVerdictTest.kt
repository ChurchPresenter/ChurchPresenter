package org.churchpresenter.signage.tab

import org.churchpresenter.signage.core.PlayerMessage
import org.churchpresenter.signage.core.WebTestRun
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** What a screen's web page test is taken to say. */
class WebTestVerdictTest {
    private fun result(at15: Double, at30: Double?, cpu: Double? = 50.0, temp: Double? = null) =
        PlayerMessage.WebTestResult(
            listOfNotNull(WebTestRun(15, at15, cpu, temp), at30?.let { WebTestRun(30, it, cpu, temp) }),
        )

    @Test
    fun theVerdictFollowsWhatReachedTheScreen() {
        assertEquals(WebTestVerdict.SMOOTH, result(15.0, 27.0).verdict())
        assertEquals(WebTestVerdict.NORMAL_ONLY, result(15.0, 26.9).verdict())
        // Smooth enough but the processor flat out: not smooth, it would struggle with a real page.
        assertEquals(WebTestVerdict.NORMAL_ONLY, result(15.0, 29.0, cpu = 90.0).verdict())
        assertEquals(WebTestVerdict.SMOOTH, result(15.0, 29.0, cpu = null).verdict())
        assertEquals(WebTestVerdict.NORMAL_ONLY, result(12.0, null).verdict())
        assertEquals(WebTestVerdict.TOO_SLOW, result(11.9, 25.0).verdict())
        assertNull(PlayerMessage.WebTestResult(error = "no Chromium").verdict())
    }

    @Test
    fun onlyAHotProcessorIsMentioned() {
        assertNull(result(15.0, 30.0, temp = 79.9).hotDegrees())
        assertEquals(82, result(15.0, 30.0, temp = 82.5).hotDegrees())
        assertNull(result(15.0, 30.0).hotDegrees())
    }
}
