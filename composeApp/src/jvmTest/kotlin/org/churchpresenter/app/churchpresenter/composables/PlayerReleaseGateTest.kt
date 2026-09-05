package org.churchpresenter.app.churchpresenter.composables

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The guard that stopped a released VLC handle being paused 200 ms later.
 *
 * The production crash was `java.lang.Error: Invalid memory access` raised inside
 * `libvlc_media_player_pause`, from a `javax.swing.Timer` that outlived the composable — so both
 * halves matter here: that a released player is never touched, and that an `Error` from one that
 * slips through does not escape.
 */
class PlayerReleaseGateTest {

    @Test
    fun `runs the block while the player is alive`() {
        val gate = PlayerReleaseGate()
        var ran = false
        assertTrue(gate.ifLive { ran = true })
        assertTrue(ran)
        assertFalse(gate.isReleased)
    }

    @Test
    fun `does not touch the player once released`() {
        val gate = PlayerReleaseGate()
        gate.release()
        var ran = false
        assertFalse(gate.ifLive { ran = true })
        assertFalse(ran, "this is the freed-native-memory dereference the crash came from")
        assertTrue(gate.isReleased)
    }

    // The production failure is a bare java.lang.Error out of libvlc; throwing anything narrower
    // here would not exercise the case that a catch (_: Exception) misses.
    @Suppress("TooGenericExceptionThrown")
    @Test
    fun `swallows an Error thrown by a native call`() {
        // catch (_: Exception) would let this straight through; the crash was an Error.
        val gate = PlayerReleaseGate()
        assertFalse(gate.ifLive { throw Error("Invalid memory access") })
    }

    @Test
    fun `swallows an exception thrown by a native call`() {
        val gate = PlayerReleaseGate()
        assertFalse(gate.ifLive { error("player is in a bad state") })
    }

    @Test
    fun `releasing more than once is safe`() {
        val gate = PlayerReleaseGate()
        gate.release()
        gate.release()
        assertTrue(gate.isReleased)
        assertFalse(gate.ifLive { error("must not be called") })
    }

    @Test
    fun `a release part way through a sequence stops the calls after it`() {
        // The real ordering: onDispose releases, and whatever was already queued must go no
        // further. Anything that ran before the release still counts as having run.
        val gate = PlayerReleaseGate()
        val ran = mutableListOf<Int>()
        gate.ifLive { ran += 1 }
        gate.release()
        gate.ifLive { ran += 2 }
        gate.ifLive { ran += 3 }
        assertEquals(listOf(1), ran)
    }
}
