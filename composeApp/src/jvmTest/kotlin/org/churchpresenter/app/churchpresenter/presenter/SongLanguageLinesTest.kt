package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.songs.SectionTranslation
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.SongTextStyle
import org.churchpresenter.settings.SongTranslationSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What each language of a slide contributes: the words now, the words next, and the look it draws in. */
class SongLanguageLinesTest {

    private val english = listOf("Amazing grace", "how sweet the sound")
    private val russian = listOf("О, благодать", "спасен тобой")
    private val ukrainian = listOf("Яка любов", "спасла мене")

    private fun section(
        lines: List<String> = english,
        translations: List<List<String>> = listOf(russian, ukrainian),
    ) = LyricSection(
        lines = lines,
        translations = translations.map { SectionTranslation(lines = it) },
    )

    private fun modes(
        lookAheadEnabled: Boolean = false,
        isLineMode: Boolean = false,
        laIsLineMode: Boolean = false,
        lineIndex: Int = -1,
    ) = SongSlideModes(lookAheadEnabled, isLineMode, laIsLineMode, lineIndex)

    // ── The slide's own lines ────────────────────────────────────────────

    @Test
    fun `a whole-section slide shows every line`() {
        assertEquals(english, slideLinesFor(english, modes()))
    }

    @Test
    fun `line mode shows the one line it names`() {
        assertEquals(listOf("how sweet the sound"), slideLinesFor(english, modes(isLineMode = true, lineIndex = 1)))
    }

    @Test
    fun `a line index outside the section falls back to the whole thing`() {
        assertEquals(english, slideLinesFor(english, modes(isLineMode = true, lineIndex = -1)))
        assertEquals(english, slideLinesFor(english, modes(isLineMode = true, lineIndex = 9)))
    }

    // ── The look-ahead ───────────────────────────────────────────────────

    @Test
    fun `line mode looks at the next line of this section first`() {
        val ahead = lookAheadLinesFor(
            english,
            nextLines = listOf("I once was lost"),
            modes = modes(lookAheadEnabled = true, isLineMode = true, laIsLineMode = true, lineIndex = 0),
        )

        assertEquals(listOf("how sweet the sound"), ahead)
    }

    @Test
    fun `it crosses into the next section only once this one is spent`() {
        val ahead = lookAheadLinesFor(
            english,
            nextLines = listOf("I once was lost", "but now am found"),
            modes = modes(lookAheadEnabled = true, isLineMode = true, laIsLineMode = true, lineIndex = 1),
        )

        assertEquals(listOf("I once was lost"), ahead)
    }

    @Test
    fun `the guard keeps a plain line-mode slide from drawing this section's next line`() {
        val ahead = lookAheadLinesFor(
            english,
            nextLines = listOf("I once was lost"),
            modes = modes(lookAheadEnabled = false, isLineMode = true, laIsLineMode = true, lineIndex = 0),
        )

        // Not "how sweet the sound", the line after this one: nobody asked to see one. What is
        // drawn is the ordinary next-section look-ahead, which this slide's own mode did not ask for
        // either -- the guard's job is the in-section line, and that is what it withholds.
        assertEquals(listOf("I once was lost"), ahead)
    }

    @Test
    fun `a whole-section look-ahead is the next section entire`() {
        val next = listOf("I once was lost", "but now am found")

        assertEquals(next, lookAheadLinesFor(english, next, modes(lookAheadEnabled = true)))
    }

    @Test
    fun `nothing follows the last section`() {
        assertTrue(lookAheadLinesFor(english, emptyList(), modes(lookAheadEnabled = true)).isEmpty())
    }

    // ── One block per language ───────────────────────────────────────────

    @Test
    fun `each configured language becomes a block, in the order they were asked for`() {
        val blocks = songLanguageBlocks(section(), nextSection = null, languages = listOf(0, 2), modes = modes())

        assertEquals(listOf(0, 2), blocks.map { it.index })
        assertEquals(english, blocks[0].lines)
        assertEquals(ukrainian, blocks[1].lines)
    }

    @Test
    fun `a language this song was never translated into is dropped, not drawn empty`() {
        val onlyEnglish = section(translations = listOf(emptyList()))

        val blocks = songLanguageBlocks(onlyEnglish, nextSection = null, languages = listOf(0, 1), modes = modes())

        assertEquals(listOf(0), blocks.map { it.index })
    }

