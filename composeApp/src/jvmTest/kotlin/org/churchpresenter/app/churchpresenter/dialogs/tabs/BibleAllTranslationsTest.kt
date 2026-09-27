package org.churchpresenter.app.churchpresenter.dialogs.tabs

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



}
