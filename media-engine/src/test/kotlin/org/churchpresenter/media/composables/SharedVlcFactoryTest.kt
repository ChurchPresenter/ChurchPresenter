package org.churchpresenter.media.composables

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The shared libVLC's arguments, and the wait a software player's stop goes through. Making the
 * factory itself loads VLC, which this suite never does.
 */
class SharedVlcFactoryTest {

    @Test
    fun `the shared factory gets the arguments vlcj gives a component's own`() {
        // vlcj keeps its defaults package-private, so they are read here rather than referenced
        // there: a vlcj upgrade that changes them fails this instead of changing playback quietly.
        val defaults = Class.forName("uk.co.caprica.vlcj.player.component.MediaPlayerComponentDefaults")
            .getDeclaredField("EMBEDDED_MEDIA_PLAYER_ARGS")
            .apply { isAccessible = true }
            .get(null) as Array<*>

        assertEquals(defaults.toList(), VLCJ_COMPONENT_ARGS)
    }

    @Test
    fun `a stop that fails still lets the load go on`() = runBlocking {
        var ran = false

        awaitOnPlayerThread(on = Runnable::run) {
            ran = true
            error("libvlc_media_player_stop on a player VLC already let go of")
        }

        assertTrue(ran, "the stop was tried, and the wait returned rather than throwing")
    }
}
