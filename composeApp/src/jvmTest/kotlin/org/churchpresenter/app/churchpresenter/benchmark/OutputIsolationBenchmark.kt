package org.churchpresenter.app.churchpresenter.benchmark

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.churchpresenter.app.churchpresenter.preferredRenderApi
import org.churchpresenter.diagnostics.UiStallInjector
import org.churchpresenter.diagnostics.UiStallSpec
import org.churchpresenter.sharedui.utils.DevFlags
import java.awt.GraphicsEnvironment
import java.io.File

private val STALLS_MS = listOf(0L, 100L, 500L, 2_000L)

/** The quiet time between stalls; a stall posted more often than it lasts would pile up behind itself. */
private const val STALL_GAP_MS = 1_000L
private const val RUN_NANOS = 8_000_000_000L
private const val WARMUP_FRAMES = 60
private const val FALLBACK_REFRESH_HZ = 60

/**
 * What a stalled operator UI costs an on-screen output (`docs/SHOW_CONTROL.md`, Output isolation).
 *
 * One output window draws a song verse that changes every frame; for each level in [STALLS_MS] the
 * event thread is blocked, a second after each stall ends, by [UiStallInjector] -- the same stall a
 * dev build takes from `-Dchurchpresenter.injectUiStall` -- and the gaps between the output's frames
 * are recorded. Not a test, because tests run headless: `./gradlew :composeApp:isolationBenchmark`
 * runs this `main` on a machine with a display, and writes the table to
 * `isolationBenchmark.reportDir`.
 */
fun main() {
    val renderApi = preferredRenderApi(System.getProperty("os.name", ""), DevFlags.renderApiOverride)
    renderApi?.let { System.setProperty("skiko.renderApi", it) }
    val device = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
    val scale = device.defaultConfiguration.defaultTransform.scaleX
    val refreshHz = device.displayMode.refreshRate.takeIf { it > 0 } ?: FALLBACK_REFRESH_HZ
    val screen = device.defaultConfiguration.bounds
    val photo = BenchmarkScenarios.photo()
    val verse = BenchmarkScenarios.all(photo).first { it.first == "song verse" }.second
    val results = mutableListOf<IsolationResult>()

    application(exitProcessOnExit = false) {
        Window(
            onCloseRequest = ::exitApplication,
            undecorated = true,
            alwaysOnTop = true,
            title = "Output isolation",
            state = rememberWindowState(
                position = WindowPosition(screen.x.dp, screen.y.dp),
                size = DpSize((1920 / scale).dp, (1080 / scale).dp),
            ),
        ) {
            var frame by remember { mutableIntStateOf(0) }
            Box(Modifier.fillMaxSize().background(Color.Black)) { verse(frame) }
            LaunchedEffect(Unit) {
                repeat(WARMUP_FRAMES) { withFrameNanos { frame++ } }
                for (stallMs in STALLS_MS) {
                    if (stallMs > 0) UiStallInjector.start(UiStallSpec(stallMs, stallMs + STALL_GAP_MS))
                    val gaps = mutableListOf<Long>()
                    val start = withFrameNanos { it }
                    var last = start
                    while (last - start < RUN_NANOS) {
                        val now = withFrameNanos { it }
                        frame++
                        gaps += now - last
                        last = now
                    }
                    UiStallInjector.stop()
                    results += isolationResult(stallMs, refreshHz, gaps.toLongArray())
                }
                exitApplication()
            }
        }
    }

    photo.parentFile.deleteRecursively()
    val markdown = isolationMarkdown(RunInfo.current(WARMUP_FRAMES, 0), renderApi ?: "default", refreshHz, results)
    System.getProperty("isolationBenchmark.reportDir")?.let { dir ->
        File(dir).apply { mkdirs() }.resolve("results.md").writeText(markdown)
    }
}
