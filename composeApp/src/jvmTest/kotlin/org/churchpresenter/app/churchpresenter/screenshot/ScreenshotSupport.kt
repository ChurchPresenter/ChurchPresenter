@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.takahirom.roborazzi.captureRoboImage
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
 * The cost is real and does not go away by being committed: Skia rasterises text per platform, so
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

/** [rootIndex] 1 shoots an open popup — a dropdown or menu is a compose root of its own. */
internal fun ComposeUiTest.captureTo(file: File, rootIndex: Int = 0) {
    onAllNodes(isRoot())[rootIndex].captureRoboImage(file.path)
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
 * A small component in both themes, stacked.
 *
 * [drive] runs before the shot — click to open a menu, type into a field. [rootIndex] 1 then shoots
 * the popup that opened.
 *
 * The content sits on a **[Surface]**, not on a `Box` with a surface-coloured background. The two
 * paint the same pixels but only the Surface publishes `LocalContentColor`, and a component that
 * leaves a colour unspecified — every `Labeled*` row's label, by design, so a call site can tint it —
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
    ImageIO.write(stacked, "png", out)
}

/**
 * The image cropped to its drawn content, with a small margin.
 *
 * A popup is its own compose root and that root is the whole window, so an open menu comes back as a
 * small menu on a screenful of empty background. The background colour is the commonest one around
 * the edge rather than a corner pixel — a component that reaches the top-left corner would otherwise
 * make its own fill the "background" and nothing would crop. An image with no other colour in it is
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
