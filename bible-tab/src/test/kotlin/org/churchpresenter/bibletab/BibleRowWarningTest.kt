@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.bibletab

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.churchpresenter.bible.Bible
import org.churchpresenter.bible.SpbRowIssue
import org.churchpresenter.bible.SpbRowReport
import org.churchpresenter.settings.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The warning above the tab when a translation has verse rows with a damaged id.
 *
 * The fixture is the Bible the other suites use plus one Genesis 1:4 row whose id carries a stray
 * zero (`V0040`), which the loader cannot read — so the verse is missing, and this banner is the
 * only thing that says so.
 */
class BibleRowWarningTest {

    private val damaged = bibleFixture.trimEnd() + "\nB001C001V0040\t1\t1\t4\tAnd God saw the light.\n"
    private val title = "Some verses are missing from this Bible"

    private fun androidx.compose.ui.test.ComposeUiTest.isShown(text: String, substring: Boolean = false) =
        onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `a translation with a damaged id is named with how many verses`() = bibleTab(content = damaged) { _, _ ->
        assertTrue(isShown(title))
        assertTrue(isShown("test.spb", substring = true))
        assertTrue(isShown("1 verse(s) have a damaged ID", substring = true))
    }

    @Test
    fun `show verses lists the affected verse by book name and hides it again`() = bibleTab(content = damaged) { _, _ ->
        assertTrue(!isShown("Genesis 1:4", substring = true))
        onNodeWithText("Show verses").performClick()
        waitForIdle()
        assertTrue(isShown("Genesis 1:4", substring = true))
        onNodeWithText("Hide verses").performClick()
        waitForIdle()
        assertTrue(!isShown("Genesis 1:4", substring = true))
    }

    @Test
    fun `hide removes the warning for now and saves nothing`() = bibleTab(content = damaged) { _, reports ->
        onNodeWithText("Hide").performClick()
        waitForIdle()
        assertTrue(!isShown(title))
        assertEquals(0, reports.settingsChanges)
    }

    @Test
    fun `never remind me saves the file name and removes the warning`() = bibleTab(content = damaged) { _, reports ->
        onNodeWithText("Never remind me again").performClick()
        waitForIdle()
        assertTrue(!isShown(title))
        assertEquals(listOf("test.spb"), reports.settingsAfterChange?.bibleSettings?.hiddenRowWarnings)
    }

    @Test
    fun `a file the person chose never to be reminded about shows no warning`() = bibleTab(
        content = damaged,
        settings = { app: AppSettings ->
            app.copy(bibleSettings = app.bibleSettings.copy(hiddenRowWarnings = listOf("test.spb")))
        },
    ) { _, _ ->
        assertTrue(!isShown(title))
    }

    @Test
    fun `a clean translation shows no warning`() = bibleTab { _, _ ->
        assertTrue(!isShown(title))
    }

    @Test
    fun `rows beyond the kept examples are counted as more`() {
        val issues = (1..50).map { SpbRowIssue(it, "B001C001V${it}0", 1, 1, it) }
        val warning = BibleRowWarning("big.spb", "/bibles/big.spb", SpbRowReport(63, issues), Bible())
        assertEquals(13, warning.moreCount)
        assertEquals(50, warning.verses.size)
        assertEquals("1 1:1", warning.verses.first())
    }
}
