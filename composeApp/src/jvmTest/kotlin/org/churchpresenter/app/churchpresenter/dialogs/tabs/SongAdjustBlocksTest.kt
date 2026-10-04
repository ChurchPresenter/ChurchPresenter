package org.churchpresenter.app.churchpresenter.dialogs.tabs

import org.churchpresenter.presenter.SongStyleElement
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.SongLayoutExtras
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.TextBox
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SongAdjustBlocksTest {

    private class Picks {
        var element: CustomizeElement = CustomizeElement.SONG_LYRICS
        var slideElement: SongStyleElement = SongStyleElement.TITLE
        var language: SongStyleLanguage? = null

        fun targets(startOn: CustomizeElement = element) = SongTargets(
            element = Adjustable(startOn) { element = it },
            slideElement = Adjustable(slideElement) { slideElement = it },
            language = Adjustable(language) { language = it },
        )
    }

    private val bilingual = OutputProfile(songMode = Constants.SONG_LANG_BOTH)

    @Test
    fun `picking each block on a lyric slide points the rows at its element and language`() {
        val picks = Picks()
        val model = songAdjustModel(AppSettings(), bilingual, picks.targets()) {}
        val blocks = assertNotNull(model.blocks)

        val pickedElements = blocks.keys.indices.map { i ->
            blocks.onSelect(i)
            picks.element
        }.toSet()

        assertEquals(
            setOf(
                CustomizeElement.SONG_NUMBER, CustomizeElement.SONG_TITLE, CustomizeElement.SONG_SECTION_LABEL,
                CustomizeElement.SONG_LYRICS, CustomizeElement.SONG_LOOK_AHEAD, CustomizeElement.SONG_NEXT_SECTION,
            ),
            pickedElements,
        )
        assertNotNull(picks.language, "a per-language block points the rows at its language")
    }

    @Test
    fun `picking a block on the title slide points the slide's own element`() {
        val picks = Picks()
        val model = songAdjustModel(
            AppSettings(), bilingual, picks.targets(startOn = CustomizeElement.SONG_TITLE_SLIDE),
        ) {}
        val blocks = assertNotNull(model.blocks)

        val slideElements = blocks.keys.indices.map { i ->
            blocks.onSelect(i)
            picks.slideElement
        }.toSet()

        assertTrue(SongStyleElement.AUTHOR in slideElements, "credits are picked on the slide: $slideElements")
        assertEquals(CustomizeElement.SONG_TITLE_SLIDE, picks.element, "the strip stays on the title slide")
    }

    @Test
    fun `a lower third has a band and no region, and its boxes count as moves to reset`() {
        var draft = AppSettings(
            songSettings = SongSettings(
                layoutExtras = SongLayoutExtras(
                    textBoxes = mapOf(
                        "LYRICS#0@LT" to TextBox(enabled = true, 0f, 0f, 50f, 50f),
                        "TITLE" to TextBox(enabled = true, 0f, 0f, 50f, 50f),
                    ),
                ),
            ),
        )
        val lowerThird = OutputProfile(displayMode = Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL)
        val model = songAdjustModel(draft, lowerThird, Picks().targets()) { t -> draft = t(draft) }

        assertNull(model.region)
        assertNotNull(model.band)
        val positions = assertNotNull(model.positions)
        assertTrue(positions.moved)

        positions.onReset()

        val boxes = draft.songSettings.layoutExtras.textBoxes
        assertFalse(boxes.getValue("LYRICS#0@LT").enabled, "the band's box is turned off")
        assertTrue(boxes.getValue("TITLE").enabled, "the full screen's is left alone")
    }

    @Test
    fun `a full screen with nothing moved has nothing to reset`() {
        val model = songAdjustModel(AppSettings(), OutputProfile(), Picks().targets()) {}
        assertFalse(assertNotNull(model.positions).moved)
        assertNotNull(model.region)
        assertNull(model.band)
    }
}
