package org.churchpresenter.sharedui.utils

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** [SttClock]: where in the STT recording a moment on the local clock falls. */
class SttClockTest {

    @AfterTest
    fun forget() = SttClock.reset()

    @Test
    fun `unknown until STT has transcribed something`() {
        assertNull(SttClock.secondsAt(1_000_000))
        SttClock.observe(0.0, atMs = 1_000_000)
        assertNull(SttClock.secondsAt(1_000_000), "a recording at 0 s has told us nothing yet")
    }

    @Test
    fun `a moment maps onto the recording's own seconds`() {
        SttClock.observe(sttSeconds = 60.0, atMs = 1_000_000)
        assertEquals(60.0, SttClock.secondsAt(1_000_000))
        assertEquals(75.5, SttClock.secondsAt(1_015_500))
    }

    @Test
    fun `the least-lagged update wins`() {
        // Transcription runs behind the audio: the same moment of audio reported later says less
        SttClock.observe(sttSeconds = 60.0, atMs = 1_003_000)   // 3 s behind
        SttClock.observe(sttSeconds = 70.0, atMs = 1_011_000)   // 1 s behind -- the better one
        SttClock.observe(sttSeconds = 80.0, atMs = 1_022_000)   // 2 s behind
        assertEquals(80.0, SttClock.secondsAt(1_011_000 + 10_000))
    }

    @Test
    fun `reset forgets the clock`() {
        SttClock.observe(sttSeconds = 60.0, atMs = 1_000_000)
        SttClock.reset()
        assertNull(SttClock.secondsAt(1_000_000))
    }
}
