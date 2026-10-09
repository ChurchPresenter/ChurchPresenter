@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.web.tabs

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasClickAction
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every control on the Web tab a mouse can press has a name a screen reader can say — the app's
 * `AccessibleNamesTest` for this one tab, which is checked here rather than through the main window
 * because composing the tab there disturbs the app suite's later main-window tests in the same JVM.
 */
class WebTabAccessibleNamesTest {

    private fun SemanticsNode.name(): String = listOfNotNull(
        config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" "),
        config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text },
        config.getOrNull(SemanticsProperties.EditableText)?.text,
    ).joinToString(" ").trim()

    private fun ComposeUiTest.assertEveryControlNamed() {
        val unnamed = onAllNodes(hasClickAction()).fetchSemanticsNodes()
            .filter { it.name().isBlank() }
            .map { "${it.config.getOrNull(SemanticsProperties.TestTag) ?: "(no tag)"} at ${it.boundsInRoot}" }
        assertTrue(
            unnamed.isEmpty(),
            "${unnamed.size} controls have no accessible name:\n${unnamed.joinToString("\n")}",
        )
    }

    @Test
    fun `every control of the browser is named`() = webTab { _, _ -> assertEveryControlNamed() }

    @Test
    fun `every control of the engine-unavailable page is named`() =
        webTab(cefInitialized = false) { _, _ -> assertEveryControlNamed() }
}
