package lottiegen.lottie

import kotlinx.serialization.json.JsonObject
import lottiegen.lottie.styles.Style10DoubleLine
import lottiegen.lottie.styles.Style11NewsTicker
import lottiegen.lottie.styles.Style12NewsBadge
import lottiegen.lottie.styles.Style1Bar
import lottiegen.lottie.styles.Style2Boxed
import lottiegen.lottie.styles.Style3Circular
import lottiegen.lottie.styles.Style4Banner
import lottiegen.lottie.styles.Style5GradientBar
import lottiegen.lottie.styles.Style6LineSplit
import lottiegen.lottie.styles.Style7RandomFade
import lottiegen.lottie.styles.Style8Diagonal
import lottiegen.lottie.styles.Style9DiagonalWipe
import lottiegen.lottie.styles.StyleGenerator
import lottiegen.model.LottieGenConfig
import lottiegen.spec.RegistryEntry
import lottiegen.spec.SpecStyleGenerator
import lottiegen.spec.StyleRegistry
import kotlin.math.roundToInt

/**
 * Main entry point for generating Lottie JSON from a config.
 */
object LottieGenerator {

    private val compiledStyles = mapOf(
        "1" to Style1Bar(),
        "2" to Style2Boxed(),
        "3" to Style3Circular(),
        "4" to Style4Banner(),
        "5" to Style5GradientBar(),
        "6" to Style6LineSplit(),
        "7" to Style7RandomFade(),
        "8" to Style8Diagonal(),
        "9" to Style9DiagonalWipe(),
        "10" to Style10DoubleLine(),
        "11" to Style11NewsTicker(),
        "12" to Style12NewsBadge()
    )

    /** Compiled styles overlaid with bundled registry specs — registry wins per id. */
    private val styles: Map<String, StyleGenerator> =
        overlayRegistry(compiledStyles, StyleRegistry.load().entries)

    /**
     * A registry entry with a compiled id replaces that style's renderer (the code-free
     * replacement flow); a new id adds a style. A corrupt/missing spec resource is
     * skipped with a stderr note — it must never break the other styles.
     */
    internal fun overlayRegistry(
        compiled: Map<String, StyleGenerator>,
        registry: List<RegistryEntry>
    ): Map<String, StyleGenerator> {
        val merged = compiled.toMutableMap()
        for (entry in registry) {
            try {
                merged[entry.id] = SpecStyleGenerator.fromResource(entry.resource)
            } catch (e: Exception) {
                System.err.println("Skipping registry style ${entry.id} (${entry.resource}): ${e.message}")
            }
        }
        return merged
    }

    fun generate(config: LottieGenConfig): JsonObject = generate(config, styles[config.style])

    /**
     * Generates with an explicit [style] instead of the dispatch map — used by the
     * developer Style Editor to render draft spec-based styles. A null style keeps the
     * classic behavior for unknown ids: an empty composition.
     */
    fun generate(config: LottieGenConfig, style: StyleGenerator?): JsonObject {
        // Scale baseSize by canvas height ratio and scale factor (matches JS: line 3613)
        val scaledBaseSize = (config.baseSize * (config.canvasH / 1080.0) * config.scaleFactor).roundToInt()
        val cfg = config.copy(baseSize = scaledBaseSize)

        val fr = 60
        val inFrames = (cfg.animDuration * fr).roundToInt()
        val holdFrames = (cfg.holdDuration * fr).roundToInt()
        val outFrames = inFrames

        val builder = LottieBuilder(cfg.canvasW, cfg.canvasH, fr)
        builder.setDuration(inFrames, holdFrames, outFrames)

        style?.generate(builder, cfg)

        return builder.toJson()
    }
}
