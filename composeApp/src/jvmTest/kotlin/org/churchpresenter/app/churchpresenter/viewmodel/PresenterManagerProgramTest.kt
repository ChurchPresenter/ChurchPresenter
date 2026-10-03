package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.core.models.qa.Question
import org.churchpresenter.core.models.scene.Scene
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.dictionary.data.StrongsEntry
import org.churchpresenter.liveshow.Cue
import org.churchpresenter.liveshow.Layer
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.models.Presenting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [PresenterManager.program] while it is still derived from the single live mode: each mode puts
 * what the outputs draw on that content's layer, and nothing else is ever set.
 */
class PresenterManagerProgramTest {

    private fun live(mode: Presenting, content: PresenterManager.() -> Unit = {}): Map<Layer, Cue> =
        PresenterManager(showPresenterWindowInitially = false).run {
            setPresentingMode(mode)
            content()
            program.value
        }

    @Test
    fun `nothing presenting is nothing on air`() {
        assertTrue(live(Presenting.NONE).isEmpty())
    }

    @Test
    fun `verses go on the slide layer as the outputs draw them`() {
        val verses = listOf(SelectedVerse(bookName = "John", chapter = 3, verseNumber = 16))
        val program = live(Presenting.BIBLE) {
            setSelectedVerses(listOf(SelectedVerse(bookName = "Genesis")))
            setDisplayedVerses(verses)
        }
        assertEquals(mapOf(Layer.SLIDE to Cue.Verses(verses)), program)
    }

    @Test
    fun `a song section goes on the slide layer with its place in the song`() {
        val section = LyricSection(title = "Amazing Grace")
        val program = live(Presenting.LYRICS) {
            setDisplayedLyricSection(section, DisplayedSongPosition(listOf(section), sectionIndex = 2, lineIndex = 1))
        }
        assertEquals(mapOf(Layer.SLIDE to Cue.Song(section, sectionIndex = 2, lineIndex = 1)), program)
    }

    @Test
    fun `a picture goes on the media layer`() {
        assertEquals(
            mapOf(Layer.MEDIA to Cue.Picture("/pics/a.jpg")),
            live(Presenting.PICTURES) { setDisplayedImagePath("/pics/a.jpg") },
        )
    }

    @Test
    fun `pictures with none displayed yet put nothing on air`() {
        assertTrue(live(Presenting.PICTURES).isEmpty())
    }

    @Test
    fun `a presentation slide goes on the slide layer`() {
        assertEquals(
            mapOf(Layer.SLIDE to Cue.PresentationSlide("deck.pptx", 4)),
            live(Presenting.PRESENTATION) { setLiveSlide("deck.pptx", 4) },
        )
    }

    @Test
    fun `a presentation with no live slide puts nothing on air`() {
        assertTrue(live(Presenting.PRESENTATION).isEmpty())
    }

    @Test
    fun `a video goes on the media layer`() {
        assertEquals(
            mapOf(Layer.MEDIA to Cue.Video("clip.mp4")),
            live(Presenting.MEDIA) { setCurrentMedia("clip.mp4", Constants.MEDIA_TYPE_LOCAL) },
        )
    }

    @Test
    fun `audio goes on the audio layer, not the media layer`() {
        assertEquals(
            mapOf(Layer.AUDIO to Cue.Audio("hymn.mp3")),
            live(Presenting.MEDIA) { setCurrentMedia("hymn.mp3", Constants.MEDIA_TYPE_AUDIO) },
        )
    }

    @Test
    fun `media with nothing playing puts nothing on air`() {
        assertTrue(live(Presenting.MEDIA).isEmpty())
    }

    @Test
    fun `a lower third goes on the graphics layer`() {
        val program = live(Presenting.LOWER_THIRD) {
            setLottieContent("{}", pauseAtFrame = false, pauseFrame = 0f, pauseDurationMs = 0, presetName = "Speaker")
        }
        assertEquals(mapOf(Layer.GRAPHICS to Cue.LowerThird("Speaker")), program)
    }

    @Test
    fun `an announcement goes on the announcements layer as displayed`() {
        assertEquals(
            mapOf(Layer.ANNOUNCEMENTS to Cue.Announcement("Welcome")),
            live(Presenting.ANNOUNCEMENTS) { setDisplayedAnnouncementText("Welcome") },
        )
    }

    @Test
    fun `a web page goes on the slide layer`() {
        assertEquals(
            mapOf(Layer.SLIDE to Cue.Web("https://example.org")),
            live(Presenting.WEBSITE) { setWebsiteUrl("https://example.org") },
        )
    }

    @Test
    fun `a canvas scene goes on the slide layer`() {
        val scene = Scene(id = "s1", name = "Welcome")
        assertEquals(mapOf(Layer.SLIDE to Cue.SceneCue(scene)), live(Presenting.CANVAS) { setActiveScene(scene) })
    }

    @Test
    fun `a canvas with no scene puts nothing on air`() {
        assertTrue(live(Presenting.CANVAS).isEmpty())
    }

    @Test
    fun `a question goes on the slide layer, and Q&A without one still holds it`() {
        val question = Question(id = "q1", text = "Why?", timestamp = 0)
        assertEquals(
            mapOf(Layer.SLIDE to Cue.QuestionCue(question)),
            live(Presenting.QA) { setDisplayedQuestion(question) },
        )
        assertEquals(mapOf(Layer.SLIDE to Cue.QuestionCue(null)), live(Presenting.QA))
    }

    @Test
    fun `captions go on their own layer`() {
        assertEquals(mapOf(Layer.CAPTIONS to Cue.Captions), live(Presenting.STT))
    }

    @Test
    fun `a dictionary entry goes on the slide layer by its number`() {
        val entry = StrongsEntry(number = "H430", word = "", transliteration = "", pronunciation = "", definition = "")
        assertEquals(
            mapOf(Layer.SLIDE to Cue.Dictionary("H430")),
            live(Presenting.DICTIONARY) { setDisplayedDictionaryEntry(entry) },
        )
        assertTrue(live(Presenting.DICTIONARY).isEmpty())
    }

    @Test
    fun `program follows the mode as it changes`() {
        val pm = PresenterManager(showPresenterWindowInitially = false)
        pm.setPresentingMode(Presenting.STT)
        assertEquals(mapOf(Layer.CAPTIONS to Cue.Captions), pm.program.value)
        pm.setDisplayedAnnouncementText("Welcome")
        pm.setPresentingMode(Presenting.ANNOUNCEMENTS)
        assertEquals(mapOf(Layer.ANNOUNCEMENTS to Cue.Announcement("Welcome")), pm.program.value)
        pm.setPresentingMode(Presenting.NONE)
        assertTrue(pm.program.value.isEmpty())
    }

}
