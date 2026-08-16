@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.roborazziEnabled
import io.github.takahirom.roborazzi.captureRoboImage
import org.churchpresenter.app.churchpresenter.composables.RecentColors
import org.churchpresenter.app.churchpresenter.ui.theme.ChurchPresenterTheme
import org.churchpresenter.app.churchpresenter.ui.theme.ThemeMode
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

internal val THEMES = listOf("light" to ThemeMode.LIGHT, "dark" to ThemeMode.DARK)

/**
 * Where every screenshot is written, relative to the module directory a Gradle test task runs in.
 *
 * **The images here are committed.** They are the artifact a human opens and approves before a UI
 * change is merged — a reviewer looks at them and asks for changes when a state is wrong, missing,
 * or badly framed. `.github/workflows/screenshots.yml` additionally renders *both* sides on one
 * runner and posts the visual difference as a PR comment; that comment is a convenience, not the
 * approval. **Re-record and include the images whenever a state you touched changed.**
 *
 * The cost is real and does not go away by being committed: Skia rasterizes text per platform, so
 * re-recording the same *unchanged* states on a different OS rewrites nearly every file — 15 of 16,
 * measured — and git keeps every version of a binary for ever. So **record on ONE platform per
 * branch**, and re-record only what actually changed; never re-record the whole suite out of habit.
 *
 * Two invariants govern the CI comparison, and both have already been broken once:
 *
 * - **A capture must be written under this root.** Images are matched between the two sides **by
 *   their path relative to it**, so one written elsewhere is not reported as *differing* — it has no
 *   counterpart and silently stops being compared. Go through [stackedThemes]/[captureComponent] and
 *   this is handled.
 * - **A class must be named `…ScreenshotTest`.** The workflow records with `--tests
 *   '*ScreenshotTest*'`; a class outside that pattern is never rendered in CI and its images are
 *   never compared.
 */
internal const val SCREENSHOT_ROOT = "screenshots"

/**
 * How long to let real work — decoding thumbnails, rasterising a deck — finish before failing.
 *
 * This is the *failure* path, not the success path: every one of these waits ends the moment its
 * condition holds, so a generous value costs a fast machine nothing. It exists only so a hang fails
 * the test instead of hanging the job.
 *
 * It is deliberately far larger than the work takes on a developer's machine. The record job runs
 * every `*ScreenshotTest*` class in one go on a two-core runner, so the same decode that finishes in
 * milliseconds locally competes with everything else for CPU. `PicturesTabScreenshotTest` had 5s and
 * failed on CI with *"Condition (no thumbnail still loading) still not satisfied after 5000 ms"* the
 * first time the job got big enough — while the identical wait elsewhere had been given 10s, which
 * is exactly the drift a shared constant prevents.
 *
 * **This is not the forbidden "widen the timeout to fix a flake".** These waits terminate on a
 * positive signal and the timeout never ends them on the happy path. The wait is also load-bearing
 * for correctness rather than just for passing: a thumbnail still showing its placeholder is a
 * *different image*, so cutting the wait short would produce a screenshot that differs from one run
 * to the next.
 *
 * **It must stay comfortably under a minute.** `runTest` — which `runComposeUiTest` builds on —
 * gives the whole test body 60s, and that is the outer bound no wait in here can exceed. Set to
 * 60s this constant could never spend its budget: a slow render hit the harness timeout first and
 * failed with `UncompletedCoroutinesError: After waiting for 1m, the test body did not run to
 * completion`, which names neither the wait nor the condition. 30s leaves room for the rest of the
 * test and still fails with the message that says what was being waited for.
 */
internal const val RENDER_TIMEOUT_MS = 30_000L
private val PARTS = File("$SCREENSHOT_ROOT/.parts")

