package org.churchpresenter.app.churchpresenter.benchmark

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * How long each kind of content takes to render on an off-screen output, at 1080p and 4K.
 *
 * Not part of `jvmTest`: it runs as `./gradlew :composeApp:renderBenchmark`, alone in its JVM, and
 * writes `results.json` and `results.md` to `build/reports/render-benchmark/`. With
 * `-PrecordRenderBaseline` it also writes them over the committed baseline in
 * `composeApp/benchmarks/`, and with `-PenforceRenderBudget` it fails when a scenario's render and
 * readback together run past one frame -- 16.7 ms at 1080p, 33.3 ms at 4K -- at p99.
 *
 * This is the path the NDI, OMT and Browser Source outputs take: an `ImageComposeScene` on the CPU
 * raster, read back through their own `FrameBuffer`. The on-screen output windows draw through the
 * GPU and are not measured here. Video (VLC), web pages (JCEF), cameras and network sources are not
 * either: each needs a device or a native runtime the build machine does not have.
 *
 * Every scenario changes something each frame where its content allows, so the numbers include
 * recomposition and layout, not only drawing.
 */
class RenderBenchmark {

    private val timer = FrameTimer(
        warmupFrames = intProperty("renderBenchmark.warmupFrames", 30),
        warmupMillis = longProperty("renderBenchmark.warmupMillis", 1_500),
        measuredFrames = intProperty("renderBenchmark.frames", 120),
    )

    private val sizes = listOf(1920 to 1080, 3840 to 2160)

    @Test
    fun `every content type renders inside its frame budget`() {
        val photo = BenchmarkScenarios.photo()
        val scenarios = BenchmarkScenarios.all(photo, sizes)
        val results = sizes.flatMap { (w, h) ->
            scenarios.map { (name, content) -> timer.measure(name, w, h, content) }
        }
        val blank = results.filter { it.drawnPixels == 0 }
        assertTrue(blank.isEmpty(), "drew nothing, so measured nothing: " + blank.joinToString { it.scenario })
        val info = RunInfo.current(timer.warmupFrames, timer.measuredFrames)
        val json = toJson(info, results)
        val markdown = toMarkdown(info, results)
        writeTo(System.getProperty("renderBenchmark.reportDir"), json, markdown)
        if (System.getProperty("renderBenchmark.record").toBoolean()) {
            writeTo(System.getProperty("renderBenchmark.baselineDir"), json, markdown)
        }
        if (System.getProperty("renderBenchmark.enforce").toBoolean()) {
            val over = overBudget(results)
            assertTrue(
                over.isEmpty(),
                "over budget: " + over.joinToString { "${it.scenario} ${it.width}x${it.height}" },
            )
        }
        photo.parentFile.deleteRecursively()
    }

    private fun writeTo(dir: String?, json: String, markdown: String) {
        if (dir.isNullOrBlank()) return
        val out = File(dir).apply { mkdirs() }
        File(out, "results.json").writeText(json)
        File(out, "results.md").writeText(markdown)
    }

    private companion object {
        fun intProperty(name: String, default: Int) = System.getProperty(name)?.toIntOrNull() ?: default
        fun longProperty(name: String, default: Long) = System.getProperty(name)?.toLongOrNull() ?: default
    }
}
