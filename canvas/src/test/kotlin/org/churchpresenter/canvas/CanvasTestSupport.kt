@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.canvas

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.churchpresenter.sharedui.testing.confirmColorDialogWith
import org.churchpresenter.sharedui.utils.FontCatalog
import org.churchpresenter.sharedui.utils.Utils
import kotlin.test.assertEquals

/** Opens the colour field showing [fromHex], types [toHex] and confirms -- the whole round trip. */
internal fun ComposeUiTest.recolor(fromHex: String, toHex: String) {
    fun showingOldColour() = onAllNodes(hasClickAction() and hasText(fromHex, ignoreCase = true))
        .fetchSemanticsNodes(atLeastOneRootRequired = false).size
    val before = showingOldColour()
    onAllNodes(hasClickAction() and hasText(fromHex))
        .firstOrFail("no colour field is showing $fromHex")
        .performScrollTo()
        .performClick()
    waitForIdle()
    confirmColorDialogWith(toHex)
    onAllNodes(hasClickAction() and hasText(toHex, ignoreCase = true))
        .firstOrFail("the colour field just edited must display $toHex")
        .assertExists("the colour field just edited must display $toHex")
    // Counted rather than asserted absent: several controls share a default colour, and only the
    // one that was edited should have stopped showing it.
    assertEquals(before - 1, showingOldColour(), "one fewer field must show $fromHex after the edit")
}

private fun SemanticsNodeInteractionCollection.firstOrFail(message: String): SemanticsNodeInteraction {
    check(fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()) { message }
    return get(0)
}

/**
 * A font family whose name appears in no other offerable family's name, so typing it into the font
 * picker filters the menu down to exactly one candidate.
 */
internal fun uniquelyNamedFont(): String {
    val fonts = Utils.getAvailableSystemFonts().filterNot { FontCatalog.isHidden(it) }
    return fonts.first { candidate -> fonts.count { it.contains(candidate, ignoreCase = true) } == 1 }
}

/** The font panel's search box announces itself with its own placeholder. */
internal const val FONT_SEARCH_LABEL = "Search fonts…"

/** [script] as a command line for the host's own shell: `cmd /c` on Windows, `sh -c` elsewhere. */
internal fun shellCommand(script: String): List<String> =
    if (System.getProperty("os.name").lowercase().contains("win")) listOf("cmd", "/c", script)
    else listOf("sh", "-c", script)
