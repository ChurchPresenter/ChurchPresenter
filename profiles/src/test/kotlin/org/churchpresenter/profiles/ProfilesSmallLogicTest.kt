@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.profiles

import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.bible.PreviewVerse
import org.churchpresenter.bible.VerseTarget
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.songs.SongBackground
import org.churchpresenter.core.models.songs.SongBackgroundType
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.DictionarySettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.utils.FallbackOutputSize
import org.churchpresenter.sharedui.utils.OutputSize
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.background
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfilesSmallLogicTest {

    @Test
    fun `each dictionary part switches only its own field`() {
        DictionaryPart.entries.forEach { part ->
            val off = part.withShown(DictionarySettings(), false)
            assertEquals(false, part.shown(off), part.name)
            val others = DictionaryPart.entries - part
            others.forEach { assertEquals(it.shown(DictionarySettings()), it.shown(off), "${part.name} left ${it.name}") }
            assertEquals(true, part.shown(part.withShown(off, true)))
        }
    }

    @Test
    fun `only a file or a device above the band has a picker caption`() {
        listOf(Constants.BACKGROUND_IMAGE, Constants.BACKGROUND_VIDEO, Constants.BACKGROUND_CAMERA).forEach {
            assertTrue(aboveBandMediaCaption(it) != null, it)
        }
        assertNull(aboveBandMediaCaption(Constants.BACKGROUND_COLOR))
        assertNull(aboveBandMediaCaption(Constants.BACKGROUND_TRANSPARENT))
    }

    private fun stageDoc(vararg screens: ScreenAssignment) = AppSettings(
        projectionSettings = ProjectionSettings(
            outputProfiles = listOf(
                OutputProfile(id = "stage", displayMode = Constants.DISPLAY_MODE_STAGE_MONITOR),
                OutputProfile(id = "main"),
            ),
            screenAssignments = screens.toList(),
        ),
    )

    @Test
    fun `the stage preview takes the first stage monitor with a known size`() {
        val settings = stageDoc(
            ScreenAssignment(activeProfileId = "main", targetBoundsW = 1920, targetBoundsH = 1080),
            ScreenAssignment(activeProfileId = "stage", targetBoundsW = 0, targetBoundsH = 768),
            ScreenAssignment(activeProfileId = "stage", targetBoundsW = 1280, targetBoundsH = 0),
            ScreenAssignment(activeProfileId = "stage", targetBoundsW = 1280, targetBoundsH = 1024),
        )
        assertEquals(OutputSize(1280, 1024), stageMonitorPreviewOutputSize(settings))
    }

    @Test
    fun `with no sized stage monitor the stage preview falls back rather than borrowing the projector`() {
        val settings = stageDoc(ScreenAssignment(activeProfileId = "main", targetBoundsW = 1024, targetBoundsH = 768))
        assertEquals(FallbackOutputSize, stageMonitorPreviewOutputSize(settings))
    }

    @Test
    fun `a gradient tile with no far colour clears it, and the own tile keeps a custom colour`() {
        val start = SongBackground(type = SongBackgroundType.GRADIENT, color = "#123456", colorEnd = "#654321")
        val solid = ColorSwatchDef(Res.string.background, color = "#000000")
        assertEquals(SongBackground(type = SongBackgroundType.COLOR, color = "#000000", colorEnd = "#654321"), solid.applyTo(start))
        val own = ColorSwatchDef(Res.string.background, color = "#abcdef", own = true)
        assertEquals("#123456", own.applyTo(start, namedColors = setOf("#000000")).color)
        assertEquals("#abcdef", own.applyTo(start, namedColors = setOf("#123456")).color)
        val gradient = ColorSwatchDef(Res.string.background, color = "#111111", colorEnd = "#222222")
        val applied = gradient.applyTo(SongBackground())
        assertEquals(SongBackgroundType.GRADIENT, applied.type)
        assertEquals("#222222", applied.colorEnd)
    }

    private fun sample(
        translations: List<BibleTranslationSettings>,
        verses: Map<String, Map<VerseTarget, PreviewVerse>>,
        slot: PreviewSampleSlot,
        titles: Map<String, String> = emptyMap(),
    ): List<SelectedVerse> {
        var out: List<SelectedVerse> = emptyList()
        runComposeUiTest {
            setContent { out = bibleSampleVerses(translations, verses, slot, titles) }
            waitForIdle()
        }
        return out
    }

    @Test
    fun `a translation quotes its own verse, and falls back to the sample where it has none`() {
        val target = BIBLE_PREVIEW_TARGETS.getValue(PreviewSampleSlot.MEDIUM)
        val own = PreviewVerse(bookName = "Иоанна", chapter = 3, verseNumber = 16, text = "Ибо так возлюбил")
        val blankBook = PreviewVerse(bookName = " ", chapter = 3, verseNumber = 16, text = "For God so loved")
        val verses = sample(
            listOf(
                BibleTranslationSettings(fileName = "rst.spb"),
                BibleTranslationSettings(fileName = "blank.spb"),
                BibleTranslationSettings(fileName = "missing.spb"),
            ),
            mapOf("rst.spb" to mapOf(target to own), "blank.spb" to mapOf(target to blankBook)),
            PreviewSampleSlot.MEDIUM,
            titles = mapOf("rst.spb" to "Russian Synodal"),
        )
        assertEquals(listOf("rst.spb", "blank.spb", "missing.spb"), verses.map { it.translationFileName })
        assertEquals("Иоанна", verses[0].bookName)
        assertEquals("Ибо так возлюбил", verses[0].verseText)
        assertEquals(verses[2].bookName, verses[1].bookName, "a blank book name reads as the sample's")
        assertEquals("For God so loved", verses[1].verseText)
        assertEquals(target.chapter, verses[2].chapter)
        assertEquals(target.verse, verses[2].verseNumber)
        assertTrue(verses[2].verseText.isNotBlank())
    }

    @Test
    fun `each sample length quotes its own verse`() {
        val texts = PreviewSampleSlot.entries.associateWith { slot ->
            sample(listOf(BibleTranslationSettings(fileName = "x.spb")), emptyMap(), slot).single()
        }
        assertEquals(3, texts.values.map { it.verseText }.distinct().size)
        PreviewSampleSlot.entries.forEach { slot ->
            val target = BIBLE_PREVIEW_TARGETS.getValue(slot)
            assertEquals(target.chapter to target.verse, texts.getValue(slot).let { it.chapter to it.verseNumber })
        }
    }

    @Test
    fun `each sample length gives the song preview its own lyrics and its own title slide`() {
        var lyrics: Map<PreviewSampleSlot, List<LyricSection>> = emptyMap()
        runComposeUiTest {
            setContent { lyrics = PreviewSampleSlot.entries.associateWith { songSampleSections(it) } }
            waitForIdle()
        }
        lyrics.values.forEach { assertTrue(it.isNotEmpty()) }
        assertEquals(3, lyrics.values.distinct().size)
        val titles = PreviewSampleSlot.entries.map { titleSlideSample(SongSettings(), it).title }
        assertEquals(3, titles.distinct().size)
        assertTrue(titles.all { it.isNotBlank() })
    }
}
