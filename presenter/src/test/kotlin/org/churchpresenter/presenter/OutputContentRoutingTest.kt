package org.churchpresenter.presenter

import org.churchpresenter.settings.MediaLook
import org.churchpresenter.settings.OutputLook
import org.churchpresenter.settings.SlideLook
import org.churchpresenter.settings.withLook
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
        look = OutputLook(
            media = MediaLook(pictures = false, video = false),
            slide = SlideLook(web = false, canvas = false, qa = false, dictionary = false),
            announcements = false,
            graphics = false,
            captions = false,
            messages = false,
            props = false,
        ),
    )

    private val switchFor: Map<Presenting, (OutputProfile) -> OutputProfile> = mapOf(
        Presenting.BIBLE to { p -> p.copy(bibleMode = Constants.SONG_LANG_BOTH) },
        Presenting.LYRICS to { p -> p.copy(songMode = Constants.SONG_LANG_BOTH) },
        Presenting.PICTURES to { p -> p.withLook { copy(media = media.copy(pictures = true)) } },
        Presenting.PRESENTATION to { p -> p.withLook { copy(media = media.copy(pictures = true)) } },
        Presenting.ANNOUNCEMENTS to { p -> p.withLook { copy(announcements = true) } },
        Presenting.LOWER_THIRD to { p -> p.withLook { copy(graphics = true) } },
        Presenting.MEDIA to { p -> p.withLook { copy(media = media.copy(video = true)) } },
        Presenting.WEBSITE to { p -> p.withLook { copy(slide = slide.copy(web = true)) } },
        Presenting.CANVAS to { p -> p.withLook { copy(slide = slide.copy(canvas = true)) } },
        Presenting.QA to { p -> p.withLook { copy(slide = slide.copy(qa = true)) } },
        Presenting.STT to { p -> p.withLook { copy(captions = true) } },
        Presenting.DICTIONARY to { p -> p.withLook { copy(slide = slide.copy(dictionary = true)) } },
        Presenting.MESSAGE to { p -> p.withLook { copy(messages = true) } },
        Presenting.PROPS to { p -> p.withLook { copy(props = true) } },
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
        val pictures = nothing.withLook { copy(media = media.copy(pictures = true)) }
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
