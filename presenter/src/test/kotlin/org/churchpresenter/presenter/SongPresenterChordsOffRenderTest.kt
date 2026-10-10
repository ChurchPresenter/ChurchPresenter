package org.churchpresenter.presenter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * With chords switched off, a chorded song draws exactly what the same words do without chords:
 * no chord name, no `[G]`, and no room left for a chord row. Held pixel for pixel against the plain
 * verse rather than by a second, identical screenshot -- and against the chords-on picture, so the
 * comparison cannot pass because chords never draw.
 */
class SongPresenterChordsOffRenderTest {

    private companion object {
        const val SETTLE_FRAMES = 5
        const val FRAME_NANOS = 16_666_667L
    }

    private val words = listOf("Amazing grace how sweet the sound", "That saved a wretch like me")
    private val chorded = listOf("[G]Amazing [C]grace how [G]sweet the sound", "That [D]saved a [G]wretch like me")

    /** The verse, carrying [chords] as the app does: the words in `lines`, the chart beside them. */
    private fun section(chords: List<String>) = LyricSection(
        header = "[Verse 1]",
        title = "Amazing Grace",
        songNumber = 42,
        type = Constants.SECTION_TYPE_VERSE,
        lines = words,
        chordLines = chords,
    )

    private fun pixels(content: @Composable () -> Unit): ByteArray {
        val scene = ImageComposeScene(960, 540, Density(1f)) {
            Box(Modifier.fillMaxSize().background(Color.Black)) { content() }
        }
        return try {
            // Auto-fit settles over a few frames; the picture compared is the one it settles on.
            repeat(SETTLE_FRAMES) { scene.render(it * FRAME_NANOS) }
            scene.render(SETTLE_FRAMES * FRAME_NANOS).peekPixels()!!.buffer.bytes
        } finally {
            scene.close()
        }
    }

    private fun song(chords: List<String>, showChords: Boolean) = pixels {
        SongPresenter(lyricSection = section(chords), appSettings = AppSettings(), showChords = showChords)
    }

    @Test
    fun `a chorded song with chords off draws exactly the plain verse`() {
        assertTrue(song(chorded, showChords = false).contentEquals(song(emptyList(), showChords = false)))
    }

    @Test
    fun `with chords on the same song draws differently`() {
        assertFalse(song(chorded, showChords = true).contentEquals(song(emptyList(), showChords = false)))
    }
}
