@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.lowerthird

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick

// ── Reading and driving what was rendered ───────────────────────────────────────────────────────
// (renderedText/showsExactly/showsContainingText are shared — see TabRenderedText.kt)

/** A button, addressed by the content description its tooltip gives it. */
fun ComposeUiTest.ltButton(label: String): SemanticsNodeInteraction =
    onNodeWithContentDescription(label)

fun ComposeUiTest.hasLtButton(label: String): Boolean =
    onAllNodesWithContentDescription(label)
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
        .isNotEmpty()

/**
 * Selects a preset from the list by its name, and waits for its animation to finish loading.
 *
 * The wait is the point. Clicking a preset only starts the Lottie parse; `canPlay` — and with it
 * every action in the tab — stays false until that finishes, off the composition thread, so
 * `waitForIdle` can return with the preset chosen and nothing yet playable. Whether a test sees
 * that gap comes down to how much else the JVM was doing, which is why it shows up when suites run
 * together and never in isolation. [openAtemDialog] carried its own copy of this wait for exactly
 * that reason; it belongs here, where every caller gets it.
 *
 * The Go Live button is the signal because it is enabled on `canPlay` itself. Every fixture in this
 * file is a valid Lottie, so the wait always ends on the button rather than on the timeout.
 */
fun ComposeUiTest.selectPreset(name: String) {
    onAllNodesWithText(name)[0].performClick()
    waitForIdle()
    waitUntil("the chosen preset finished loading", WAIT_TIMEOUT_MS) {
        onAllNodes(hasContentDescription(LowerThirdLabel.GO_LIVE) and isEnabled())
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
            .isNotEmpty()
    }
}

/** The upload button's tooltip, which is also its content description. */
const val ATEM_UPLOAD_LABEL = "Send to ATEM"

/** The dialog's status line once the frames it will send have been rendered. */
const val ATEM_READY = "Ready to upload"

/**
 * Waits until the open dialog's status line reads [ATEM_READY].
 *
 * Opening the dialog — and switching its mode — starts a background render of the frames the upload
 * would send, and until that lands the status line is "Preparing frames" over a progress bar
 * instead. Nothing in the dialog waits for it, so which of the two a test sees is decided by how
 * fast the render finished, and the two states are different heights: the whole dialog below the
 * line shifts with them.
 *
 * The `waitForIdle` first is what makes the wait mean something. `atemPrepareProgress` starts at
 * `1f`, so the dialog's first composition claims "ready" before its `LaunchedEffect` has started the
 * render and reported back - checking the text without letting that effect run could pass on the
 * claim rather than on the render.
 */
fun ComposeUiTest.waitForAtemPrepared() {
    waitForIdle()
    waitUntil("the ATEM frame render finished", WAIT_TIMEOUT_MS) {
        onAllNodesWithText(ATEM_READY)
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
            .isNotEmpty()
    }
}

/**
 * Selects [presetName] — the upload button is disabled until one is chosen — then opens the ATEM
 * upload dialog.
 *
 * Both waits below replace a bare `waitForIdle()` that made this helper intermittently fail under
 * load (reliably when the ATEM suites ran together, never in isolation). Selecting a preset enables
 * the upload button asynchronously, and **a click on a disabled control is silently swallowed** — so
 * the click landed on nothing and the dialog never opened, which surfaced much later as "there are
 * no existing nodes for that selector" at whatever the test did next. Each wait ends on a positive
 * signal; the timeouts exist only to fail the test.
 */
fun ComposeUiTest.openAtemDialog(presetName: String = "Welcome") {
    selectPreset(presetName)
    waitUntil("the ATEM upload button is enabled", WAIT_TIMEOUT_MS) {
        onAllNodes(hasContentDescription(ATEM_UPLOAD_LABEL) and isEnabled())
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
            .isNotEmpty()
    }
    ltButton(ATEM_UPLOAD_LABEL).performClick()
    waitUntil("the dialog's upload-mode rows are composed", WAIT_TIMEOUT_MS) {
        onAllNodes(isSelectable()).fetchSemanticsNodes().size >= 2
    }
    waitForAtemPrepared()
}
