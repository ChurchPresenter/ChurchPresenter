package org.churchpresenter.liveshow

import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.core.models.qa.Question
import org.churchpresenter.core.models.scene.Scene
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.settings.BackgroundConfig
import kotlin.test.Test
import kotlin.test.assertEquals

/** Which layer each kind of content lands on, as the layer-model note decided it. */
class CueTest {

    @Test
    fun `every cue lands on its decided layer`() {
        val expected = mapOf(
            Cue.Background(BackgroundConfig()) to Layer.BACKGROUND,
            Cue.Video("clip.mp4") to Layer.MEDIA,
            Cue.Picture("a.jpg") to Layer.MEDIA,
            Cue.Verses(listOf(SelectedVerse())) to Layer.SLIDE,
            Cue.Song(LyricSection(), sectionIndex = 0, lineIndex = 0) to Layer.SLIDE,
            Cue.PresentationSlide("deck.pptx", 3) to Layer.SLIDE,
            Cue.SceneCue(Scene(id = "s")) to Layer.SLIDE,
            Cue.Web("https://example.org") to Layer.SLIDE,
            Cue.QuestionCue(Question(id = "q", text = "Why?", timestamp = 0)) to Layer.SLIDE,
            Cue.Dictionary("H430") to Layer.SLIDE,
            Cue.Captions to Layer.CAPTIONS,
            Cue.LowerThird("Speaker") to Layer.GRAPHICS,
            Cue.Announcement("Welcome") to Layer.ANNOUNCEMENTS,
            Cue.Message("Parents of 42") to Layer.MESSAGES,
            Cue.Audio("song.mp3") to Layer.AUDIO,
        )
        expected.forEach { (cue, layer) -> assertEquals(layer, cue.layer, "$cue") }
        assertEquals(Cue::class.sealedSubclasses.size, expected.size, "a new cue needs its layer pinned here")
    }

    @Test
    fun `a cue keeps what it names`() {
        val config = BackgroundConfig(backgroundColor = "#123456")
        val verses = listOf(SelectedVerse(bookName = "John"))
        val section = LyricSection(title = "Amazing Grace")
        val scene = Scene(id = "s")
        val question = Question(id = "q", text = "Why?", timestamp = 0)
        assertEquals(config, Cue.Background(config).config)
        assertEquals("clip.mp4", Cue.Video("clip.mp4").url)
        assertEquals("a.jpg", Cue.Picture("a.jpg").path)
        assertEquals(verses, Cue.Verses(verses).verses)
        Cue.Song(section, sectionIndex = 2, lineIndex = 1).let {
            assertEquals(section, it.section)
            assertEquals(2, it.sectionIndex)
            assertEquals(1, it.lineIndex)
        }
        Cue.PresentationSlide("deck.pptx", 3).let {
            assertEquals("deck.pptx", it.fileName)
            assertEquals(3, it.index)
        }
        assertEquals(scene, Cue.SceneCue(scene).scene)
        assertEquals("https://example.org", Cue.Web("https://example.org").url)
        assertEquals(question, Cue.QuestionCue(question).question)
        assertEquals("H430", Cue.Dictionary("H430").number)
        assertEquals("Speaker", Cue.LowerThird("Speaker").name)
        assertEquals("Welcome", Cue.Announcement("Welcome").text)
        assertEquals("Parents of 42", Cue.Message("Parents of 42").text)
        assertEquals("song.mp3", Cue.Audio("song.mp3").url)
    }
}
