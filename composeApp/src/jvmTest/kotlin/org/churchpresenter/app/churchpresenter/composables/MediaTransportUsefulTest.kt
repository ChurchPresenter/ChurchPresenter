package org.churchpresenter.app.churchpresenter.composables

import org.churchpresenter.app.churchpresenter.presenter.Presenting
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * When the live preview panel offers a transport for the loaded clip.
 *
 * The bug this pins: a finished clip is left **loaded**, rewound to 0 and not playing, and the old
 * rule was "anything live at all, plus a loaded clip". So playing a video to the end and then going
 * live with a song brought the seek bar back, showing 0:00 of a video that was over.
 *
 * The rule cannot simply be "media is live" either, which is the trap in the other direction: audio
 * goes on playing while the operator shows a song or a verse -- that is a feature, not an accident --
 * and its transport has to stay reachable.
 */
class MediaTransportUsefulTest {

    @Test
    fun `a video on screen offers its transport`() {
        assertTrue(mediaTransportUseful(isLoaded = true, isPlaying = true, presentingMode = Presenting.MEDIA))
    }

    @Test
    fun `a video on screen but paused still offers it`() {
        // Paused is exactly when an operator reaches for the transport.
        assertTrue(mediaTransportUseful(isLoaded = true, isPlaying = false, presentingMode = Presenting.MEDIA))
    }

    @Test
    fun `audio playing behind a song keeps its transport`() {
        assertTrue(mediaTransportUseful(isLoaded = true, isPlaying = true, presentingMode = Presenting.LYRICS))
    }

    @Test
    fun `a finished clip under a live song offers nothing`() {
        // The reported bug, in one line: loaded, not playing, something else on screen.
        assertFalse(mediaTransportUseful(isLoaded = true, isPlaying = false, presentingMode = Presenting.LYRICS))
    }

    @Test
    fun `a finished clip with nothing live offers nothing`() {
        assertFalse(mediaTransportUseful(isLoaded = true, isPlaying = false, presentingMode = Presenting.NONE))
    }

    @Test
    fun `nothing loaded offers nothing, whatever is on screen`() {
        assertFalse(mediaTransportUseful(isLoaded = false, isPlaying = false, presentingMode = Presenting.MEDIA))
        assertFalse(mediaTransportUseful(isLoaded = false, isPlaying = true, presentingMode = Presenting.MEDIA))
    }

    @Test
    fun `a clip loaded and paused before going live offers nothing yet`() {
        // Loading leaves the clip paused on purpose; until it is live or playing there is nothing for
        // the panel's transport to act on, and the Media tab's own controls are where it is started.
        assertFalse(mediaTransportUseful(isLoaded = true, isPlaying = false, presentingMode = Presenting.BIBLE))
    }
}
