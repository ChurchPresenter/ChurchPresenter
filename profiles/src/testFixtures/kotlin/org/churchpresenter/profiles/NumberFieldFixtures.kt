@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.profiles

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.hasImeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.input.ImeAction

/**
 * Retypes the number field currently displaying [showing], and asserts the field then displays what
 * was typed. That holds even for a value the field rejects: `NumberSettingsTextField` always shows
 * what you typed and only withholds the `onValueChange` callback when the value is out of range, so
 * the caller can assert the stored setting separately either way.
 */
fun ComposeUiTest.retypeNumberField(showing: Int, to: Int) {
    onAllNodes(hasSetTextAction() and hasImeAction(ImeAction.Default) and hasText(showing.toString()))
        .firstOr("no number field is showing $showing")
        .performTextReplacement(to.toString())
    waitForIdle()
    assertNumberFieldShows(to, "the field just retyped")
}

/** Asserts some number field on the tab is displaying [value]. */
fun ComposeUiTest.assertNumberFieldShows(value: Int, what: String) {
    onAllNodes(hasSetTextAction() and hasImeAction(ImeAction.Default) and hasText(value.toString()))
        .firstOr("$what must display $value")
        .assertExists("$what must display $value")
}

private fun SemanticsNodeInteractionCollection.firstOr(message: String): SemanticsNodeInteraction {
    check(fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()) { message }
    return get(0)
}
