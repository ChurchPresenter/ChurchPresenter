package org.churchpresenter.app.churchpresenter.dialogs.tabs

import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Bible pane's "All" chip: one edit written to every translation, touching only the property
 * the operator changed, so a second language's own colour survives the whole stack being resized.
 */
class BibleAllTranslationsTest {

    @Test
    fun `All only stands with two or more translations to style`() {
        assertEquals(ALL_TRANSLATIONS, effectiveTranslationIndex(ALL_TRANSLATIONS, stackSize = 2))
        assertEquals(0, effectiveTranslationIndex(ALL_TRANSLATIONS, stackSize = 1))
        assertEquals(0, effectiveTranslationIndex(ALL_TRANSLATIONS, stackSize = 0))
    }

    @Test
    fun `a single translation's index is kept inside the stack`() {
        assertEquals(1, effectiveTranslationIndex(1, stackSize = 3))
        assertEquals(2, effectiveTranslationIndex(7, stackSize = 3))
    }

    @Test
    fun `an edit under All reaches every translation`() {
        val stack = BibleSettings().withTranslations(
            listOf("kjv.spb", "rst.spb").map { BibleTranslationSettings(fileName = it) },
        )
        val edited = stack.updateEveryTranslation { it.copy(showAbbreviation = true) }
        assertEquals(listOf(true, true), edited.translationList().map { it.showAbbreviation })
    }

    @Test
    fun `only the property that changed is written`() {
        val shown = BibleElementStyle(color = "#FFFFFF", fontSize = 70)
        val edited = shown.copy(fontSize = 90)
        val secondLanguage = BibleElementStyle(color = "#FFFF00", fontSize = 60)

        val result = secondLanguage.withChangesFrom(shown, edited)

        assertEquals(90, result.fontSize, "the change reached it")
        assertEquals("#FFFF00", result.color, "and its own colour survived")
    }

    @Test
    fun `nothing changed writes nothing`() {
        val mine = BibleElementStyle(color = "#FFFF00", bold = true)
        val shown = BibleElementStyle()
        assertEquals(mine, mine.withChangesFrom(shown, shown))
    }
}
