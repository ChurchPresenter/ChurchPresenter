@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasClickAction
import org.churchpresenter.sharedui.models.Tabs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every control a mouse can press has a name a screen reader can say: the main window, one tab at a
 * time, with no clickable node that carries neither a content description nor text of its own or
 * of what it merges. An icon-only button is the usual offender; it reads as "button" and nothing
 * else. On failure, each unnamed node's test tag and bounds say where to look.
 */
class AccessibleNamesTest : MainDesktopComposeHarness() {

    private fun SemanticsNode.name(): String {
        val description = config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString(" ")
        val text = config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
        val editable = config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()
        return listOf(description, text, editable).joinToString(" ").trim()
    }

    private fun ComposeUiTest.unnamedControls(): List<String> =
        onAllNodes(hasClickAction(), useUnmergedTree = false).fetchSemanticsNodes()
            .filter { it.name().isBlank() }
            .map { node ->
                val tag = node.config.getOrNull(SemanticsProperties.TestTag) ?: "(no tag)"
                "$tag at ${node.boundsInRoot}"
            }

    /** Every clickable control on the main window, showing only [tab], is named. */
    private fun assertEveryControlNamed(tab: Tabs) = root(showingOnly(tab), devMode = false) {
        val unnamed = unnamedControls()
        assertTrue(
            unnamed.isEmpty(),
            "${unnamed.size} controls on $tab have no accessible name:\n${unnamed.joinToString("\n")}",
        )
    }

    /**
     * One per tab, so a new tab cannot be left out. The Web tab is `:web`'s `WebTabAccessibleNamesTest`:
     * composing it through the main window here disturbs this suite's later main-window tests in the
     * same JVM.
     */
    @Test
    fun `every tab has a test of its own`() {
        val covered = this::class.java.declaredMethods.map { it.name }.toSet()
        val missing = (Tabs.entries - Tabs.WEB).filter { tab -> covered.none { it.startsWith("the ${tab.name} tab") } }
        assertTrue(missing.isEmpty(), "no accessible-names test for: $missing")
    }

    @Test
    fun `the BIBLE tab names every control`() = assertEveryControlNamed(Tabs.BIBLE)

    @Test
    fun `the SONGS tab names every control`() = assertEveryControlNamed(Tabs.SONGS)

    @Test
    fun `the PICTURES tab names every control`() = assertEveryControlNamed(Tabs.PICTURES)

    @Test
    fun `the PRESENTATION tab names every control`() = assertEveryControlNamed(Tabs.PRESENTATION)

    @Test
    fun `the MEDIA tab names every control`() = assertEveryControlNamed(Tabs.MEDIA)

    @Test
    fun `the LOWER_THIRD tab names every control`() = assertEveryControlNamed(Tabs.LOWER_THIRD)

    @Test
    fun `the ANNOUNCEMENTS tab names every control`() = assertEveryControlNamed(Tabs.ANNOUNCEMENTS)

    @Test
    fun `the CANVAS tab names every control`() = assertEveryControlNamed(Tabs.CANVAS)

    @Test
    fun `the QA tab names every control`() = assertEveryControlNamed(Tabs.QA)

    @Test
    fun `the STT tab names every control`() = assertEveryControlNamed(Tabs.STT)

    @Test
    fun `the CROSSWORD tab names every control`() = assertEveryControlNamed(Tabs.CROSSWORD)

    @Test
    fun `the DICTIONARY tab names every control`() = assertEveryControlNamed(Tabs.DICTIONARY)

    @Test
    fun `the COMPANION_SURFACE tab names every control`() = assertEveryControlNamed(Tabs.COMPANION_SURFACE)
}
