package org.churchpresenter.presenter

import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.models.Presenting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OutputContentRoutingTest {

    private val nothing = OutputProfile(
        bibleMode = Constants.SONG_LANG_OFF, songMode = Constants.SONG_LANG_OFF,
        showPictures = false, showAnnouncements = false,
        showStreaming = false, showMedia = false, showWebsite = false, showCanvas = false,
        showQA = false, showSTT = false, showDictionary = false,
    )

    private val switchFor: Map<Presenting, (OutputProfile) -> OutputProfile> = mapOf(
        Presenting.BIBLE to { p -> p.copy(bibleMode = Constants.SONG_LANG_BOTH) },
        Presenting.LYRICS to { p -> p.copy(songMode = Constants.SONG_LANG_BOTH) },
        Presenting.PICTURES to { p -> p.copy(showPictures = true) },
        Presenting.PRESENTATION to { p -> p.copy(showPictures = true) },
        Presenting.ANNOUNCEMENTS to { p -> p.copy(showAnnouncements = true) },
        Presenting.LOWER_THIRD to { p -> p.copy(showStreaming = true) },
        Presenting.MEDIA to { p -> p.copy(showMedia = true) },
        Presenting.WEBSITE to { p -> p.copy(showWebsite = true) },
        Presenting.CANVAS to { p -> p.copy(showCanvas = true) },
        Presenting.QA to { p -> p.copy(showQA = true) },
        Presenting.STT to { p -> p.copy(showSTT = true) },
        Presenting.DICTIONARY to { p -> p.copy(showDictionary = true) },
    )

    @Test
    fun `each kind of content is shown only by its own switch`() {
        switchFor.forEach { (mode, turnOn) ->
            assertFalse(showsContentFor(mode, nothing), "$mode with every switch off")
            assertTrue(showsContentFor(mode, turnOn(nothing)), "$mode with its switch on")
        }
    }

    @Test
    fun `pictures and presentations share one switch`() {
        val pictures = nothing.copy(showPictures = true)
        assertEquals(
            listOf(Presenting.PICTURES, Presenting.PRESENTATION),
            Presenting.entries.filter { showsContentFor(it, pictures) },
        )
    }

    @Test
    fun `nothing live is shown nowhere, whatever the switches say`() {
        assertFalse(showsContentFor(Presenting.NONE, OutputProfile()))
    }
}
