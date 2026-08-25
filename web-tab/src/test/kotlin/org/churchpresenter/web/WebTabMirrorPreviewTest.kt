@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.web

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the operator sees in the Web tab between going live and the first frame arriving.
 *
 * Mirror mode is the default once a page is live: the tab stops rendering its own browser and shows
 * the presenter's screenshot instead, so that what is checked here is the picture the operator uses
 * to see what the congregation is looking at. Until the first snapshot lands there is a spinner, and
 * after a long enough wait a hint saying why one might never arrive.
 *
 * [WebTabLiveTest] drives live mode, the mirror/interactive toggle and the type-to-page field, but
 * never sets a snapshot — so the whole `webSnapshot != null` branch, and the placeholder that stands
 * in for it, went unexercised. Nothing proved the mirrored image ever appears at all.
 *
 * **What this cannot reach, and why.** The mirrored image forwards mouse, scroll and keyboard input
 * to the presenter's live `CefBrowser` by reflection. Those bodies need a real browser — a JCEF
 * render surface, which is not available headless — and every one of them begins with a
 * `liveBrowser == null` early return. So the tests below drive the input paths with no browser
 * attached, which covers the guard and pins that the mirror is inert rather than crashing; the
 * forwarding itself stays uncovered and is listed in the coverage plan under JCEF.
 *
 * The hint text is chosen from `os.name` — macOS gets a Screen Recording permission hint, everything
 * else a plain wait message. Only the host's own branch is asserted here. Faking `os.name` to reach
 * the other one would resolve skiko's host OS against the fake and break every later Compose test in
 * the JVM, which is the trap `TestSingletons.latchSkikoHostOs` exists for.
 */
class WebTabMirrorPreviewTest {

    private val spinner = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)

    /** The wait hint the running platform shows — the other platform's is unreachable from here. */
    private val expectedHint: String =
        if (System.getProperty("os.name", "").lowercase().contains("mac")) {
            WebLabel.SNAPSHOT_SCREEN_RECORDING_HINT
        } else {
            WebLabel.SNAPSHOT_WAITING
        }

    private fun ComposeUiTest.goLive(presenterMode: () -> Unit) {
        presenterMode()
        waitForIdle()
    }

    @Test
    fun `going live with no snapshot yet shows the waiting spinner`() = webTab { output, _ ->
        goLive { output.live = true }

        onNode(spinner).assertExists()
        // The hint is deliberately not up yet — it only earns the operator's attention once the wait
        // has become abnormal, and showing it immediately would read as an error on every go-live.
        onNodeWithText(expectedHint).assertDoesNotExist()
    }

    @Test
    fun `the hint appears only once the wait has gone on long enough`() = webTab { output, _ ->
        goLive { output.live = true }

        // Virtual time: the production delay is 7s, which the test clock advances instantly. Nothing
        // here waits on a real clock.
        mainClock.advanceTimeBy(7_001)
        waitForIdle()

        onNodeWithText(expectedHint).assertExists()
        onNode(spinner).assertExists()
    }

    @Test
    fun `a snapshot replaces the placeholder with the mirrored image`() = webTab { output, _ ->
        goLive { output.live = true }
        onNode(spinner).assertExists()

        output.setSnapshot(ImageBitmap(8, 8))
        waitForIdle()

        // The image itself carries no contentDescription, so it contributes no semantics node to
        // assert on. The placeholder disappearing is the positive signal that the snapshot branch —
        // and only it — is what composed.
        onNode(spinner).assertDoesNotExist()
        onNodeWithText(expectedHint).assertDoesNotExist()
    }

    @Test
    fun `clearing the snapshot puts the operator back on the placeholder`() = webTab { output, _ ->
        goLive { output.live = true }
        output.setSnapshot(ImageBitmap(8, 8))
        waitForIdle()
        onNode(spinner).assertDoesNotExist()

        output.setSnapshot(null)
        waitForIdle()

        // A snapshot stream that dies mid-service has to show the wait state again rather than leave
        // a frozen frame the operator would read as still live.
        onNode(spinner).assertExists()
    }

    @Test
    fun `input over the mirrored image is inert with no browser attached`() = webTab { output, _ ->
        goLive { output.live = true }
        output.setSnapshot(ImageBitmap(8, 8))
        waitForIdle()

        // Each forwarding path guards on liveBrowser being null before it reflects anything. Driving
        // all three proves the guards hold rather than that the events are unreachable.
        onRoot().performMouseInput {
            moveTo(center)
            press()
            release()
            scroll(1f)
        }
        onRoot().performKeyInput { pressKey(Key.A) }
        waitForIdle()

        assertEquals(null, output.liveBrowser, "no browser was ever attached")
        assertTrue(output.isLive, "and nothing was torn down")
        onNode(spinner).assertDoesNotExist()
    }

    @Test
    fun `switching to interactive mode drops the mirror entirely`() = webTab { output, _ ->
        goLive { output.live = true }
        onNode(spinner).assertExists()

        onNodeWithText(WebLabel.MIRROR).performClick()
        waitForIdle()

        // Interactive mode renders the tab's own browser, so neither the snapshot nor its placeholder
        // belongs on screen — a leftover spinner there would sit on top of a live page.
        onNode(spinner).assertDoesNotExist()
        assertTrue(hasWebButton(WebLabel.FOCUS_FIRST_INPUT).not(), "mirror-only controls go with it")
    }

    @Test
    fun `a tall narrow panel fits the preview to its height instead of its width`() {
        // The preview keeps the output's aspect ratio and picks the larger of the two fits. Every
        // other test here gets the whole test window, which is wide, so only the fit-by-width arm
        // ever ran; a tall narrow panel is what an operator with the sidebar open actually has.
        webTab(width = 300.dp) { output, _ ->
            output.live = true
            output.setSnapshot(ImageBitmap(64, 64))
            waitForIdle()

            onNode(spinner).assertDoesNotExist()
        }
    }
}
