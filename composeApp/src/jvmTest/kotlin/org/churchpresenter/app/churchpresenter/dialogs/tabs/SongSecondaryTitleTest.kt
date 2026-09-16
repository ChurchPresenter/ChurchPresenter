package org.churchpresenter.app.churchpresenter.dialogs.tabs

import org.churchpresenter.settings.SongSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The second title's profile: the fallback that makes the language switch free to press, the write
 * that seeds both outputs, and the reset that puts it back to following the first.
 */
class SongSecondaryTitleTest {

    private val styled = SongSettings(
        titleColor = "#112233",
        titleFontSize = 44,
        titleLowerThirdColor = "#445566",
        titleLowerThirdFontSize = 21,
    )

    @Test
    fun `it follows the first title until something is written`() {
        assertFalse(styled.secondaryTitleLanguage.enabled)
        assertEquals("#112233", styled.secondaryTitleStyle(SongStyleTarget.FULL_SCREEN).color)
        assertEquals(44, styled.secondaryTitleStyle(SongStyleTarget.FULL_SCREEN).fontSize)
        assertEquals("#445566", styled.secondaryTitleStyle(SongStyleTarget.LOWER_THIRD).color)
    }

    @Test
    fun `writing one output seeds the other from the first title`() {
        val edited = styled.withSecondaryTitleStyle(
            SongStyleTarget.FULL_SCREEN,
            styled.elementStyle(SongStyleElement.TITLE, SongStyleTarget.FULL_SCREEN).copy(color = "#FF0000"),
        )
        assertTrue(edited.secondaryTitleLanguage.enabled)
        assertEquals("#FF0000", edited.secondaryTitleStyle(SongStyleTarget.FULL_SCREEN).color)
        // The band was never styled, and must not fall back to the class default now that the flag
        // covering both outputs has gone on.
        assertEquals("#445566", edited.secondaryTitleStyle(SongStyleTarget.LOWER_THIRD).color)
        assertEquals(21, edited.secondaryTitleStyle(SongStyleTarget.LOWER_THIRD).fontSize)
    }

    @Test
    fun `the first title is untouched by the second`() {
        val edited = styled.withSecondaryTitleStyle(
            SongStyleTarget.FULL_SCREEN,
            styled.elementStyle(SongStyleElement.TITLE, SongStyleTarget.FULL_SCREEN).copy(color = "#FF0000"),
        )
        assertEquals("#112233", edited.elementStyle(SongStyleElement.TITLE, SongStyleTarget.FULL_SCREEN).color)
    }

    @Test
    fun `reset puts it back to following the first`() {
        val edited = styled.withSecondaryTitleStyle(
            SongStyleTarget.FULL_SCREEN,
            styled.elementStyle(SongStyleElement.TITLE, SongStyleTarget.FULL_SCREEN).copy(color = "#FF0000"),
        )
        val reset = edited.withElementReset(
            SongStyleElement.TITLE,
            SongStyleTarget.FULL_SCREEN,
            SongStyleLanguage.SECONDARY,
        )
        assertFalse(reset.secondaryTitleLanguage.enabled)
        assertEquals("#112233", reset.secondaryTitleStyle(SongStyleTarget.FULL_SCREEN).color)
    }

    @Test
    fun `the language-aware accessors point at the right profile`() {
        val edited = styled
            .withElementStyle(
                SongStyleElement.TITLE,
                SongStyleTarget.FULL_SCREEN,
                SongStyleLanguage.SECONDARY,
                styled.elementStyle(SongStyleElement.TITLE, SongStyleTarget.FULL_SCREEN).copy(color = "#00FF00"),
            )
        assertEquals(
            "#00FF00",
            edited.elementStyle(SongStyleElement.TITLE, SongStyleTarget.FULL_SCREEN, SongStyleLanguage.SECONDARY).color,
        )
        assertEquals(
            "#112233",
            edited.elementStyle(SongStyleElement.TITLE, SongStyleTarget.FULL_SCREEN, SongStyleLanguage.PRIMARY).color,
        )
    }

    /** Only the two a song carries twice; asking for any other element's second gives its only one. */
    @Test
    fun `an element with no second language is unaffected by the switch`() {
        assertEquals(SECOND_LANGUAGE_ELEMENTS, listOf(SongStyleElement.LYRICS, SongStyleElement.TITLE))
        val number = styled.elementStyle(SongStyleElement.NUMBER, SongStyleTarget.FULL_SCREEN)
        assertEquals(
            number,
            styled.elementStyle(SongStyleElement.NUMBER, SongStyleTarget.FULL_SCREEN, SongStyleLanguage.SECONDARY),
        )
    }
}