    @Test
    fun `an output configured only for a missing language still shows the words`() {
        val onlyEnglish = section(translations = emptyList())

        val blocks = songLanguageBlocks(onlyEnglish, nextSection = null, languages = listOf(2), modes = modes())

        assertEquals(listOf(0), blocks.map { it.index })
        assertEquals(english, blocks.single().lines)
    }

    @Test
    fun `a block carries its own look-ahead from its own language`() {
        val next = section(lines = listOf("I once was lost"), translations = listOf(listOf("Я был слепой")))

        val blocks =
            songLanguageBlocks(section(), next, languages = listOf(0, 1), modes = modes(lookAheadEnabled = true))

        assertEquals(listOf("I once was lost"), blocks[0].lookAheadLines)
        assertEquals(listOf("Я был слепой"), blocks[1].lookAheadLines)
    }

    @Test
    fun `a block reports where its look-ahead starts once the two are drawn as one run`() {
        val next = section(lines = listOf("I once was lost"), translations = emptyList())

        val withAhead = songLanguageBlocks(section(), next, listOf(0), modes(lookAheadEnabled = true)).single()
        val without = songLanguageBlocks(section(), nextSection = null, languages = listOf(0), modes = modes()).single()

        assertEquals(2, withAhead.lookAheadStart)
        assertEquals(english + listOf("I once was lost"), withAhead.allLines)
        assertEquals(-1, without.lookAheadStart)
        assertEquals(english, without.allLines)
    }

    // ── The look one line is drawn in ────────────────────────────────────

    private val shadowMarker = Shadow(color = Color.Red)

    private fun styling(profile: SongTextStyle, autoFit: Int? = null, scale: Float = 1f, isKey: Boolean = false) =
        songLineStyling(profile, autoFit, scale, isKey) { _, _, _ -> shadowMarker }

    @Test
    fun `auto-fit may shrink the configured size but never grow it`() {
        val profile = SongTextStyle(fontSize = 70)

        assertEquals(40f, styling(profile, autoFit = 40).fontSize.value)
        assertEquals(70f, styling(profile, autoFit = 120).fontSize.value)
        assertEquals(70f, styling(profile, autoFit = null).fontSize.value)
    }

    @Test
    fun `the scale factor multiplies whatever size was settled on`() {
        assertEquals(35f, styling(SongTextStyle(fontSize = 70), scale = 0.5f).fontSize.value)
    }

    @Test
    fun `a key output draws every language white, whatever it is configured with`() {
        val red = SongTextStyle(color = "#FF0000")

        assertEquals(Color.White, styling(red, isKey = true).color)
        assertEquals(Color.Red, styling(red).color)
    }

    @Test
    fun `bold, italic and the shadow come from the profile`() {
        val plain = styling(SongTextStyle())
        val loud = styling(SongTextStyle(bold = true, italic = true, shadow = true))

        assertEquals(FontWeight.Normal, plain.textStyle.fontWeight)
        assertEquals(FontStyle.Normal, plain.textStyle.fontStyle)
        assertNull(plain.textStyle.shadow)
        assertEquals(FontWeight.Bold, loud.textStyle.fontWeight)
        assertEquals(FontStyle.Italic, loud.textStyle.fontStyle)
        assertEquals(shadowMarker, loud.textStyle.shadow)
    }

    // ── Which languages have a look of their own ─────────────────────────

    @Test
    fun `the primary never overrides itself`() {
        val settings = SongSettings(translations = listOf(SongTranslationSettings(overrideStyle = true)))

        assertEquals(false, settings.languageOverridesStyle(0))
    }

    @Test
    fun `a language overrides only when it says so`() {
        val settings = SongSettings(
            translations = listOf(
                SongTranslationSettings(overrideStyle = true),
                SongTranslationSettings(overrideStyle = false),
            ),
        )

        assertEquals(true, settings.languageOverridesStyle(1))
        assertEquals(false, settings.languageOverridesStyle(2))
        // Past the end of the list is a language with no settings, which cannot override.
        assertEquals(false, settings.languageOverridesStyle(4))
    }
}
