@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.stt

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import org.churchpresenter.sharedui.testing.renderedText

// ── Reading and driving what was rendered ───────────────────────────────────────────────────────

// renderedText/showsExactly/showsContainingText live in TabRenderedText.kt — they are shared with
// the other tab suites in this package.

/** A button, addressed by the content description its tooltip gives it. */
fun ComposeUiTest.sttButton(label: String) = onNodeWithContentDescription(label)

fun ComposeUiTest.hasSttButton(label: String): Boolean =
    onAllNodesWithContentDescription(label)
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
        .isNotEmpty()

/** The server-URL field — the only control on the tab that takes typed text. */
fun ComposeUiTest.urlField() = onAllNodes(hasSetTextAction())[0]

/**
 * Whether the url field can still be typed into.
 *
 * The tab disables it while a connection is up or in flight, and a disabled text field drops its
 * set-text action entirely — so "locked" is the absence of the field rather than a disabled node.
 */
fun ComposeUiTest.urlFieldIsEditable(): Boolean =
    onAllNodes(hasSetTextAction()).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()

/**
 * What the url field currently holds.
 *
 * A field's contents are `EditableText`, not `Text`, so they never appear in [renderedText] — an
 * assertion phrased against that would pass whatever the field said.
 */
fun ComposeUiTest.urlFieldText(): String =
    onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText))
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
        .firstNotNullOfOrNull { it.config.getOrNull(SemanticsProperties.EditableText)?.text }
        .orEmpty()
