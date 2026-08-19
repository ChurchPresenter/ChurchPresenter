package presentation.engine.tools

import presentation.engine.DeckRasterizer
import presentation.engine.LoadResult
import presentation.engine.PresentationLoader
import presentation.engine.model.EffectSpec
import presentation.engine.model.LayerSpec
import java.io.File
import javax.imageio.ImageIO

/**
 * Coverage-audit tool: dumps how the engine parses a real presentation — layers, compiled
 * timeline steps, transitions, and every degrade warning. Run against a user-reported deck to
 * turn "animation looks wrong" into a data-entry fix in PresetCatalog/TimelineCompiler.
 *
 * Usage: `./gradlew dumpTiming -Pfile=/path/to/deck.pptx`
 */
object DumpTiming {

    @JvmStatic
    fun main(args: Array<String>) {
        val path = args.firstOrNull() ?: run {
            System.err.println("usage: DumpTiming <file.pptx|file.key|file.pdf>")
            return
        }
        val file = File(path)
        val deck = when (val result = PresentationLoader.load(file)) {
            is LoadResult.Failure -> {
                println("LOAD FAILED: ${result.error} ${result.detail ?: ""}")
                return
            }
            is LoadResult.Success -> result.deck
        }
        println("=== ${file.name} — ${deck.format}, ${deck.slideCount} slides, " +
            "${deck.slideWidthPt}x${deck.slideHeightPt}pt ===")
        for (slide in deck.slides) {
            println()
            println("Slide ${slide.index + 1}  fidelity=${slide.fidelity}" +
                (slide.transition?.let { "  transition=${it.type}/${it.direction} ${it.durationMs}ms advTm=${it.advanceAfterMs}" } ?: ""))
            for (layer in slide.layers) {
                val detail = when (layer) {
                    is LayerSpec.Background -> "shapes=${layer.shapeIndexes}"
                    is LayerSpec.Shape -> "shapeIndex=${layer.shapeIndex}"
                    is LayerSpec.ParagraphText -> "shape=${layer.shapeIndex} para=${layer.paragraphIndex}"
                    is LayerSpec.StaticComposite -> "static"
                    is LayerSpec.Media -> "media=${layer.mediaFile}"
                }
                println("  layer ${layer.id} z=${layer.zIndex} visible=${layer.initiallyVisible} " +
                    "boundsPt=${layer.boundsPt} $detail")
            }
            val timeline = slide.timeline
            if (timeline == null) {
                println("  (no timeline)")
            } else {
                timeline.steps.forEachIndexed { stepIndex, step ->
                    println("  step ${stepIndex + 1}:")
                    for (interval in step.intervals) {
                        println("    ${interval.layerId}  ${describe(interval.effect)}  " +
                            "begin=${interval.beginMs} dur=${interval.durMs} repeat=${interval.repeat} fill=${interval.fill}")
                    }
                }
            }
        }
        println()
        if (deck.warnings.isEmpty()) {
            println("No degrade warnings — full coverage for this deck.")
        } else {
            println("DEGRADE WARNINGS (${deck.warnings.size}):")
            deck.warnings.forEach { println("  - $it") }
        }
        // Smoke-render the first slide so raster failures show up here too. With a second
        // argument (a directory), every slide's final frame is written as PNG for inspection.
        DeckRasterizer(deck, targetWidthPx = 960).use { rasterizer ->
            val outDir = args.getOrNull(1)?.let { File(it).apply { mkdirs() } }
            if (outDir == null) {
                val frame = rasterizer.renderFinalFrame(0)
                println("First slide renders at ${frame.width}x${frame.height}.")
            } else {
                for (slide in deck.slides) {
                    val frame = rasterizer.renderFinalFrame(slide.index)
                    val out = File(outDir, "slide_%02d.png".format(slide.index + 1))
                    ImageIO.write(DeckRasterizer.flattenToRgb(frame), "png", out)
                    println("Wrote ${out.absolutePath} (${frame.width}x${frame.height})")
                }
            }
        }
    }

    private fun describe(effect: EffectSpec): String = when (effect) {
        is EffectSpec.Custom -> "Custom(${effect.role}, curves=${
            effect.curves.joinToString { c -> "${c.property}=${c.keyframes}" }
        })"
        else -> effect.toString()
    }
}
