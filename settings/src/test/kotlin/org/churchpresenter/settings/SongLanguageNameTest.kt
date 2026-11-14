package org.churchpresenter.settings

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Naming one song language at a time, as the Songs tab's Languages menu does: the install-wide names
 * every song, profile and output reads.
 */
class SongLanguageNameTest {

    @Test
    fun `naming one language keeps the others`() {
        val named = SongSettings(languageNames = listOf("English", "Español")).withLanguageName(1, "Yoruba")

        assertEquals(listOf("English", "Yoruba"), named.languageNames)
    }

    @Test
    fun `a language past the named ones leaves the gap blank`() {
        val named = SongSettings(languageNames = listOf("English")).withLanguageName(2, " Deutsch ")

        assertEquals(listOf("English", "", "Deutsch"), named.languageNames)
        assertEquals("", named.languageLabel(1))
    }

    @Test
    fun `unnaming the last language stores nothing for it`() {
        val unnamed = SongSettings(languageNames = listOf("English", "Español")).withLanguageName(1, "")

        assertEquals(listOf("English"), unnamed.languageNames)
    }

    @Test
    fun `unnaming the only named language stores no names at all`() {
        val unnamed = SongSettings(languageNames = listOf("English")).withLanguageName(0, " ")

        assertEquals(emptyList(), unnamed.languageNames)
    }
}
