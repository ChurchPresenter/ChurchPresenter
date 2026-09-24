@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.utils.PreviewOutput
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Which outputs a tab's preview can stand for, which one it picks, and the row that lets the
 * operator change it. The row draws nothing with fewer than two candidates, so most fixtures here
 * configure a second output.
 */
class PreviewOutputPickerTest {

    private val tab = "bible"

    /** `showBible` is derived from the assigned profile's `bibleMode`, one of two fixed profiles. */
    private fun screen(shows: Boolean = true, target: Int = 0, name: String = "") = ScreenAssignment(
        targetDisplay = target,
        activeProfileId = if (shows) "shows" else "off",
        screenName = name,
    )

    private fun settings(
        screens: List<ScreenAssignment> = listOf(screen()),
        browserSources: List<ScreenAssignment> = emptyList(),
        ndi: List<ScreenAssignment> = emptyList(),
        stored: Map<String, String> = emptyMap(),
    ) = AppSettings(
        projectionSettings = ProjectionSettings(
            outputProfiles = listOf(
                OutputProfile(id = "shows", bibleMode = Constants.SONG_LANG_BOTH),
                OutputProfile(id = "off", bibleMode = Constants.SONG_LANG_OFF),
            ),
            screenAssignments = screens,
            browserSourceOutputs = browserSources,
            ndiOutputs = ndi,
            previewOutputSelections = stored,
        ),
    )

