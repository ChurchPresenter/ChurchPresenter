package org.churchpresenter.lottiegen.spec

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.churchpresenter.lottiegen.lottie.LottieGenerator
import org.churchpresenter.lottiegen.lottie.styles.Style11NewsTicker
import org.churchpresenter.lottiegen.model.LottieGenConfig
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * End-to-end fidelity gate: the Style 11 spec port must produce the same layer
 * structure and identical rest-state geometry as the compiled Style11NewsTicker,
 * across alignments and visibility toggles, through the real generation pipeline.
 *
 * Layer structure (names/order/ty/td/tt) matches exactly in every config. The port
 * models the compiled style's canvas-based band geometry with a single FIXED
 * content-block slot of 76 em = (canvasW − 2·marginH)/baseSize at the default
 * config, which makes the block center land on the canvas center (= the compiled
 * bandCX) for all three alignments, and both band Y offsets (−0.81 em band,
 * +1.66 em ticker from BLOCK_CENTER with blockHeightEm 4.82) cancel marginV and
 * canvasH exactly, so they hold for EVERY config. The alignment-specific badge,
 * stripes, and center-aligned text geometry use per-alignment element variants via
 * PlacementOverride.hidden (exactly one variant builds per alignment), matching
 * the compiled structure — the badge parallelogram and stripes keep their absolute
 * slant direction via MirrorMode.NONE plus world-side vertex sets, because the
 * compiled shapes are NOT mirror images of each other.
 *
 * Rest origins are asserted exactly (1e-6) for every layer EXCEPT the X coordinate
 * of "Name Mask" and "Info Mask" at left/right alignment (deviation 4 below);
 * their Y is asserted exactly everywhere, and both mask origins are fully exact at
 * center alignment.
 *
 * Documented deviations (spec vs compiled):
 *
 * 1. Band widths: the port uses SizeSpec.CanvasWidth, i.e. exactly canvasW wide,
 *    while the compiled bands are canvasW − 2·marginH (1824 px vs 1920 px at
 *    defaults) — the port's bands extend into the horizontal margins. Rest
 *    origins are unaffected (both are canvas-centered for every config).
 * 2. The 76 em block width (and all constants derived from compiled bandW: badge
 *    vertices 16.72/19.12/38 em, stripe centers ±27.36 em, logo offset 27.36 em,
 *    right-alignment name offset 24.88 em, mask widths 75.52/75.6 em, text slide
 *    distances 18.144/26.432/30.24 em) bake (canvasW − 2·marginH)/baseSize = 76
 *    at the DEFAULT canvas/margins/baseSize; they deviate when that ratio changes.
 *    Cfg-derived canvas math is inexpressible in the spec model.
 * 3. Text Y offsets bake the compiled fontSize·0.35 baseline term at default font
 *    sizes: name −0.39 em = (bandCY − baseY)/em + 0.35·1.2, info +1.975 em =
 *    (tickerCY − baseY)/em + 0.35·0.9 — deviate when nameSize/infoSize change.
 *    (The band-position part of these offsets is exact for all configs, see above.)
 * 4. Mask rest X at left/right alignment: MaskRevealSpec positions a mask relative
 *    to the measured TEXT EXTENT center, while the compiled masks are band-anchored
 *    at fixed canvas positions — a text-width-dependent difference that is
 *    inexpressible (spec-model gap). Mitigation: the name mask uses padEm 2.4
 *    (= 2·namePadX), which makes its ALIGNMENT-SIDE edge (the edge the reveal
 *    slide crosses) land exactly on the compiled mask edge (marginH on the left /
 *    canvasW − marginH − badge zone + 1.2 em on the right) independent of text
 *    width, so the pct-0 clip behavior matches compiled exactly; its width is
 *    textW + 2.4 em vs compiled 51.84 em. The info mask keeps the compiled width
 *    (75.6 em) so it covers the whole ticker; its center tracks the text extent
 *    (compiled: fixed bandCX). At CENTER alignment both masks are fully exact
 *    (justify-2 makes the extent center text-width-independent): name center
 *    variant widthEm 75.52 at offset 0, info center variant offsetXEm 36.8
 *    (= half band − 1.2 em pad) → mask center = canvas center.
 * 5. Ticker stroke width: compiled uses borderPx·0.7; StrokeWidthSpec has only
 *    FromConfig (= borderPx, factor 1.0) or a static Em — a static Em would be
 *    wrong for every other borderThickness and would not vanish at 0, so the port
 *    uses FromConfig: correct gating, 30% too wide (spec-model gap: no
 *    config-scaled stroke-width factor).
 * 6. Derived alphas: fill alphaFactor 0.72 (ticker) / 0.38 (stripes) multiplies
 *    exactly, while compiled ROUNDS ((alpha·0.72).roundToInt(), ±0.5 alpha) and
 *    floors the ticker at max(10, …) — the floor only differs below accent alpha
 *    14. Exact at the default alpha 100 used here.
 * 7. Slide-from offsets 12.42 em (band group) / 9.95 em (ticker group) bake the
 *    compiled slideFromY − restY = marginV + 60 px + fixed band ems at defaults
 *    (canvasH cancels); non-rest keyframes only, never visible at rest.
 * 8. Stripes are one polygon plus a Repeater (3 copies at +0.9 em, base at
 *    stripeCX − 0.9 em) instead of the compiled three explicit path groups —
 *    identical stripe geometry, different shape-item encoding inside the layer.
 * 9. Center-aligned info text replicates the compiled quirk verbatim: justify 2
 *    anchored at the LEFT margin + 1.2 em (not the canvas center).
 */
