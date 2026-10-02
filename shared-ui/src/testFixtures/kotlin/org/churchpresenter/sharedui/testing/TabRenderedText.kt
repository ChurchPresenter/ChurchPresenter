@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.sharedui.testing

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithContentDescription

/**
 * Reading back what a tab rendered, shared by every `*TabTestSupport` in this package.
 *
 * These live on their own rather than in one tab's support file because they say nothing about any
 * particular tab: two support files in the same package each declaring their own copy compiles in
 * isolation but makes every call site ambiguous once both are present.
 */

/** Every string on screen. */
fun ComposeUiTest.renderedText(): List<String> =
    onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
        .mapNotNull { it.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { t -> t.text } }

fun ComposeUiTest.showsExactly(text: String): Boolean = renderedText().any { it == text }

fun ComposeUiTest.showsContainingText(fragment: String): Boolean =
    renderedText().any { it.contains(fragment) }

/** The button a tab draws for [label], found by the content description it is announced with. */
fun ComposeUiTest.button(label: String) = onNodeWithContentDescription(label)
