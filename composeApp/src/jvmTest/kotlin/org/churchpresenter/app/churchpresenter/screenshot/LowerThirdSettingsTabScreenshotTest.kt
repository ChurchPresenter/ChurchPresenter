@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import org.churchpresenter.app.churchpresenter.dialogs.tabs.awaitFolderScan

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.ProjectionSettings
import org.churchpresenter.app.churchpresenter.data.settings.StreamingSettings
import org.churchpresenter.app.churchpresenter.dialogs.tabs.LowerThirdSettingsTab
import org.churchpresenter.app.churchpresenter.ui.theme.ChurchPresenterTheme
import java.io.File
import kotlin.test.Test

/**
 * The Lower Third tab of the settings dialog, in both themes.
 *
 * Two panels: the presets found in the configured folder on the left, and on the right a live
 * preview of the selected one over the window insets the lower-third output is placed with. What
 * changes the tab is **what the folder holds and whether a preset is selected** — an unset folder,
 * a folder with no animation in it and a folder with several read the same to the code and draw
 * three different things — so those are the states below.
 *
 * The presets are fixtures written for this suite, not the ones that ship with the app: the preview
 * pane runs the animation on an endless loop, so anything with motion in it would be a different
 * image on every recording. Each fixture is a single solid color holding still for its whole
 * duration, which makes the pane's contents the same whatever frame the capture lands on — and
 * makes which preset is selected legible at a glance.
 */
class LowerThirdSettingsTabScreenshotTest {

    // ── What the folder holds ───────────────────────────────────────────────────────────────────

    /** No folder configured yet — the list says so, and there is nothing to preview. */
    @Test
    fun `no folder chosen`() = shoot("no_folder", settings = AppSettings())

    /** A folder with nothing in it that parses as an animation. */
    @Test
    fun `an empty folder`() = shoot("empty_folder", settings = withPresets(0))

    /** Presets listed, none selected: the preview pane asks for one. */
    @Test
    fun `presets listed`() = shoot("presets", settings = withPresets(PRESETS.size))

    // ── A preset selected ───────────────────────────────────────────────────────────────────────

    /** The selected row takes an accent bar and a fill, and its animation plays in the preview. */
    @Test
    fun `a preset selected`() = shoot("preset_selected", settings = withPresets(PRESETS.size)) { select(0) }

    /** A different one: the selection and the preview move together. */
    @Test
    fun `another preset selected`() = shoot("preset_selected_other", settings = withPresets(PRESETS.size)) { select(2) }

    // ── The output's placement ──────────────────────────────────────────────────────────────────

    /**
     * The window insets, off their defaults so each of the four is distinguishable.
     *
     * They are all 0 otherwise, which says nothing about which field is which — and the band drawn
     * across the screen mock is the projection tab's lower-third height, shown here at its default
     * third.
     */
    @Test
    fun `the window insets set`() = shoot(
        "window_insets",
        settings = withPresets(PRESETS.size,
            StreamingSettings(windowTop = 40, windowLeft = 120, windowRight = 60, windowBottom = 24)),
    )

    /**
     * A taller lower third.
     *
     * The band in the mock is `lowerThirdHeightPercent`, which lives on the Projection tab rather
     * than this one — so this image is here to show what that setting does to the placement mock,
     * and where the Top field ends up once the band it sits above grows.
     */
    @Test
    fun `a taller band`() = shoot(
        "band_60_percent",
        settings = withPresets(PRESETS.size).copy(projectionSettings = ProjectionSettings(lowerThirdHeightPercent = 60)),
    )

    // ── Driving ─────────────────────────────────────────────────────────────────────────────────

    /**
     * Selects the nth preset and lets the preview catch up.
     *
     * The pane debounces by 400ms before it reads the file, and then loops the animation forever —
     * which never leaves the clock idle, so the clock is driven by hand from here rather than by
     * `waitForIdle`, which would spin until the test timed out.
     */
    private fun ComposeUiTest.select(index: Int) {
        mainClock.autoAdvance = false
        onAllNodesWithText(PRESETS[index])[0].performClick()
        mainClock.advanceTimeBy(600)
        // Frame, settle, frame: the pane's animation is parsed off the test dispatcher, so it needs
        // both frames to draw and passes of real work between them to arrive at all. Driving the
        // clock by hand rather than letting it run keeps the frame the capture lands on the same
        // every time — the animation itself holds one color, so only *whether* it has loaded can
        // vary, and this is what settles that.
        repeat(FRAMES_TO_SETTLE) {
            mainClock.advanceTimeByFrame()
            waitForIdle()
        }
    }

