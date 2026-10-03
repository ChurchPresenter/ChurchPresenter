package org.churchpresenter.sharedui.composables

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SectionLabelTest {

    @Test
    fun `the four inked kinds are recognised and everything else reads as a verse`() {
        assertEquals(SongSectionKind.CHORUS, sectionKindOf("Chorus"))
        assertEquals(SongSectionKind.BRIDGE, sectionKindOf("Bridge 2"))
        assertEquals(SongSectionKind.TAG, sectionKindOf("Coda"))
        assertEquals(SongSectionKind.VERSE, sectionKindOf("Verse 1"))
        assertEquals(SongSectionKind.VERSE, sectionKindOf("Intro"))
        assertEquals(SongSectionKind.VERSE, sectionKindOf(""))
    }

    @Test
    fun `a pre-chorus is not a chorus`() = assertEquals(SongSectionKind.VERSE, sectionKindOf("Pre-Chorus"))

    @Test
    fun `each kind has an ink of its own`() = runComposeUiTest {
        val inks = mutableMapOf<SongSectionKind, Color>()
        setContent {
            ChurchPresenterTheme(themeMode = ThemeMode.LIGHT) {
                SongSectionKind.entries.forEach { inks[it] = SectionInk.of(it) }
            }
        }
        waitForIdle()

        assertEquals(4, inks.values.toSet().size)
    }

    @Test
    fun `the label is upper-cased and a single slide carries no count`() = runComposeUiTest {
        setContent { ChurchPresenterTheme(themeMode = ThemeMode.DARK) { SectionLabelRow("Chorus") } }

        assertTrue(onAllNodes(hasText("CHORUS")).fetchSemanticsNodes().isNotEmpty())
        assertFalse(onAllNodes(hasText("1/1")).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `a split section names which slide it is`() = runComposeUiTest {
        setContent {
            ChurchPresenterTheme(themeMode = ThemeMode.LIGHT) {
                SectionLabelRow("Verse 2", slideIndex = 1, slideCount = 3)
            }
        }

        assertTrue(onAllNodes(hasText("VERSE 2")).fetchSemanticsNodes().isNotEmpty())
        assertTrue(onAllNodes(hasText("2/3")).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `a zone label without a colour still renders upper-cased`() = runComposeUiTest {
        setContent { ChurchPresenterTheme(themeMode = ThemeMode.LIGHT) { ZoneLabel("preview") } }

        assertTrue(onAllNodes(hasText("PREVIEW")).fetchSemanticsNodes().isNotEmpty())
    }
}
