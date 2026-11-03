package org.churchpresenter.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The STT tab's own preview settings: one per install, never an output's, and read back as written. */
class STTTabViewTest {

    @Test
    fun `the tab's view is the document's, whatever a profile carries`() {
        val ours = STTTabView(shows = STTTabView.SHOWS_TRANSLATION, maxSegments = 0, fontSize = 20)
        val global = AppSettings(sttSettings = STTSettings(tab = ours))
        val profile = OutputProfile(sttSettings = STTSettings(tab = STTTabView(layout = STTTabView.LAYOUT_STACKED)))

        assertEquals(ours, global.resolvedFor(profile).sttSettings.tab)
        assertTrue("tab" in STT_GLOBAL_KEYS)
    }

    @Test
    fun `it reads back as it was written`() {
        val view = STTTabView(
            shows = STTTabView.SHOWS_TRANSCRIPTION,
            maxSegments = 12,
            showInProgress = false,
            showTranslationInProgress = false,
            wordHighlighting = false,
            fontSize = 22,
            layout = STTTabView.LAYOUT_STACKED,
        )
        val text = settingsJson.encodeToString(STTTabView.serializer(), view)
        assertEquals(view, settingsJson.decodeFromString(STTTabView.serializer(), text))
        assertNotEquals(view, view.copy(fontSize = 23))
        assertEquals(view.hashCode(), view.copy().hashCode())
    }

    @Test
    fun `an older document without one gets the defaults`() {
        val read = settingsJson.decodeFromString(STTSettings.serializer(), """{"serverUrl":"http://x"}""")
        assertEquals(STTTabView(), read.tab)
        assertEquals(STTTabView.SHOWS_BOTH, read.tab.shows)
        assertEquals(STTTabView.LAYOUT_SIDE_BY_SIDE, read.tab.layout)
        assertTrue(read.tab.maxSegments in STTTabView.MAX_SEGMENTS && read.tab.fontSize in STTTabView.FONT_SIZES)
    }
}