    // ── Harness ─────────────────────────────────────────────────────────────────────────────────

    private fun shoot(
        name: String,
        settings: AppSettings,
        drive: ComposeUiTest.() -> Unit = {},
    ) = stackedThemes(SECTION, name) { mode, file ->
        runComposeUiTest {
            setContent {
                ChurchPresenterTheme(themeMode = mode) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Box(Modifier.fillMaxSize()) {
                            var current by remember { mutableStateOf(settings) }
                            LowerThirdSettingsTab(
                                settings = current,
                                onSettingsChange = { transform -> current = transform(current) },
                            )
                        }
                    }
                }
            }
            waitForIdle()
            awaitFolderScan()
            drive()
            captureTo(file)
        }
    }

    // ── Fixtures ────────────────────────────────────────────────────────────────────────────────

    /** Settings over a folder holding [count] presets, plus a file that is not one. */
    private fun withPresets(count: Int, streaming: StreamingSettings = StreamingSettings()): AppSettings {
        val dir = presetFolder(count)
        return AppSettings(streamingSettings = streaming.copy(lowerThirdFolder = dir.absolutePath))
    }

    /**
     * A fixed folder under a neutral root.
     *
     * Not a temp directory and not a repo-relative `build/` one: the tab lists what is in it by name
     * and the folder is read back on every recording, and a `build/` path resolves through the
     * developer's home directory.
     *
     * The stray `notes.txt` is there because the list is filtered twice — by extension, then by the
     * file actually parsing as an animation — and a folder of nothing but valid presets would not
     * show that the filter runs.
     */
    private fun presetFolder(count: Int): File {
        val dir = FIXTURES.absoluteFile
        dir.deleteRecursively()
        dir.mkdirs()
        File(dir, "notes.txt").writeText("not an animation")
        PRESETS.take(count).forEachIndexed { index, name -> File(dir, name).writeText(solid(COLOURS[index])) }
        return dir
    }

    /**
     * A one-layer Lottie holding a single color across the bottom third for its whole duration.
     *
     * Still on purpose: the preview loops whatever it is given for ever, so a fixture with motion in
     * it would land on a different frame each recording and rewrite these images for no change. The
     * band is placed where a real lower third sits so the pane reads as the thing it previews.
     */
    private fun solid(hex: String) = """
        {"v":"5.7.4","fr":30,"ip":0,"op":90,"w":1920,"h":1080,"nm":"Fixture","ddd":0,"assets":[],
         "layers":[{"ddd":0,"ind":1,"ty":1,"nm":"Band","sr":1,
          "ks":{"o":{"a":0,"k":100},"r":{"a":0,"k":0},"p":{"a":0,"k":[960,900,0]},
                "a":{"a":0,"k":[960,180,0]},"s":{"a":0,"k":[100,100,100]}},
          "ao":0,"sw":1920,"sh":360,"sc":"$hex","ip":0,"op":90,"st":0,"bm":0}]}
    """.trimIndent()

    private companion object {
        const val SECTION = "lowerThirdSettingsTab"

        val FIXTURES: File = File("/tmp")
            .takeIf { it.isDirectory }
            ?.let { File(it, "churchpresenter-screenshots/lowerthirds") }
            ?: File(System.getProperty("java.io.tmpdir"), "churchpresenter-screenshots/lowerthirds")

        /** Frames pumped after a selection, enough for the preview to have loaded and drawn. */
        const val FRAMES_TO_SETTLE = 20

        /** Listed in this order — the tab sorts by name. */
        val PRESETS = listOf("Announcement.json", "Speaker Name.json", "Welcome.json")
        val COLOURS = listOf("#2B6CB0", "#B0342B", "#2F855A")
    }
}
