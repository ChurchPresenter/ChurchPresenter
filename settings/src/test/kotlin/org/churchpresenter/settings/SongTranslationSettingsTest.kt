package org.churchpresenter.settings

import org.churchpresenter.core.models.songs.MAX_SONG_EXTRA_TRANSLATIONS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** How each extra language of a song is styled, and how a style is read and written per language. */
class SongTranslationSettingsTest {

    private fun style(color: String) = SongTextStyle(color = color)

    @Test
    fun `each element and output has its own stored profile`() {
        val settings = SongTranslationSettings()
        val written = SongTranslationElement.entries.flatMap { element ->
            listOf(false, true).map { lowerThird -> element to lowerThird }
        }.foldIndexed(settings) { index, acc, (element, lowerThird) ->
            acc.withStyle(element, lowerThird, style("#0000%02X".format(index)))
        }
        SongTranslationElement.entries.flatMap { e -> listOf(false, true).map { e to it } }
            .forEachIndexed { index, (element, lowerThird) ->
                assertEquals("#0000%02X".format(index), written.style(element, lowerThird).color)
            }
    }

    @Test
    fun `writing one profile leaves the others as they were`() {
        val edited = SongTranslationSettings().withStyle(SongTranslationElement.LYRICS, true, style("#123456"))
        assertEquals("#123456", edited.lyricsLowerThird.color)
        assertEquals(SongTextStyle(), edited.lyrics)
        assertEquals(SongTextStyle(), edited.title)
    }

    @Test
    fun `seeding switches the look on and fills every profile from the seed`() {
        val seeded = SongTranslationSettings().seededFrom { element, lowerThird ->
            style("${element.name}-$lowerThird")
        }
        assertTrue(seeded.overrideStyle)
        assertEquals("LYRICS-true", seeded.lyricsLowerThird.color)
        assertEquals("NEXT_SECTION-false", seeded.nextSection.color)
    }

    @Test
    fun `a language without its own look inherits the primary's`() {
        val settings = SongSettings()
        val primary = style("#ABCDEF")
        val inherited = settings.translationStyle(1, SongTranslationElement.LYRICS, false) { _, _ -> primary }
        assertSame(primary, inherited)
        val first = settings.translationStyle(0, SongTranslationElement.LYRICS, false) { _, _ -> primary }
        assertSame(primary, first)
    }

    @Test
    fun `a language with its own look draws it`() {
        val settings = SongSettings().withTranslationSettings(0) {
            it.seededFrom { _, _ -> style("#111111") }
                .withStyle(SongTranslationElement.TITLE, false, style("#222222"))
        }
        val primary = style("#ABCDEF")
        fun drawn(element: SongTranslationElement) =
            settings.translationStyle(1, element, false) { _, _ -> primary }.color
        assertEquals("#222222", drawn(SongTranslationElement.TITLE))
        assertEquals("#111111", drawn(SongTranslationElement.LYRICS))
        assertTrue(settings.languageOverridesStyleAt(0))
    }

    @Test
    fun `the list grows to reach the language being written and never past the maximum`() {
        val grown = SongSettings().withTranslationSettings(2) { it.copy(label = "Yoruba") }
        assertEquals(3, grown.translations.size)
        assertEquals("Yoruba", grown.translationSettings(2).label)
        assertEquals(SongTranslationSettings(), grown.translationSettings(0))
        assertEquals(
            SongTranslationSettings(),
            grown.translationSettings(9),
            "an unconfigured language reads as default",
        )

        val past = SongSettings().withTranslationSettings(MAX_SONG_EXTRA_TRANSLATIONS) { it.copy(label = "x") }
        assertEquals(emptyList(), past.translations)
        val before = SongSettings().withTranslationSettings(-1) { it.copy(label = "x") }
        assertEquals(emptyList(), before.translations)
    }

    private fun SongSettings.languageOverridesStyleAt(index: Int) = translationSettings(index).overrideStyle
}
