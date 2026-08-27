package org.churchpresenter.settings

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Renaming a translation: what is stored, what is handed to a picker, and what a caller watches. */
class BibleTranslationRenameTest {

    private fun stack(vararg translations: BibleTranslationSettings) =
        BibleSettings(storageDirectory = "/bibles").withTranslations(translations.toList())

    private fun renamed(name: String = "", abbreviation: String = "") = stack(
        BibleTranslationSettings(fileName = "kjv.spb", customName = name, customAbbreviation = abbreviation),
        BibleTranslationSettings(fileName = "rst.spb"),
    )

    @Test
    fun `a name typed with a trailing space is stored exactly as typed`() {
        // Trimming on the way in deleted the space as it was pressed, so "King James" stuck at
        // "King" and a two-word name could not be typed at all.
        val after = stack(BibleTranslationSettings(fileName = "kjv.spb"))
            .updateTranslation(0) { it.copy(customName = "King ") }

        assertEquals("King ", after.translationList().single().customName)
    }

    @Test
    fun `a name is trimmed where it is used`() {
        assertEquals(mapOf("kjv.spb" to "King James"), renamed(name = "  King James  ").customNames())
    }

    @Test
    fun `a blank name is not a rename to nothing`() {
        assertEquals(emptyMap(), renamed(name = "   ").customNames())
    }

    @Test
    fun `only the renamed translations reach the picker`() {
        assertEquals(mapOf("kjv.spb" to "Authorised"), renamed(name = "Authorised").customNames())
    }

    @Test
    fun `both halves of a rename are read back together`() {
        assertEquals("Authorised" to "AV", renamed("Authorised", "AV").customNameOf("kjv.spb"))
    }

    @Test
    fun `a translation that has not been renamed reads back empty`() {
        assertEquals("" to "", renamed(name = "Authorised").customNameOf("rst.spb"))
    }

    @Test
    fun `a file that is not in the stack reads back empty`() {
        assertEquals("" to "", renamed(name = "Authorised").customNameOf("niv.spb"))
    }

    @Test
    fun `the rename key changes when a name changes`() {
        assertTrue(renamed(name = "Authorised").customNameKey() != renamed().customNameKey())
    }

    @Test
    fun `the rename key changes when only the abbreviation changes`() {
        // The abbreviation is what labels the verse on screen, so a caller watching this key must
        // notice one on its own -- it was a cleared abbreviation that stayed on the output.
        assertTrue(renamed(abbreviation = "AV").customNameKey() != renamed().customNameKey())
    }

    @Test
    fun `the rename key is unchanged by an edit to anything else`() {
        val before = renamed(name = "Authorised").customNameKey()
        val after = renamed(name = "Authorised").updateTranslation(0) { it.copy(textFontSize = 90) }

        assertEquals(before, after.customNameKey())
    }

    @Test
    fun `a rename travels with its translation when the stack is reordered`() {
        val after = renamed(name = "Authorised", abbreviation = "AV").swapped()

        assertEquals(listOf("rst.spb", "kjv.spb"), after.translationList().map { it.fileName })
        assertEquals("Authorised" to "AV", after.customNameOf("kjv.spb"))
    }

    @Test
    fun `a rename survives a settings round trip`() {
        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
        val settings = AppSettings(bibleSettings = renamed("Authorised", "AV"))

        val restored = json.decodeFromString<AppSettings>(json.encodeToString(settings))

        assertEquals("Authorised" to "AV", restored.bibleSettings.customNameOf("kjv.spb"))
    }
}