    private fun <T> composed(block: @Composable () -> T): T {
        var result: T? = null
        runComposeUiTest {
            setContent { MaterialTheme { result = block() } }
            waitForIdle()
        }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    /**
     * `realWindowCount` defaults to 0 in production too, whenever the JVM is headless (a real build
     * never is, so this only ever bites tests) -- passed explicitly here for determinism, and bumped
     * to simulate a real display existing where a case needs the dev-fallback exemption turned off.
     */
    private fun outputs(
        s: AppSettings,
        mode: Presenting? = Presenting.BIBLE,
        realWindowCount: Int = 0,
    ): List<PreviewOutput> = composed { outputsShowing(s, mode, realWindowCount) }

    private fun picked(
        s: AppSettings,
        mode: Presenting? = Presenting.BIBLE,
        realWindowCount: Int = 0,
    ): PreviewOutput = composed { rememberPreviewOutput(s, tab, mode, realWindowCount) }

    private fun picker(
        initial: AppSettings,
        realWindowCount: Int = 0,
        block: ComposeUiTest.(get: () -> AppSettings) -> Unit,
    ) = runComposeUiTest {
        var current = initial
        setContent {
            MaterialTheme {
                var state by remember { mutableStateOf(current) }
                PreviewOutputPicker(
                    settings = state,
                    tabId = tab,
                    mode = Presenting.BIBLE,
                    onSettingsChange = { transform -> state = transform(state); current = state },
                    realWindowCount = realWindowCount,
                )
            }
        }
        block { current }
    }

    // ── Which outputs are listed ──────────────────────────────────────────────

    @Test
    fun `a screen showing the content is listed`() {
        val listed = outputs(settings())
        assertEquals(1, listed.size)
        assertEquals("screen:0", listed[0].key)
        assertEquals("Screen 1", listed[0].label)
        assertTrue(listed[0].showsMode)
    }

    @Test
    fun `a screen set to None is not listed`() {
        // realWindowCount = 1: a real display exists, so this is not a dev-fallback slot and "None"
        // means what it says. Left at the default 0, every slot up to devWindowCount is exempted --
        // see the doc comment on `outputsShowing`'s `realWindowCount` parameter.
        val listed = outputs(settings(listOf(screen(target = Constants.KEY_TARGET_NONE))), realWindowCount = 1)
        assertTrue(listed.isEmpty())
    }

    @Test
    fun `a screen set to None IS listed when it is a dev-fallback slot`() {
        val listed = outputs(settings(listOf(screen(target = Constants.KEY_TARGET_NONE))))
        assertEquals(listOf("screen:0"), listed.map { it.key })
    }

    @Test
    fun `a screen not routed this content is listed, but marked off`() {
        val listed = outputs(settings(listOf(screen(shows = false))))
        assertEquals(1, listed.size)
        assertEquals("screen:0", listed[0].key)
        assertFalse(listed[0].showsMode)
    }

    @Test
    fun `a screen's own name is used when it has one`() {
        assertEquals("Foyer", outputs(settings(listOf(screen(name = "Foyer"))))[0].label)
    }

    @Test
    fun `browser sources follow the screens`() {
        val listed = outputs(settings(browserSources = listOf(screen(target = -1))))
        assertEquals(listOf("screen:0", "browserSource:0"), listed.map { it.key })
        assertEquals("Browser Source 1", listed[1].label)
    }

    @Test
    fun `an enabled NDI output comes last`() {
        val listed = outputs(settings(ndi = listOf(screen(target = -1))))
        assertEquals(listOf("screen:0", "ndi:0"), listed.map { it.key })
        assertEquals("NDI Output 1", listed[1].label)
    }

    @Test
    fun `a disabled NDI output is not listed, because it sends nothing`() {
        val off = screen(target = -1).copy(ndiEnabled = false)
        assertEquals(listOf("screen:0"), outputs(settings(ndi = listOf(off))).map { it.key })
    }

    @Test
    fun `content NONE lists the output but marks it off, since nothing routes NONE`() {
        val listed = outputs(settings(), mode = Presenting.NONE)
        assertEquals(1, listed.size)
        assertFalse(listed[0].showsMode)
    }

    @Test
    fun `a null mode lists every live output and marks none of them off`() {
        val listed = outputs(settings(listOf(screen(shows = false))), mode = null)
        assertEquals(1, listed.size)
        assertTrue(listed[0].showsMode)
    }

    // ── Which one a tab previews ──────────────────────────────────────────────

    @Test
    fun `with nothing stored the first output is previewed`() {
        assertEquals("screen:0", picked(settings(browserSources = listOf(screen(target = -1)))).key)
    }

    @Test
    fun `a stored pick is honoured`() {
        val s = settings(
            browserSources = listOf(screen(target = -1)),
            stored = mapOf(tab to "browserSource:0"),
        )
        assertEquals("browserSource:0", picked(s).key)
    }

    @Test
    fun `a stored pick that matches nothing falls back to the first`() {
        val s = settings(stored = mapOf(tab to "ndi:7"))
        assertEquals("screen:0", picked(s).key, "an unplugged monitor must not blank the preview")
    }

    @Test
    fun `another tab's stored pick is not this tab's`() {
        val s = settings(
            browserSources = listOf(screen(target = -1)),
            stored = mapOf("songs" to "browserSource:0"),
        )
        assertEquals("screen:0", picked(s).key)
    }

    @Test
    fun `with no output at all a fallback shape stands in`() {
        val nothing = picked(settings(listOf(screen(target = Constants.KEY_TARGET_NONE))), realWindowCount = 1)
        assertEquals("", nothing.key)
        assertTrue(nothing.size.width > 0 && nothing.size.height > 0, "something must still be drawn")
    }

    @Test
    fun `when nothing shows the content, an off output is still picked over nothing`() {
        val picked = picked(settings(listOf(screen(shows = false))))
        assertEquals("screen:0", picked.key)
        assertFalse(picked.showsMode)
    }

    // ── The row itself ────────────────────────────────────────────────────────

    @Test
    fun `one output draws no picker, because there is no choice to make`() {
        picker(settings()) { _ ->
            onNodeWithText("PREVIEW OUTPUT", substring = true).assertDoesNotExist()
        }
    }

    @Test
    fun `a single output with content off still draws no picker, since there is still no choice`() {
        picker(settings(listOf(screen(shows = false)))) { _ ->
            onNodeWithText("Screen 1", substring = true).assertDoesNotExist()
        }
    }

    @Test
    fun `two outputs draw the picker, showing the one in force`() {
        picker(settings(browserSources = listOf(screen(target = -1)))) { _ ->
            onNodeWithText("Screen 1").assertExists()
        }
    }

    @Test
    fun `picking another output stores it against this tab`() {
        picker(settings(browserSources = listOf(screen(target = -1)))) { get ->
            onAllNodesWithText("Screen 1").onLast().performClick()
            waitForIdle()
            onAllNodesWithText("Browser Source 1").onLast().performClick()
            waitForIdle()

            assertEquals(
                mapOf(tab to "browserSource:0"),
                get().projectionSettings.previewOutputSelections,
            )
        }
    }

    @Test
    fun `picking leaves another tab's stored choice alone`() {
        val s = settings(
            browserSources = listOf(screen(target = -1)),
            stored = mapOf("songs" to "screen:0"),
        )
        picker(s) { get ->
            onAllNodesWithText("Screen 1").onLast().performClick()
            waitForIdle()
            onAllNodesWithText("Browser Source 1").onLast().performClick()
            waitForIdle()

            val stored = get().projectionSettings.previewOutputSelections
            assertEquals("screen:0", stored["songs"], "the other tab's pick must survive")
            assertEquals("browserSource:0", stored[tab])
        }
    }

    @Test
    fun `the picker writes nothing but the selection`() {
        val initial = settings(browserSources = listOf(screen(target = -1)))
        picker(initial) { get ->
            onAllNodesWithText("Screen 1").onLast().performClick()
            waitForIdle()
            onAllNodesWithText("Browser Source 1").onLast().performClick()
            waitForIdle()

            assertEquals(
                initial.projectionSettings.copy(
                    previewOutputSelections = mapOf(tab to "browserSource:0"),
                ),
                get().projectionSettings,
            )
        }
    }
}