/**
 * How much of an image may differ before `verifyRoborazziJvm` fails: **0.1%** of its pixels.
 *
 * Roborazzi's own default is **0** — one differing pixel fails. That is the right target and not yet
 * reachable here (see below), so this is deliberately a little above it rather than the order of
 * magnitude looser that a screenshot gate is usually given.
 *
 * **Why not 2.5%, measured on this set on 2026-08-08.** The smallest real changes cost far less than
 * a percent of the picture, so a threshold in the percents hides exactly what a reviewer wants
 * caught:
 *
 * | change | pixels differing |
 * |---|---|
 * | one switch flipped (`stageMonitorSettingsTab` chords on/off) | **0.19%** |
 * | a button and its status word (`obsSettingsTab` connected/error) | 0.66% |
 * | four switches (`dictionarySettingsTab` show/hide) | 0.76% |
 * | one sentence rewritten (`atemSettingsTab` capacity) | 6.2% |
 * | a row appearing and reflowing what is under it (`serverSettingsTab` API key) | 32.6% |
 *
 * At 2.5% the first three of those merge cleanly — a switch could be inverted, a status could go the
 * wrong color, and the gate would stay green.
 *
 * **What sets the floor is churn, not the threshold**, so the churn was fixed rather than the number
 * widened. Nine images used to move on every re-record; each had a cause, and none of them was Skia:
 *
 * - `colour_picker` (0.87%) — the picker's "Recent" row, JVM-wide state; see [PinnedRecentColors].
 * - `previewApp/about_*` (0.55%) — `BuildConfig.VERSION_DISPLAY` carries the git hash, so it changed
 *   on every commit. The dialog now takes the version as a parameter and the test pins it.
 * - `previewApp/settings_companion_satellite_*` (0.16%) — `CompanionSatelliteSettings` generates its
 *   device id with `UUID.randomUUID()` and the tab draws it. Pinned in the preview fixture.
 * - `previewApp/dictionary_light` (4.2%) — the interlinear index loads in the background and is
 *   queried once, when an entry is selected, so a selection made mid-load kept a partial count. The
 *   test now re-selects until the count is complete.
 * - `previewApp/canvas_*` (0.07%) — not churn at all: a stale image, never re-recorded after the
 *   canvas source list moved from emoji to real icons.
 *
 * With those gone the set is byte-stable across consecutive recordings, so **0.1% is slack for
 * anti-aliasing and nothing else — it sits under every real change measured above.** 0 is the
 * target; keep this only as long as something still jitters.
 *
 * The threshold is a *fraction of the whole image*, so it scales with the picture: on a stacked
 * 1024×1538 tab shot it is ~7,900 pixels, on a trimmed component shot a few hundred.
 *
 * Note the comparator underneath already ignores imperceptible color drift — Roborazzi's default
 * `SimpleImageComparator` counts a pixel as differing only past a color distance of 0.007, which is
 * about four levels of one 8-bit channel.
 */
internal const val CHANGE_THRESHOLD = 0.001f

/**
 * What the record/compare/verify tasks apply to a **golden** — the stacked image that is committed.
 */
private val GOLDEN_OPTIONS = RoborazziOptions(
    compareOptions = RoborazziOptions.CompareOptions(changeThreshold = CHANGE_THRESHOLD),
)

/**
 * What a per-theme part is captured with: always *recorded*, never verified.
 *
 * The parts under `.parts/` are scratch — they exist for the few milliseconds between rendering a
 * theme and stacking the pair, and are deleted straight after. Left on the ambient task type they
 * would be treated as goldens of their own, and `verifyRoborazziJvm` would fail every state with
 * "the original file was not found" while never looking at the images that are actually committed.
 *
 * Falls back to the ambient type when Roborazzi is switched off entirely, which is what keeps an
 * ordinary `jvmTest` run from writing images at all.
 *
 * **Only [stackedThemes] may use this.** A capture written straight to a committed path — which is
 * how every `AppPreview*` suite works — must stay on the ambient task type, or a `verify` run would
 * quietly *re-record* it instead of checking it. That is exactly what happened the first time this
 * was wired up: verify came back green while overwriting eight `previewApp` images.
 */
private val PART_OPTIONS =
    if (roborazziEnabled()) RoborazziOptions(taskType = RoborazziTaskType.Record) else RoborazziOptions()

/**
 * Captures a compose root to [file].
 *
 * A file under `.parts/` is one half of a [stackedThemes] pair and is only ever *recorded*; anything
 * else is a golden and takes whichever task is running — so an `AppPreview*` suite that writes
 * straight to `screenshots/previewApp/…` is verified, while the throwaway halves are not.
 *
 * [rootIndex] 1 shoots an open popup — a dropdown or menu is a compose root of its own.
 */
