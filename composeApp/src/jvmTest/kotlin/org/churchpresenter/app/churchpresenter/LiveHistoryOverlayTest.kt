package org.churchpresenter.app.churchpresenter

import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.sharedui.utils.LiveHistoryEntry
import kotlin.test.Test
import kotlin.test.assertEquals

class LiveHistoryOverlayTest {

    private fun verseUp() = PresenterManager().apply {
        setSelectedVerses(listOf(SelectedVerse(bookName = "Psalms", chapter = 23, verseNumber = 1)))
        setPresentingMode(Presenting.BIBLE)
    }

    @Test
    fun `an overlay going up is logged as itself`() {
        val pm = verseUp()
        pm.setPresentingMode(Presenting.LOWER_THIRD)

        assertEquals(
            LiveHistoryEntry(Presenting.LOWER_THIRD.name),
            liveHistoryEntryOf(pm, null, Presenting.LOWER_THIRD),
        )
    }

    @Test
    fun `an overlay change while the overlay is not up logs the slide under it`() {
        val pm = verseUp()

        val entry = liveHistoryEntryOf(pm, null, Presenting.LOWER_THIRD)

        assertEquals(Presenting.BIBLE.name, entry.contentType)
        assertEquals("Psalms 23:1", entry.reference)
    }
}
