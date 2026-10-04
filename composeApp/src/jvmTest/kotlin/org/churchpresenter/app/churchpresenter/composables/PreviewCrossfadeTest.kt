package org.churchpresenter.app.churchpresenter.composables

import org.churchpresenter.settings.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals

class PreviewCrossfadeTest {

    private fun settings(bibleFade: Boolean, bibleMs: Float, songFade: Boolean, songMs: Float) = AppSettings().let {
        it.copy(
            bibleSettings = it.bibleSettings.copy(crossfade = bibleFade, transitionDuration = bibleMs),
            songSettings = it.songSettings.copy(crossfade = songFade, transitionDuration = songMs),
        )
    }

    @Test
    fun `with neither crossfading the tile cuts`() {
        assertEquals(0, previewCrossfadeMs(settings(false, 800f, false, 900f)))
    }

    @Test
    fun `only the transition that crossfades sets the length`() {
        assertEquals(800, previewCrossfadeMs(settings(true, 800f, false, 1_500f)))
        assertEquals(900, previewCrossfadeMs(settings(false, 1_500f, true, 900f)))
    }

    @Test
    fun `with both crossfading the longer one wins`() {
        assertEquals(1_200, previewCrossfadeMs(settings(true, 1_200f, true, 600f)))
    }

    @Test
    fun `a very short crossfade is still at least a tenth of a second`() {
        assertEquals(100, previewCrossfadeMs(settings(true, 20f, false, 0f)))
    }
}
