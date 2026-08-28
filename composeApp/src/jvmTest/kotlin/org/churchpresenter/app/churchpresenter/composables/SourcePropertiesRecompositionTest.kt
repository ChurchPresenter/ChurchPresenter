@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithText
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.core.models.scene.SourceTransform
import org.churchpresenter.app.churchpresenter.utils.TimerStateManager
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What survives a recomposition the operator did not ask for.
 *
 * The panel lives inside the canvas tab, so it is re-invoked whenever anything around it changes —
 * the dialog is resized, a setting elsewhere is edited, the tab re-lays out. None of that is a change
 * to the source being edited, and none of it may disturb what the panel is showing. That matters
 * most for the state the panel keeps *outside* the source: the local text a numeric field holds
 * before it is committed, and a countdown's running timer. Losing either mid-service is a real
 * failure — a duration being typed silently reverting, a timer resetting itself.
 *
 * [redrawablePanel] reproduces that by handing the panel a new `Modifier` on each redraw and leaving
 * the source untouched, which is precisely how its real caller behaves.
 */
class SourcePropertiesRecompositionTest {

    /** Timers are process-wide and their tickers are real coroutines — see [TimerStateManager.clear]. */
    @AfterTest
    fun stopTimers() = TimerStateManager.clear()

    // ── Every kind of source ──────────────────────────────────────────────────

    @Test
    fun `redrawing changes neither the source nor what any panel shows`() {
        // All eleven kinds, so no per-type block can be the one that rebuilds itself from scratch.
        Fixture.everyKind("redraw").forEach { source ->
            withOsName(OS_WITHOUT_ENUMERATOR) {
                redrawablePanel(source) { get, redraw ->
                    val before = renderedText()

                    redraw()

                    assertEquals(
                        source, get(),
                        "redrawing a ${source::class.simpleName} panel must not edit the source",
                    )
                    assertEquals(
                        before, renderedText(),
                        "nor change a single thing the ${source::class.simpleName} panel displays",
                    )
                }
            }
        }
    }

    @Test
    fun `redrawing repeatedly is still a no-op`() {
        redrawablePanel(Fixture.image()) { get, redraw ->
            val before = renderedText()
            repeat(3) { redraw() }

            assertEquals(Fixture.image(), get())
            assertEquals(before, renderedText())
        }
    }

    // ── State the panel keeps outside the source ──────────────────────────────

    @Test
    fun `a half-typed position survives a redraw uncommitted`() {
        // The float fields hold what is typed locally until Done or focus loss. A redraw in between
        // must not throw that away — nor commit it early.
        redrawablePanel(Fixture.text()) { get, redraw ->
            typeField(1, "0.42")

            redraw()

            assertFieldShows("0.42", "the X field, still holding what was typed")
            assertEquals(
                SourceTransform(), get().transform,
                "and still uncommitted — a redraw is not a Done",
            )
        }
    }

    @Test
    fun `a half-typed position can still be committed after a redraw`() {
        redrawablePanel(Fixture.text()) { get, redraw ->
            typeField(1, "0.42")
            redraw()

            textFields()[1].performImeAction()
            waitForIdle()

            assertEquals(0.42f, get().transform.x, "the value typed before the redraw is what commits")
        }
    }

    /**
     * That the redraw leaves the countdown *running* — deliberately not what it reads.
     *
     * This asserted `00:02:00` until CI disagreed with it four times: the Start button launches a
     * real one-second ticker on a background dispatcher, so an absolute read-out only holds while
     * under a second of wall clock passes between the click and the assertion. That is true on an
     * idle machine and false on a loaded runner, which is a test that reports how fast the box is.
     *
     * The remaining time surviving the redraw is not separately covered, and cannot be without a
     * clock the test controls: [TimerStateManager]'s interval is a private constant on an object,
     * and the alternative — a mutable interval the tests reach in and set — is the singleton seam
     * the repo bans. The gap is small, because every path that would reseed the time (`reset`,
     * `onDurationChanged`) also clears `isRunning`, so a countdown that came back reseeded would
     * fail the assertions below anyway.
     */
    @Test
    fun `a running countdown keeps running across a redraw`() {
        val id = "clk-redraw"
        val seedSeconds = 2 * 60
        redrawablePanel(Fixture.clock(id).copy(mode = "countdown", targetMinute = 2)) { _, redraw ->
            onNodeWithText("Start").performScrollTo().performClick()
            waitForIdle()
            onNodeWithText("Pause").assertExists()

            redraw()

            onNodeWithText("Pause").assertExists("the timer must still be running after a redraw")
            assertTrue(
                TimerStateManager.getState(id, seedSeconds).isRunning,
                "and the timer itself must still be running, not just the button that says so",
            )
        }
    }

    @Test
    fun `a ticked checkbox stays ticked across a redraw`() {
        redrawablePanel(Fixture.video()) { get, redraw ->
            toggleCheckbox(0)
            checkboxes()[0].assertIsOn()

            redraw()

            checkboxes()[0].assertIsOn()
            assertEquals(true, (get() as SceneSource.VideoSource).loop)
        }
    }

    @Test
    fun `a section unfolded by a checkbox stays unfolded across a redraw`() {
        redrawablePanel(Fixture.color()) { _, redraw ->
            toggleCheckbox(0)
            onNodeWithText("COLOR 2").assertExists()

            redraw()

            onNodeWithText("COLOR 2").assertExists("the gradient controls must not fold away again")
            checkboxes()[0].assertIsOn()
        }
    }

    // ── The panel's own parameters ────────────────────────────────────────────

    @Test
    fun `the panel renders the same whether or not it is given app settings`() {
        // Only the Bible panel reads them; for every other kind they must make no difference at all.
        listOf(
            Fixture.image(), Fixture.text(), Fixture.color(), Fixture.video(), Fixture.browser(),
            Fixture.shape(), Fixture.clock("clk-settings"), Fixture.qr(), Fixture.capture(),
        ).forEach { source ->
            var withoutSettings: Set<String> = emptySet()
            withOsName(OS_WITHOUT_ENUMERATOR) {
                sourcePanel(source) { _ -> withoutSettings = renderedText() }
                sourcePanel(source, appSettings = AppSettings()) { _ ->
                    assertEquals(
                        withoutSettings, renderedText(),
                        "app settings must not change a ${source::class.simpleName} panel",
                    )
                }
            }
        }
    }

    @Test
    fun `the caller's modifier is honoured`() {
        // redrawablePanel supplies a padding modifier of its own; the panel must lay out inside it
        // rather than ignoring it, which is what shifts everything down by the padding it was given.
        var withoutPadding = 0f
        sourcePanel(Fixture.image()) { _ ->
            withoutPadding = onNodeWithText(Label.PROPERTIES).fetchSemanticsNode().boundsInRoot.top
        }
        redrawablePanel(Fixture.image()) { _, redraw ->
            redraw() // the odd tick applies a 1dp top padding
            val padded = onNodeWithText(Label.PROPERTIES).fetchSemanticsNode().boundsInRoot.top
            assertEquals(
                true, padded > withoutPadding,
                "the panel must sit inside the modifier it was handed (was $padded, unpadded $withoutPadding)",
            )
        }
    }

    @Test
    fun `an unticked checkbox stays unticked across a redraw`() {
        redrawablePanel(Fixture.shape()) { _, redraw ->
            toggleCheckbox(0) // Show Stroke off
            checkboxes()[0].assertIsOff()

            redraw()

            checkboxes()[0].assertIsOff()
            assertEquals(0, countOf("Stroke Width"), "and its controls must still be folded away")
        }
    }
}