internal fun ComposeUiTest.captureTo(file: File, rootIndex: Int = 0) {
    dismissHover()
    val options = if (file.absoluteFile.startsWith(PARTS.absoluteFile)) PART_OPTIONS else GOLDEN_OPTIONS
    onAllNodes(isRoot())[rootIndex].captureRoboImage(file.path, options)
}

/**
 * Takes the pointer off the UI, so no control is captured wearing a hover highlight.
 *
 * `performClick` injects a real press and leaves the cursor sitting where it landed, which puts a
 * Material state layer over that control — an 8% wash of the content colour over its container — for
 * as long as the pointer stays inside. Whether the hover has been delivered and animated in by the
 * time the frame is grabbed is not something any of the waits here settle, so the same state came
 * out of CI twice with the Go Live button in two different shades and `lower_third_light` was
 * reported as changed on a run that changed nothing.
 *
 * Dismissing it here rather than at each call site is what makes it hold: every capture goes through
 * this function, and a new suite gets the behaviour without knowing to ask for it.
 */
private fun ComposeUiTest.dismissHover() {
    onAllNodes(isRoot()).fetchSemanticsNodes(atLeastOneRootRequired = false).forEachIndexed { index, _ ->
        onAllNodes(isRoot())[index].performMouseInput { exit(Offset(-1f, -1f)) }
    }
    waitForIdle()
}

/**
 * Runs [shoot] once per theme and writes both renders into one `screenshots/<section>/<name>.png`,
 * light above dark — so a state is written once and reviewed as a single image.
 *
 * Stacked afterwards rather than composed together because each render fills the test window: two
 * in one composition would get half the height each instead of two full-size views.
 */
internal fun stackedThemes(
    section: String,
    name: String,
    trim: Boolean = false,
    shoot: (ThemeMode, File) -> Unit,
) {
    PARTS.mkdirs()
    val parts = THEMES.map { (suffix, mode) ->
        File(PARTS, "${section}_${name}_$suffix.png").also { shoot(mode, it) }
    }
    // Nothing was written: capture is inert outside the Roborazzi tasks, so an ordinary test run
    // still composes every state (a throw there fails the test) without touching the images.
    if (parts.all { it.exists() }) stackVertically(parts, File("$SCREENSHOT_ROOT/$section/$name.png"), trim)
    parts.forEach { it.delete() }
    PARTS.delete()
}

/**
 * Runs [shoot] once per theme and writes each render as its own golden —
 * `screenshots/<section>/<name>_light.png` and `<name>_dark.png`.
 *
 * [stackedThemes] is the default and reviews a state as a single image; this exists for a suite
 * whose states are tall enough that stacking makes a strip too long to read at a glance. Both write
 * under [SCREENSHOT_ROOT], so either way the CI comparison matches the files by path.
 */
internal fun separateThemes(section: String, name: String, shoot: (ThemeMode, File) -> Unit) {
    THEMES.forEach { (suffix, mode) ->
        shoot(mode, File("$SCREENSHOT_ROOT/$section/${name}_$suffix.png"))
    }
}

/**
 * A small component in both themes, stacked.
 *
 * [drive] runs before the shot — click to open a menu, type into a field. [rootIndex] 1 then shoots
 * the popup that opened.
 *
 * The content sits on a **[Surface]**, not on a `Box` with a surface-coloured background. The two
 * paint the same pixels but only the Surface publishes `LocalContentColor`, and a component that
 * leaves a color unspecified — every `Labeled*` row's label, by design, so a call site can tint it —
 * falls back to that. Without one the fallback is Material's default of plain black, which is
 * invisible against the dark theme: `labeledControls` was recorded that way and its labels came out
 * black-on-black. Every tab in the app draws inside a Surface, so this is also what the component
 * really looks like there.
 */
internal fun captureComponent(
    section: String,
    name: String,
    rootIndex: Int = 0,
    drive: ComposeUiTest.() -> Unit = {},
    content: @Composable () -> Unit,
) = stackedThemes(section, name, trim = true) { mode, file ->
    runComposeUiTest {
        setContent {
            ChurchPresenterTheme(themeMode = mode) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Box(Modifier.padding(12.dp)) { content() }
                }
            }
        }
        drive()
        captureTo(file, rootIndex)
    }
}

