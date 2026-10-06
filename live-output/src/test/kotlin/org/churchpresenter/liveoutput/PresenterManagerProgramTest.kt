package org.churchpresenter.liveoutput

import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.core.models.qa.Question
import org.churchpresenter.core.models.scene.Scene
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.songs.SongBackground
import org.churchpresenter.core.models.songs.SongBackgroundType
import org.churchpresenter.dictionary.data.StrongsEntry
import org.churchpresenter.liveshow.BackgroundSource
import org.churchpresenter.liveshow.Cue
import org.churchpresenter.liveshow.Layer
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.models.Presenting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [PresenterManager.program] while it is still derived from the single live mode: each mode puts
 * what the outputs draw on that content's layer, and Bible and songs put their background up on the
 * background layer with it. A mode has its cue even before its content arrives, because the outputs
 * draw the mode's presenter regardless.
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
        assertEquals(
            mapOf(Layer.BACKGROUND to Cue.Background(BackgroundSource.BIBLE), Layer.SLIDE to Cue.Verses(verses)),
            program,
        )
    }

    @Test
    fun `a song section goes on the slide layer with its place in the song`() {
        val section = LyricSection(title = "Amazing Grace")
        val program = live(Presenting.LYRICS) {
            setDisplayedLyricSection(section, DisplayedSongPosition(listOf(section), sectionIndex = 2, lineIndex = 1))
        }
        assertEquals(Cue.Song(section, sectionIndex = 2, lineIndex = 1), program[Layer.SLIDE])
    }

    @Test
    fun `a song puts the songs background up, carrying the section's own`() {
        val own = SongBackground(type = SongBackgroundType.COLOR, color = "#336699")
        val section = LyricSection(title = "Amazing Grace", background = own)
        val program = live(Presenting.LYRICS) {
            setDisplayedLyricSection(section, DisplayedSongPosition(listOf(section)))
        }
        assertEquals(Cue.Background(BackgroundSource.SONGS, own), program[Layer.BACKGROUND])
        assertEquals(setOf(Layer.BACKGROUND, Layer.SLIDE), program.keys)
    }

    @Test
    fun `a picture goes on the media layer`() {
        assertEquals(
            mapOf(Layer.MEDIA to Cue.Picture("/pics/a.jpg")),
            live(Presenting.PICTURES) { setDisplayedImagePath("/pics/a.jpg") },
        )
    }

    @Test
    fun `pictures with none displayed yet still hold the media layer`() {
        assertEquals(mapOf(Layer.MEDIA to Cue.Picture(null)), live(Presenting.PICTURES))
    }

    @Test
    fun `a presentation slide goes on the slide layer`() {
        assertEquals(
            mapOf(Layer.SLIDE to Cue.PresentationSlide("deck.pptx", 4)),
            live(Presenting.PRESENTATION) { setLiveSlide("deck.pptx", 4) },
        )
    }

    @Test
    fun `a presentation with no live slide still holds the slide layer`() {
        assertEquals(mapOf(Layer.SLIDE to Cue.PresentationSlide(null, -1)), live(Presenting.PRESENTATION))
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
    fun `media with nothing playing still holds the media layer`() {
        assertEquals(mapOf(Layer.MEDIA to Cue.Video("")), live(Presenting.MEDIA))
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
    fun `a canvas with no scene still holds the slide layer`() {
        assertEquals(mapOf(Layer.SLIDE to Cue.SceneCue(null)), live(Presenting.CANVAS))
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
        assertEquals(mapOf(Layer.SLIDE to Cue.Dictionary(null)), live(Presenting.DICTIONARY))
    }

    @Test
    fun `program follows the mode as it changes`() {
        val pm = PresenterManager(showPresenterWindowInitially = false)
        pm.setWebsiteUrl("https://example.org")
        pm.setPresentingMode(Presenting.WEBSITE)
        assertEquals(mapOf(Layer.SLIDE to Cue.Web("https://example.org")), pm.program.value)
        pm.setPresentingMode(Presenting.STT)
        assertEquals(
            mapOf(Layer.SLIDE to Cue.Web("https://example.org"), Layer.CAPTIONS to Cue.Captions),
            pm.program.value,
            "captions go up over the slide",
        )
        pm.setPresentingMode(Presenting.NONE)
        assertTrue(pm.program.value.isEmpty())
    }


    @Test
    fun `only Bible and songs put a background up`() {
        Presenting.entries.forEach { mode ->
            val expected = when (mode) {
                Presenting.NONE -> 0
                Presenting.BIBLE, Presenting.LYRICS -> 2
                else -> 1
            }
            assertEquals(expected, live(mode).size, "$mode")
        }
    }
}
