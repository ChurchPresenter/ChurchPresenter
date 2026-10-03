package org.churchpresenter.sharedui.utils

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.settings.SongSettings
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SongLanguageNamesTest {

    private fun namesFor(settings: SongSettings, slots: IntRange): List<String> {
        val names = mutableListOf<String>()
        runComposeUiTest {
            setContent { slots.forEach { names += songLanguageName(settings, it) } }
            waitForIdle()
        }
        return names
    }

    @Test
    fun `an unnamed language is called by its position`() =
        assertEquals(
            listOf("Language 1", "Language 2", "Language 3", "Language 4", "Language 4"),
            namesFor(SongSettings(), 0..4),
        )

    @Test
    fun `a named language keeps its name, trimmed, and a blank one falls back`() =
        assertEquals(
            listOf("English", "Language 2", "Español"),
            namesFor(SongSettings(languageNames = listOf(" English ", "  ", "Español")), 0..2),
        )
}
