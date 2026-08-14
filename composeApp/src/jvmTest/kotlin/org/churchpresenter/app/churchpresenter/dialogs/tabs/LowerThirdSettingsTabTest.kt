@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.StreamingSettings
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Drives the lower-third settings tab: the Lottie animation library on the left, the live preview on
 * the right, and the streaming-window margins beneath it.
 *
 * The library is read from a real folder, so each test that needs files works in its own temporary
 * one — see `LowerThirdSettingsTabTestSupport.kt`. The margins write into [StreamingSettings]; the
 * file list and selection live in the tab's own view model and are asserted through what reaches the
 * screen.
 */
class LowerThirdSettingsTabTest {

    private fun settingsWith(change: StreamingSettings.() -> StreamingSettings): AppSettings =
        AppSettings().let { it.copy(streamingSettings = it.streamingSettings.change()) }

    private fun ComposeUiTest.retype(showing: Int, to: Int) {
        onNode(hasSetTextAction() and hasText(showing.toString())).performTextReplacement(to.toString())
        waitForIdle()
    }

    // ── Structure ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the tab shows both panels`() = lowerThirdTab { _ ->
        onNodeWithText("Lottie Files").assertExists("the library panel must render")
        onNodeWithText("To trigger lower thirds via API visit the Server tab.")
            .assertExists("with the hint about the Server tab")
        onNodeWithText("Window Position").assertExists("and the preview panel's margins")
    }

    @Test
    fun `the tab offers its two library buttons`() = lowerThirdTab { _ ->
        onNodeWithText("Remove File").assertHasClickAction()
        onNodeWithText("Generate").assertHasClickAction()
    }

    @Test
    fun `the preview panel shows a placeholder until a preset is chosen`() = lowerThirdTab { _ ->
        // Once as the panel's caption, once inside the empty preview box.
        onAllNodesWithText("Select a preset to preview").assertCountEquals(2)
    }

    @Test
    fun `the window position diagram labels the lower third band`() = lowerThirdTab { _ ->
        onNodeWithText("Lower Third").assertExists("the band in the screen diagram must be labelled")
        for (edge in listOf("LEFT", "TOP", "RIGHT", "BOTTOM")) {
            onNodeWithText(edge).assertExists("the $edge margin field must be captioned")
        }
    }

    // ── The library, empty ──────────────────────────────────────────────────────────────────────

    @Test
    fun `with no folder configured the list says so`() = lowerThirdTab { _ ->
        onNodeWithText("No directory selected")
            .assertExists("an unconfigured library must say there is no directory, not that it is empty")
        onAllNodesWithText("No JSON files found").assertCountEquals(0)
    }

    @Test
    fun `an empty folder reads as having no files rather than no directory`() {
        withLottieFolder { folder ->
            lowerThirdTab(initial = settingsForFolder(folder)) { _ ->
                onNodeWithText("No JSON files found").assertExists()
                onAllNodesWithText("No directory selected").assertCountEquals(0)
            }
        }
    }

    @Test
    fun `a folder that no longer exists reads as having no files`() {
        val gone = File(System.getProperty("java.io.tmpdir"), "churchpresenter-no-such-lottie-folder")
        gone.deleteRecursively()
        lowerThirdTab(initial = settingsForFolder(gone)) { _ ->
            onNodeWithText("No JSON files found").assertExists("a missing folder must not crash the tab")
        }
    }

    // ── The library, populated ──────────────────────────────────────────────────────────────────

    @Test
    fun `the list shows the folder's Lottie animations in order`() {
        withLottieFolder(
            "zebra.json" to lottieJson("zebra"),
            "alpha.json" to lottieJson("alpha"),
            "middle.json" to lottieJson("middle"),
        ) { folder ->
            lowerThirdTab(initial = settingsForFolder(folder)) { _ ->
                for (name in listOf("alpha.json", "middle.json", "zebra.json")) {
                    onNodeWithText(name).assertExists("$name must be listed")
                }
                onAllNodesWithText("No JSON files found").assertCountEquals(0)
            }
        }
    }

    @Test
    fun `files that are not Lottie animations are left out`() {
        withLottieFolder(
            "real.json" to lottieJson("real"),
            "plain.json" to notLottieJson,
            "notes.txt" to lottieJson("wrong extension"),
        ) { folder ->
            lowerThirdTab(initial = settingsForFolder(folder)) { _ ->
                onNodeWithText("real.json").assertExists("a genuine animation is listed")
                onAllNodesWithText("plain.json").assertCountEquals(0)
                onAllNodesWithText("notes.txt").assertCountEquals(0)
            }
        }
    }

    @Test
    fun `every listed file can be selected`() {
        withLottieFolder("one.json" to lottieJson("one"), "two.json" to lottieJson("two")) { folder ->
            lowerThirdTab(initial = settingsForFolder(folder)) { _ ->
                onNodeWithText("one.json").assertHasClickAction()
                onNodeWithText("two.json").assertHasClickAction()
            }
        }
    }

    // ── Selection and preview ───────────────────────────────────────────────────────────────────

    @Test
    fun `choosing a file names it in the preview panel`() {
        withLottieFolder("chosen.json" to lottieJson("chosen")) { folder ->
            lowerThirdTab(initial = settingsForFolder(folder)) { _ ->
                onAllNodesWithText("chosen.json").assertCountEquals(1) // just the list row
                onAllNodesWithText("Select a preset to preview").assertCountEquals(2)

                onNodeWithText("chosen.json").performClick()
                waitForIdle()

                // Now the list row and the preview panel's caption both name it.
                onAllNodesWithText("chosen.json").assertCountEquals(2)
                onAllNodesWithText("Select a preset to preview").assertCountEquals(1)
            }
        }
    }

    @Test
    fun `choosing another file moves the preview to it`() {
        withLottieFolder("first.json" to lottieJson("first"), "second.json" to lottieJson("second")) { folder ->
            lowerThirdTab(initial = settingsForFolder(folder)) { _ ->
                onNodeWithText("first.json").performClick()
                waitForIdle()
                onAllNodesWithText("first.json").assertCountEquals(2)

                onNodeWithText("second.json").performClick()
                waitForIdle()

                onAllNodesWithText("second.json").assertCountEquals(2)
                onAllNodesWithText("first.json").assertCountEquals(1)
            }
        }
    }

    // ── Removing ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `Remove deletes the chosen animation from the folder and the list`() {
        withLottieFolder("doomed.json" to lottieJson("doomed"), "keeper.json" to lottieJson("keeper")) { folder ->
            lowerThirdTab(initial = settingsForFolder(folder)) { _ ->
                onNodeWithText("doomed.json").performClick()
                waitForIdle()

                onNodeWithText("Remove File").performScrollTo().performClick()
                waitForIdle()

                assertFalse(File(folder, "doomed.json").exists(), "the file must be gone from disk")
                assertTrue(File(folder, "keeper.json").exists(), "and the others left alone")
                onAllNodesWithText("doomed.json").assertCountEquals(0)
                onNodeWithText("keeper.json").assertExists()
            }
        }
    }

    @Test
    fun `Remove with nothing chosen deletes nothing`() {
        withLottieFolder("safe.json" to lottieJson("safe")) { folder ->
            lowerThirdTab(initial = settingsForFolder(folder)) { _ ->
                onNodeWithText("Remove File").performScrollTo().performClick()
                waitForIdle()

                assertTrue(File(folder, "safe.json").exists(), "nothing is selected, so nothing is removed")
                onNodeWithText("safe.json").assertExists()
            }
        }
    }

    @Test
    fun `removing the last animation returns the list to its empty state`() {
        withLottieFolder("only.json" to lottieJson("only")) { folder ->
            lowerThirdTab(initial = settingsForFolder(folder)) { _ ->
                onNodeWithText("only.json").performClick()
                waitForIdle()
                onNodeWithText("Remove File").performScrollTo().performClick()
                waitForIdle()

                onNodeWithText("No JSON files found").assertExists()
                // The preview is debounced by 400ms, so the box still holds the removed animation
                // until that elapses; the virtual clock is advanced rather than waited on.
                mainClock.advanceTimeBy(500)
                waitForIdle()
                onAllNodesWithText("Select a preset to preview").assertCountEquals(2)
            }
        }
    }

    // ── The generator ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `Generate opens the generator against the configured folder`() {
        withLottieFolder { folder ->
            var openedWith: String? = null
            lowerThirdTab(
                initial = settingsForFolder(folder),
                onOpenLottieGen = { outputDir, _ -> openedWith = outputDir },
            ) { _ ->
                onNodeWithText("Generate").performScrollTo().performClick()
                waitForIdle()
            }
            assertEquals(
                folder.absolutePath,
                openedWith,
                "the generator must be pointed at the library it will save into",
            )
        }
    }

    /**
     * The generator writes its file straight to disk and then calls back; the tab has to re-read the
     * folder, or a freshly generated animation would not appear until the tab was reopened.
     */
    @Test
    fun `a file saved by the generator appears once it reports back`() {
        withLottieFolder("existing.json" to lottieJson("existing")) { folder ->
            var reportSaved: (() -> Unit)? = null
            lowerThirdTab(
                initial = settingsForFolder(folder),
                onOpenLottieGen = { _, onFileSaved -> reportSaved = onFileSaved },
            ) { _ ->
                onNodeWithText("Generate").performScrollTo().performClick()
                waitForIdle()
                onAllNodesWithText("generated.json").assertCountEquals(0)

                File(folder, "generated.json").writeText(lottieJson("generated"))
                runOnIdle { reportSaved?.invoke() }
                waitForIdle()

                onNodeWithText("generated.json").assertExists("the new animation must appear in the list")
                onNodeWithText("existing.json").assertExists("alongside what was already there")
            }
        }
    }

    // ── Streaming window margins ────────────────────────────────────────────────────────────────

    /**
     * `NumberSettingsTextField` copies what is typed into its own state and only withholds the
     * callback when the value is out of range. So the field reading back the new number proves
     * nothing on its own — every margin test here closes the loop by re-rendering a fresh tab from
     * the settings that came out, where the field can only be showing what was stored.
     */
    @Test
    fun `each streaming-window margin field writes its own value back`() {
        // The four share a default, so give each one a value only it holds.
        val distinct = settingsWith { copy(windowLeft = 41, windowTop = 42, windowRight = 43, windowBottom = 44) }
        var saved = AppSettings()
        lowerThirdTab(initial = distinct) { get ->
            retype(showing = 41, to = 11)
            assertEquals(11, get().streamingSettings.windowLeft, "the left margin must be stored")

            retype(showing = 42, to = 22)
            assertEquals(22, get().streamingSettings.windowTop, "the top margin must be stored")

            retype(showing = 43, to = 33)
            assertEquals(33, get().streamingSettings.windowRight, "the right margin must be stored")

            retype(showing = 44, to = 55)
            assertEquals(55, get().streamingSettings.windowBottom, "the bottom margin must be stored")

            assertEquals(11, get().streamingSettings.windowLeft, "and none of them disturbed a neighbour")
            assertEquals(33, get().streamingSettings.windowRight)
            saved = get()
        }
        // Re-rendered from the saved settings alone: each field can only show what was stored.
        lowerThirdTab(initial = saved) { _ ->
            for (value in listOf(11, 22, 33, 55)) {
                onNode(hasSetTextAction() and hasText(value.toString()))
                    .assertExists("a fresh render must show the stored margin $value")
            }
        }
    }

    @Test
    fun `a margin outside the allowed range is not stored`() {
        var saved = AppSettings()
        lowerThirdTab(initial = settingsWith { copy(windowLeft = 41) }) { get ->
            retype(showing = 41, to = 99999)
            assertEquals(41, get().streamingSettings.windowLeft, "99999 is outside 0..10000")
            // The field itself echoes the rejected entry — that is the widget's own state, not
            // anything that was stored.
            onNode(hasSetTextAction() and hasText("99999")).assertExists()
            saved = get()
        }
        lowerThirdTab(initial = saved) { _ ->
            onAllNodes(hasSetTextAction() and hasText("99999"))
                .assertCountEquals(0)
            onNode(hasSetTextAction() and hasText("41"))
                .assertExists("a fresh render shows the value that survived, not the rejected one")
        }
    }

    // ── The lower-third band diagram ────────────────────────────────────────────────────────────

    @Test
    fun `the band diagram renders whatever height is configured`() {
        for (percent in listOf(10, 33, 60)) {
            val settings = AppSettings().let {
                it.copy(projectionSettings = it.projectionSettings.copy(lowerThirdHeightPercent = percent))
            }
            lowerThirdTab(initial = settings) { _ ->
                onNodeWithText("Lower Third")
                    .assertExists("the band must be drawn and labelled at $percent%")
            }
        }
    }
}
