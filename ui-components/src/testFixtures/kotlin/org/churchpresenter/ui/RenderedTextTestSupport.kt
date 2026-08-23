@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsMatcher

/**
 * Reading back what a composable rendered, shared by every tab and settings suite that asserts on it.
 *
 * These live here rather than in any one support file because they say nothing about any particular
 * tab: two support files in the same package each declaring their own copy compiles in isolation but
 * makes every call site ambiguous once both are present, and a feature module of its own cannot see
 * a copy that lives in `:composeApp` at all.
 */

/** Every string on screen. */
fun ComposeUiTest.renderedText(): List<String> =
    onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
        .mapNotNull { it.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { t -> t.text } }

fun ComposeUiTest.showsExactly(text: String): Boolean = renderedText().any { it == text }

fun ComposeUiTest.showsContainingText(fragment: String): Boolean =
    renderedText().any { it.contains(fragment) }
