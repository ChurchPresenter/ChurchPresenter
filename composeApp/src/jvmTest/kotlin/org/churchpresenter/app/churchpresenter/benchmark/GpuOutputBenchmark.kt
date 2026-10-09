package org.churchpresenter.app.churchpresenter.benchmark

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import org.churchpresenter.sharedui.utils.DevFlags
import java.awt.GraphicsEnvironment
import java.io.File

private const val DEFAULT_WARMUP_FRAMES = 60
private const val DEFAULT_FRAMES = 240
private const val FALLBACK_REFRESH_HZ = 60

/**
 * How each content type runs on an **on-screen** output window -- the projector path, drawn by Skia
 * on the GPU -- which [RenderBenchmark] cannot measure: it renders off-screen on the CPU raster, the
 * NDI/OMT/Browser Source path. Not a test, because tests run headless; `./gradlew
 * :composeApp:gpuBenchmark` runs this `main` on the test classpath, on a machine with a display.
 *
 * Each scenario gets its own undecorated window at 1920x1080 device pixels, and at 3840x2160 where
 * the screen holds it, on the render API the app itself would pick. The content changes every frame;
 * what is recorded is the gap between consecutive frames, which is what the audience sees: on a
 * 60 Hz display a steady output is 16.7 ms apart, and a gap past one and a half refreshes is a
 * dropped frame. The report goes to `gpuBenchmark.reportDir`, and to `gpuBenchmark.baselineDir`
 * when `gpuBenchmark.record` is set.
 */
fun main() {
    val renderApi = preferredRenderApi(System.getProperty("os.name", ""), DevFlags.renderApiOverride)
    renderApi?.let { System.setProperty("skiko.renderApi", it) }
    val warmupFrames = System.getProperty("gpuBenchmark.warmupFrames")?.toIntOrNull() ?: DEFAULT_WARMUP_FRAMES
    val frames = System.getProperty("gpuBenchmark.frames")?.toIntOrNull() ?: DEFAULT_FRAMES

    val device = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
    val scale = device.defaultConfiguration.defaultTransform.scaleX
    val refreshHz = device.displayMode.refreshRate.takeIf { it > 0 } ?: FALLBACK_REFRESH_HZ
    val screen = device.defaultConfiguration.bounds
    val sizes = listOf(1920 to 1080, 3840 to 2160).filter { (w, h) ->
        w / scale <= screen.width && h / scale <= screen.height
    }

    val photo = BenchmarkScenarios.photo()
    val scenarios = BenchmarkScenarios.all(photo, sizes)
    val runs = sizes.flatMap { size -> scenarios.map { size to it } }
    val results = mutableListOf<GpuResult>()

    application(exitProcessOnExit = false) {
        var index by remember { mutableIntStateOf(0) }
        if (index < runs.size) {
            val (size, scenario) = runs[index]
            key(index) {
                Window(
                    onCloseRequest = ::exitApplication,
                    undecorated = true,
                    alwaysOnTop = true,
                    title = "GPU benchmark",
                    state = rememberWindowState(
                        position = WindowPosition(screen.x.dp, screen.y.dp),
                        size = DpSize((size.first / scale).dp, (size.second / scale).dp),
                    ),
                ) {
                    var frame by remember { mutableIntStateOf(0) }
                    Box(Modifier.fillMaxSize().background(Color.Black)) { scenario.second(frame) }
                    LaunchedEffect(Unit) {
                        repeat(warmupFrames) { withFrameNanos { frame++ } }
                        val gaps = LongArray(frames)
                        var last = withFrameNanos { it }
                        for (i in 0 until frames) {
                            val now = withFrameNanos { it }
                            frame++
                            gaps[i] = now - last
                            last = now
                        }
                        results += gpuResult(scenario.first, size.first, size.second, refreshHz, gaps)
                        index++
                    }
                }
            }
        } else {
            LaunchedEffect(Unit) { exitApplication() }
        }
    }

    photo.parentFile.deleteRecursively()
    val info = RunInfo.current(warmupFrames, frames)
    val api = renderApi ?: "default"
    val json = gpuJson(info, api, results)
    val markdown = gpuMarkdown(info, api, results)
    listOfNotNull(
        System.getProperty("gpuBenchmark.reportDir"),
        System.getProperty("gpuBenchmark.baselineDir")
            .takeIf { System.getProperty("gpuBenchmark.record").toBoolean() },
    ).forEach { dir ->
        val out = File(dir).apply { mkdirs() }
        File(out, "results.json").writeText(json)
        File(out, "results.md").writeText(markdown)
    }
}
