@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.performMouseInput
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.sharedui.models.Tabs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The controls an operator runs a service with can be reached from the keyboard: pressing Tab
 * around the main window lands on Go Live, Add to Schedule and Clear Display, and on preview mode's
 * Take. A control Tab skips is one a keyboard-only operator cannot press at all.
 */
class KeyboardReachTest : MainDesktopComposeHarness() {

    private companion object {
        /** Only ever the bound on a wait for state; never the thing that passes a test. */
        const val WAIT_MS = 5_000L
    }

    /** The names of everything Tab lands on in [presses] presses -- more than one full cycle. */
    private fun ComposeUiTest.tabStops(presses: Int = 80): Set<String> = buildSet {
        repeat(presses) {
            press(Key.Tab)
            onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Focused, true)).fetchSemanticsNodes()
                .forEach { node ->
                    node.config.getOrNull(SemanticsProperties.ContentDescription)?.let(::addAll)
                    node.config.getOrNull(SemanticsProperties.Text)?.forEach { add(it.text) }
                }
        }
    }

    private fun songsOnly(settings: AppSettings) =
        settings.copy(hiddenTabs = Tabs.entries.filter { it != Tabs.SONGS }.map { it.name }.toSet())

    /** Waits, bounded, until a control named [name] can be pressed -- only an enabled one takes focus. */
    private fun ComposeUiTest.awaitEnabled(name: String) =
        waitUntil("$name to be enabled", timeoutMillis = WAIT_MS) {
            onAllNodes(hasContentDescription(name) and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }

    /** Selects the library's one song once it has loaded, so Go Live can be pressed. */
    private fun ComposeUiTest.selectTheSong() {
        val song = hasText("A Test Song", substring = true)
        waitUntil("the song to load", timeoutMillis = WAIT_MS) { onAllNodes(song).fetchSemanticsNodes().isNotEmpty() }
        onAllNodes(song)[0].performMouseInput { click() }
        waitForIdle()
        awaitEnabled("Go Live")
    }

    @Test
    fun `go live, add to schedule and clear display are reachable with tab`() {
        root(songsOnly(withOneSong()), devMode = false) {
            selectTheSong()

            val stops = tabStops()

            listOf("Go Live", "Add to Schedule", "Clear Display").forEach { name ->
                assertTrue(name in stops, "Tab never reaches $name; it reaches: $stops")
            }
        }
    }

    @Test
    fun `preview mode's take is reachable with tab`() {
        root(songsOnly(withPreviewMode(on = true)), presenterManager = cuedManager(), devMode = true) {
            waitUntil("Take to be enabled", timeoutMillis = WAIT_MS) {
                onAllNodes(hasText("Take") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
            }
            val stops = tabStops()
            assertTrue("Take" in stops, "Tab never reaches Take; it reaches: $stops")
        }
    }
}