private fun stackVertically(parts: List<File>, out: File, trim: Boolean) {
    val images = parts
        .map { ImageIO.read(it) ?: error("unreadable capture: ${it.path}") }
        .map { if (trim) it.trimmed() else it }
    val divider = 2
    val width = images.maxOf { it.width }
    val height = images.sumOf { it.height } + divider * (images.size - 1)

    val stacked = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val canvas = stacked.createGraphics()
    var y = 0
    images.forEachIndexed { index, image ->
        canvas.drawImage(image, 0, y, null)
        y += image.height
        if (index < images.lastIndex) {
            canvas.color = Color.GRAY
            canvas.fillRect(0, y, width, divider)
            y += divider
        }
    }
    canvas.dispose()
    out.parentFile?.mkdirs()

    // Handed to Roborazzi rather than written straight to disk, so the file a reviewer opens is the
    // one the record/compare/verify tasks own: `recordRoborazziJvm` writes it, and
    // `verifyRoborazziJvm` compares this exact image against what is committed and fails past
    // [CHANGE_THRESHOLD], leaving a side-by-side `<name>_compare.png` under
    // `composeApp/build/outputs/roborazzi/`. Composing the pair ourselves and then writing it with
    // ImageIO — which is what this did first — left the committed images outside Roborazzi's world
    // entirely: only the throwaway per-theme parts were ever verified.
    stacked.toComposeImageBitmap().captureRoboImage(out, GOLDEN_OPTIONS)
}

/**
 * The image cropped to its drawn content, with a small margin.
 *
 * A popup is its own compose root and that root is the whole window, so an open menu comes back as a
 * small menu on a screenful of empty background. The background color is the commonest one around
 * the edge rather than a corner pixel — a component that reaches the top-left corner would otherwise
 * make its own fill the "background" and nothing would crop. An image with no other color in it is
 * returned untouched.
 */
private fun BufferedImage.trimmed(margin: Int = 8): BufferedImage {
    val border = buildList {
        for (x in 0 until width) { add(getRGB(x, 0)); add(getRGB(x, height - 1)) }
        for (y in 0 until height) { add(getRGB(0, y)); add(getRGB(width - 1, y)) }
    }
    val background = border.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: return this
    var left = width
    var top = height
    var right = -1
    var bottom = -1
    for (y in 0 until height) {
        for (x in 0 until width) {
            if (getRGB(x, y) == background) continue
            if (x < left) left = x
            if (x > right) right = x
            if (y < top) top = y
            if (y > bottom) bottom = y
        }
    }
    if (right < left || bottom < top) return this

    val x = (left - margin).coerceAtLeast(0)
    val y = (top - margin).coerceAtLeast(0)
    return getSubimage(x, y, (right + margin - x).coerceAtMost(width - x), (bottom + margin - y).coerceAtMost(height - y))
}

/**
 * Empties the color picker's "Recent" row for one test class, and puts it back.
 *
 * [RecentColors] is a JVM-wide singleton loaded from `~/.churchpresenter/recent_colors.json` at class
 * init and appended to by every picker anyone confirms — so the row a picker draws is whatever the
 * machine and the tests that ran before happened to leave there. That is not a hypothetical: it is
 * why `bibleSettingsTab/colour_picker.png` and `songSettingsTab/colour_picker.png` were two of the
 * nine images that changed on **every** full re-record, at 0.87% of the picture each, which is above
 * [CHANGE_THRESHOLD] and so was a standing `verifyRoborazziJvm` failure that meant nothing.
 *
 * Emptied, the row is not drawn at all and the image is the picker itself. Restored afterwards
 * because a class that left the singleton empty would change what every later class in the JVM sees
 * — the same reason `RecentFilesSwap` exists.
 *
 * ```
 * private val recents = PinnedRecentColors()
 * @BeforeTest fun pinRecents() = recents.clear()
 * @AfterTest fun unpinRecents() = recents.restore()
 * ```
 */
internal class PinnedRecentColors {
    private val stashed = mutableListOf<String>()

    fun clear() {
        stashed += RecentColors.colors
        RecentColors.colors.clear()
    }

    fun restore() {
        RecentColors.colors.clear()
        RecentColors.colors.addAll(stashed)
        stashed.clear()
    }
}
