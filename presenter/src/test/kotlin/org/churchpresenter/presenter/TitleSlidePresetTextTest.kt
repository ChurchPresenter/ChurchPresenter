package org.churchpresenter.presenter

import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.songs.SectionTranslation
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A title slide played through a Lower Third preset (#1017): the words it puts in the preset's
 * `Name`, `Info` and `Detail` layers, and how a preset is told from a band template.
 */
class TitleSlidePresetTextTest {

    private val titleSlide = LyricSection(
        type = Constants.SECTION_TYPE_TITLE_SLIDE,
        title = "O Come All Ye Faithful",
        songNumber = 112,
        author = "John Francis Wade",
        composer = "Frederick Oakeley",
        ccli = "31054",
        bpm = 96,
        translations = listOf(SectionTranslation(title = "Adeste Fideles")),
    )
    private val settings = SongSettings(titleSlideShowCcli = true, titleSlideShowTempo = true)
    private val presetLayers = setOf(PRESET_LAYER_NAME, PRESET_LAYER_INFO, PRESET_LAYER_DETAIL)

    private fun texts(
        section: LyricSection = titleSlide,
        layers: Set<String> = presetLayers,
        language: String = Constants.SONG_LANG_PRIMARY,
    ) = titleSlidePresetTexts(section, settings, language, layers)

    @Test
    fun `the number and title go in Name, the credits across Info and Detail`() {
        assertEquals(
            mapOf(
                PRESET_LAYER_NAME to "112 – O Come All Ye Faithful",
                PRESET_LAYER_INFO to "John Francis Wade  ·  Frederick Oakeley",
                PRESET_LAYER_DETAIL to "CCLI #31054  ·  ♩ = 96 BPM",
            ),
            texts(),
        )
    }

    @Test
    fun `every language's title shares the one Name line`() {
        val both = texts(language = Constants.SONG_LANG_BOTH)
        assertEquals("112 – O Come All Ye Faithful – Adeste Fideles", both[PRESET_LAYER_NAME])
    }

    @Test
    fun `a preset with one credit line takes every credit, and one with none still gets the title`() {
        val infoOnly = texts(layers = setOf(PRESET_LAYER_NAME, PRESET_LAYER_INFO))
        assertEquals(
            "John Francis Wade  ·  Frederick Oakeley  ·  CCLI #31054  ·  ♩ = 96 BPM",
            infoOnly[PRESET_LAYER_INFO],
        )
        assertEquals(setOf(PRESET_LAYER_NAME), texts(layers = setOf(PRESET_LAYER_NAME)).keys)
        assertEquals(setOf(PRESET_LAYER_DETAIL), texts(layers = setOf(PRESET_LAYER_DETAIL)).keys)
    }

    @Test
    fun `a credit line with nothing left for it is blanked rather than left as the sample`() {
        val oneCredit = titleSlide.copy(composer = "", ccli = "", bpm = 0)
        assertEquals("", texts(section = oneCredit)[PRESET_LAYER_DETAIL])
    }

    @Test
    fun `nothing is filled on a lyric slide or in a file without the preset's layers`() {
        assertEquals(emptyMap(), texts(section = titleSlide.copy(type = "verse")))
        assertEquals(emptyMap(), texts(layers = setOf("Text1", "Reference1")))
    }

    private fun template(vararg layers: String): BibleLottieTemplate {
        val json = layers.joinToString(",") { """{"ty": 5, "nm": "$it"}""" }
        val file = """{"fr": 30, "op": 90, "w": 1920, "h": 1080, "layers": [$json]}"""
        return checkNotNull(parseBibleLottieTemplate(file))
    }

    @Test
    fun `a preset is a file with the preset's text layers and none of the band's`() {
        assertTrue(template(PRESET_LAYER_NAME, PRESET_LAYER_INFO).isLowerThirdPreset)
        assertTrue(template(PRESET_LAYER_DETAIL).isLowerThirdPreset)
        assertFalse(template(PRESET_LAYER_NAME, "Text1").isLowerThirdPreset, "a band layer makes it a band")
        assertFalse(template("Text1", "Reference1").isLowerThirdPreset)
        assertFalse(template("Logo").isLowerThirdPreset)
    }

    @Test
    fun `reference lines are counted from the first up to a gap`() {
        assertEquals(0, template("Text1").referenceSlotCount)
        assertEquals(1, template("Text1", "Reference1", "Text2").referenceSlotCount)
        assertEquals(2, template("Reference1", "Reference2", "Reference4").referenceSlotCount)
        assertEquals(4, template("Reference1", "Reference2", "Reference3", "Reference4").referenceSlotCount)
    }
}