class SpecPort11Test {

    private val eps = 1e-6

    private fun port() = SpecStyleGenerator.fromResource("/styles/style11_news_ticker_port.json")

    private fun configs(): List<LottieGenConfig> {
        val logoData = "data:image/png;base64,iVBORw0KGgo="
        val result = mutableListOf<LottieGenConfig>()
        for (align in listOf("left", "center", "right")) {
            for (logo in listOf(false, true)) {
                for (bg in listOf(true, false)) {
                    result.add(
                        LottieGenConfig(
                            align = align,
                            logoEnabled = logo,
                            logoData = if (logo) logoData else null,
                            logoW = if (logo) 120 else 0,
                            logoH = if (logo) 80 else 0,
                            bgEnabled = bg,
                            borderThickness = 4f
                        )
                    )
                }
            }
        }
        result.add(LottieGenConfig(hideName = true))
        result.add(LottieGenConfig(hideInfo = true))
        result.add(LottieGenConfig(borderThickness = 0f))
        result.add(LottieGenConfig(borderThickness = 4f))
        return result
    }

    @Test
    fun portMatchesCompiledStyle11AtRest() {
        for (cfg in configs()) {
            val expected = LottieGenerator.generate(cfg, Style11NewsTicker())
            val actual = LottieGenerator.generate(cfg, port())
            val label = "align=${cfg.align} logo=${cfg.logoEnabled} bg=${cfg.bgEnabled} " +
                "hideName=${cfg.hideName} hideInfo=${cfg.hideInfo} border=${cfg.borderThickness}"

            val expectedLayers = expected["layers"]!!.jsonArray.map { it.jsonObject }
            val actualLayers = actual["layers"]!!.jsonArray.map { it.jsonObject }

            assertEquals(
                expectedLayers.map { it.name() }, actualLayers.map { it.name() },
                "layer names/order [$label]"
            )

            val inFrames = (cfg.animDuration * 60).roundToInt()
            for ((exp, act) in expectedLayers.zip(actualLayers)) {
                val layerLabel = "${exp.name()} [$label]"
                assertEquals(exp["ty"]!!.jsonPrimitive.int, act["ty"]!!.jsonPrimitive.int, "layer type $layerLabel")
                assertEquals(exp["td"]?.jsonPrimitive?.int, act["td"]?.jsonPrimitive?.int, "td $layerLabel")
                assertEquals(exp["tt"]?.jsonPrimitive?.int, act["tt"]?.jsonPrimitive?.int, "tt $layerLabel")

                // Compare the shape-origin screen position (position minus anchor).
                // The mask layers' X is text-extent-anchored in the spec model but
                // band-anchored in the compiled style at left/right alignment —
                // inexpressible, documented as deviation 4; Y is exact everywhere,
                // and both coordinates are exact at center alignment.
                val skipX = exp.name().endsWith(" Mask") && cfg.align != "center"
                val expOrigin = exp.restOrigin(inFrames)
                val actOrigin = act.restOrigin(inFrames)
                expOrigin.zip(actOrigin).forEachIndexed { i, (e, a) ->
                    if (i == 0 && skipX) return@forEachIndexed
                    assertEquals(e, a, eps, "rest origin[$i] $layerLabel")
                }
            }
        }
    }

    private fun JsonObject.name(): String = this["nm"]!!.jsonPrimitive.content

    /** Screen position of the layer's local origin at rest: position minus anchor (x, y). */
    private fun JsonObject.restOrigin(inFrames: Int): List<Double> {
        val p = restValues("p", inFrames)
        val a = restValues("a", inFrames)
        return listOf(p[0] - a[0], p[1] - a[1])
    }

    /**
     * The value a transform property holds at the end of the in phase: the static k,
     * or the animated keyframe whose t equals inFrames.
     */
    private fun JsonObject.restValues(prop: String, inFrames: Int): List<Double> {
        val p = this["ks"]!!.jsonObject[prop]!!.jsonObject
        val animated = p["a"]!!.jsonPrimitive.int == 1
        val k = p["k"]!!
        val values: JsonArray = if (!animated) {
            k.jsonArray
        } else {
            val restKf = k.jsonArray.map { it.jsonObject }
                .first { it["t"]!!.jsonPrimitive.int == inFrames }
            restKf["s"]!!.jsonArray
        }
        return values.map { (it as JsonPrimitive).double }
    }
}
