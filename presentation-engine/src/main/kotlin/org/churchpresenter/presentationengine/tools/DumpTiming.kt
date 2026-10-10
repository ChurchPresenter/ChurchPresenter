package org.churchpresenter.presentationengine.tools

import org.churchpresenter.presentationengine.DeckRasterizer
import org.churchpresenter.presentationengine.LoadResult
import org.churchpresenter.presentationengine.PresentationLoader
import org.churchpresenter.presentationengine.model.EffectSpec
import org.churchpresenter.presentationengine.model.LayerSpec
import java.io.File
import java.io.PrintStream
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
    fun main(args: Array<String>) = dump(args, System.out, System.err)

    internal fun dump(args: Array<String>, out: PrintStream, err: PrintStream) {
        val path = args.firstOrNull() ?: run {
            err.println("usage: DumpTiming <file.pptx|file.key|file.pdf>")
            return
        }
        val file = File(path)
        val deck = when (val result = PresentationLoader.load(file)) {
            is LoadResult.Failure -> {
                out.println("LOAD FAILED: ${result.error} ${result.detail ?: ""}")
                return
            }
            is LoadResult.Success -> result.deck
        }
        out.println("=== ${file.name} — ${deck.format}, ${deck.slideCount} slides, " +
            "${deck.slideWidthPt}x${deck.slideHeightPt}pt ===")
        for (slide in deck.slides) {
            out.println()
            val transition = slide.transition?.let {
                "  transition=${it.type}/${it.direction} ${it.durationMs}ms advTm=${it.advanceAfterMs}"
            } ?: ""
            out.println("Slide ${slide.index + 1}  fidelity=${slide.fidelity}$transition")
            for (layer in slide.layers) {
                val detail = when (layer) {
                    is LayerSpec.Background -> "shapes=${layer.shapeIndexes}"
                    is LayerSpec.Shape -> "shapeIndex=${layer.shapeIndex}"
                    is LayerSpec.ParagraphText -> "shape=${layer.shapeIndex} para=${layer.paragraphIndex}"
                    is LayerSpec.StaticComposite -> "static"
                    is LayerSpec.Media -> "media=${layer.mediaFile}"
                }
                out.println("  layer ${layer.id} z=${layer.zIndex} visible=${layer.initiallyVisible} " +
                    "boundsPt=${layer.boundsPt} $detail")
            }
            val timeline = slide.timeline
            if (timeline == null) {
                out.println("  (no timeline)")
            } else {
                timeline.steps.forEachIndexed { stepIndex, step ->
                    out.println("  step ${stepIndex + 1}:")
                    for (interval in step.intervals) {
                        out.println("    ${interval.layerId}  ${describe(interval.effect)}  " +
                            "begin=${interval.beginMs} dur=${interval.durMs} " +
                            "repeat=${interval.repeat} fill=${interval.fill}")
                    }
                }
            }
        }
        out.println()
        if (deck.warnings.isEmpty()) {
            out.println("No degrade warnings — full coverage for this deck.")
        } else {
            out.println("DEGRADE WARNINGS (${deck.warnings.size}):")
            deck.warnings.forEach { out.println("  - $it") }
        }
        // Smoke-render the first slide so raster failures show up here too. With a second
        // argument (a directory), every slide's final frame is written as PNG for inspection.
        DeckRasterizer(deck, targetWidthPx = 960).use { rasterizer ->
            val outDir = args.getOrNull(1)?.let { File(it).apply { mkdirs() } }
            if (outDir == null) {
                val frame = rasterizer.renderFinalFrame(0)
                out.println("First slide renders at ${frame.width}x${frame.height}.")
            } else {
                for (slide in deck.slides) {
                    val frame = rasterizer.renderFinalFrame(slide.index)
                    val frameFile = File(outDir, "slide_%02d.png".format(slide.index + 1))
                    ImageIO.write(DeckRasterizer.flattenToRgb(frame), "png", frameFile)
                    out.println("Wrote ${frameFile.absolutePath} (${frame.width}x${frame.height})")
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
