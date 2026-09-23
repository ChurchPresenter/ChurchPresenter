package org.churchpresenter.settings

import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What a profile decides about content and layout: whether Bible and song text appear at all, and
 * whether the output it is assigned to is a full screen or a lower-third band.
 *
 * They are one-liners, which is exactly why they are worth pinning — they are read at render time
 * rather than stored, so a change to what counts as "off" or as "a band" silently changes what
 * every output following this profile does, and the only symptom is a screen showing the wrong
 * thing during a service.
 */
class OutputProfileTest {

    // ── Whether text appears ─────────────────────────────────────────────────────

    @Test
    fun `a profile shows bible and songs by default`() {
        val profile = OutputProfile()

        assertTrue(profile.showBible, "a fresh profile shows the service, not nothing")
        assertTrue(profile.showSongs)
    }

    @Test
    fun `switching a kind of content off hides it`() {
        val profile = OutputProfile(
            bibleMode = Constants.SONG_LANG_OFF,
            songMode = Constants.SONG_LANG_OFF,
        )

        assertFalse(profile.showBible)
        assertFalse(profile.showSongs)
    }

    @Test
    fun `every language mode other than off still shows`() {
        // "off" is the only mode that hides; the rest choose which language to render.
        listOf(Constants.SONG_LANG_BOTH, Constants.SONG_LANG_PRIMARY, "secondary").forEach { mode ->
            assertTrue(OutputProfile(bibleMode = mode).showBible, "bibleMode=$mode should still show")
            assertTrue(OutputProfile(songMode = mode).showSongs, "songMode=$mode should still show")
        }
    }

    @Test
    fun `the two kinds of content are switched independently`() {
        val bibleOnly = OutputProfile(songMode = Constants.SONG_LANG_OFF)

        assertTrue(bibleOnly.showBible, "an overflow profile can carry the reading without the lyrics")
        assertFalse(bibleOnly.showSongs)
    }

    // ── Full screen or a band across the bottom ─────────────────────────────────

    @Test
    fun `a profile is full screen by default`() {
        val profile = OutputProfile()

        assertFalse(profile.isLowerThird)
        assertFalse(profile.isLowerThirdVertical)
    }

    @Test
    fun `both band orientations count as a lower third`() {
        val horizontal = OutputProfile(displayMode = Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL)
        val vertical = OutputProfile(displayMode = Constants.DISPLAY_MODE_LOWER_THIRD_VERTICAL)

        assertTrue(horizontal.isLowerThird)
        assertTrue(vertical.isLowerThird, "a vertical band is still a band; layout branches on this")
    }

    @Test
    fun `a band on a portrait shape reports as vertical`() {
        val landscape = OutputProfile(
            displayMode = Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL,
            previewWidth = 1920,
            previewHeight = 1080,
        )
        val portrait = landscape.copy(previewWidth = 1080, previewHeight = 1920)

        assertFalse(landscape.isLowerThirdVertical, "a band with width to split sets its languages side by side")
        assertTrue(portrait.isLowerThirdVertical, "a portrait band has no width to split, so it stacks")
    }

    @Test
    fun `a full screen is never vertical, whatever its shape`() {
        // The flag only ever governed how a *band* arranges parallel translations, so a portrait
        // full screen -- which stacks by its own layout anyway -- must not claim it.
        val portraitFullScreen = OutputProfile(
            displayMode = Constants.DISPLAY_MODE_FULLSCREEN,
            previewWidth = 1080,
            previewHeight = 1920,
        )

        assertFalse(portraitFullScreen.isLowerThirdVertical)
    }

    @Test
    fun `a stage monitor is not a lower third`() {
        val stage = OutputProfile(displayMode = Constants.DISPLAY_MODE_STAGE_MONITOR)

        assertFalse(stage.isLowerThird, "the stage monitor has its own layout entirely")
        assertFalse(stage.isLowerThirdVertical)
    }

    @Test
    fun `an unrecognised display mode falls back to full screen`() {
        // A settings file from a newer build could name a mode this one has never heard of.
        val unknown = OutputProfile(displayMode = "some_future_mode")

        assertFalse(unknown.isLowerThird, "an unknown mode must render something rather than nothing")
    }

    @Test
    fun `content and layout are decided independently`() {
        val bandWithoutSongs = OutputProfile(
            displayMode = Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL,
            songMode = Constants.SONG_LANG_OFF,
        )

        assertTrue(bandWithoutSongs.isLowerThird)
        assertFalse(bandWithoutSongs.showSongs)
        assertTrue(bandWithoutSongs.showBible, "these decisions are made separately")
    }
}
