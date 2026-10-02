package org.churchpresenter.lowerthird

import kotlinx.coroutines.runBlocking
import org.churchpresenter.atem.AtemKey
import org.churchpresenter.settings.AtemSettings
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the sequence does when the switcher will not do as it is told. The key is driven through an
 * [AtemKeyDriver] the test supplies, so a refusal arrives at once instead of after the real client's
 * five-second UDP timeout.
 *
 * The lower third still goes up when its key does not: the operator is told why, and the screen
 * output is not held hostage to the switcher.
 */
class LowerThirdSequencerFailureTest {

    private val atem = AtemSettings(host = "10.0.0.9")
    private val clip = LowerThirdClip("Welcome", "{}", durationMs = 0L, pauseAtFrame = false, pauseDurationMs = 0L)
    private val key = LowerThirdKey(mixEffect = 0, keyer = 1)

    @AfterTest
    fun reset() = runBlocking { LowerThirdSequencer.stop() }

    @Test
    fun `a key that will not go on air is reported, and the lower third still runs`() = runBlocking {
        val error = LowerThirdSequencer.run(clip, key, atem, autoEnd = false) { _, _, _, _ ->
            throw IOException("No response from ATEM")
        }

        assertEquals("No response from ATEM", error)
        assertEquals("running:Welcome", LowerThirdSequencer.status.value)
    }

    @Test
    fun `a switcher in the wrong state is reported the same way`() = runBlocking {
        val error = LowerThirdSequencer.run(clip, key, atem, autoEnd = false) { _, _, _, _ ->
            throw IllegalStateException("keyer is busy")
        }

        assertEquals("keyer is busy", error)
    }

    @Test
    fun `a refusal with nothing to say is still named`() = runBlocking {
        val error = LowerThirdSequencer.run(clip, key, atem, autoEnd = false) { _, _, _, _ -> throw IOException() }

        assertEquals("ATEM unreachable", error)
    }

    @Test
    fun `a key that will not come off still ends the sequence`() = runBlocking {
        val calls = CopyOnWriteArrayList<Pair<AtemKey, Boolean>>()
        LowerThirdSequencer.run(clip, key, atem, autoEnd = false) { _, _, k, onAir ->
            calls += k to onAir
            if (!onAir) throw IOException("switcher went away")
        }

        LowerThirdSequencer.stop()

        assertEquals(listOf(true, false), calls.map { it.second }, "on, then an attempt at off")
        assertEquals(AtemKey(false, 0, 1), calls.last().first, "the same key it put on")
        assertEquals("idle", LowerThirdSequencer.status.value)
    }

    @Test
    fun `a key that failed to go on is not taken off again`() = runBlocking {
        val offAttempts = CopyOnWriteArrayList<Boolean>()
        LowerThirdSequencer.run(clip, key, atem, autoEnd = false) { _, _, _, onAir ->
            if (onAir) throw IOException("No response from ATEM") else offAttempts += onAir
        }

        LowerThirdSequencer.stop()

        assertTrue(offAttempts.isEmpty(), "there is no key on air to take off")
    }
}
