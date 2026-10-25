@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import java.io.File

// Finding and pointing at controls in a settings window drawn in the tour's panel.

/** Points at [m] in the settings column, or, if this page has no such control, says the line over a caption. */
internal fun DemoTour.point(heading: String, sub: String, m: SemanticsMatcher) {
    val target = runCatching { seek(m) }.getOrNull()
    if (target != null) {
        panelNote(heading, sub, target)
    } else {
        File(System.getenv("CP_DEMO_OUT"), "missed.txt").appendText("$heading\n")
        caption(heading, sub)
    }
}

/** An entry of the page list down the left of the settings. */
internal fun DemoTour.rail(label: String) = panelItems(hasText(label)).minBy { it.bounds.left }

internal fun DemoTour.quietPanelClick(m: SemanticsMatcher) {
    panelItems(m).firstOrNull()?.node?.performClick()
    ui.waitForIdle()
}

/**
 * [m] in the settings column, scrolled into view: down the page first, then back up for what sat
 * above where it was left.
 */
internal fun DemoTour.seek(m: SemanticsMatcher): Target {
    fun found() = panelItems(m).filter { it.bounds.width > 0f }
    for (direction in listOf(1f, -1f)) {
        repeat(SEEK_STEPS) {
            found().firstOrNull()?.let { return panelReveal(m) }
            panelItems(hasScrollAction()).forEach {
                it.node.performSemanticsAction(SemanticsActions.ScrollBy) { by -> by(0f, direction * dp(SEEK_PX)) }
            }
            ui.waitForIdle()
        }
    }
    return panelReveal(m)
}

private const val SEEK_STEPS = 40
private const val SEEK_PX = 220f
