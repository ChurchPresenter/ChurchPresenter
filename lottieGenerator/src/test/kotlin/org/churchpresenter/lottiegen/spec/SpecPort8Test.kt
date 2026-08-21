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
import org.churchpresenter.lottiegen.lottie.styles.Style8Diagonal
import org.churchpresenter.lottiegen.model.LottieGenConfig
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end fidelity gate: the Style 8 spec port must produce the same layer
 * structure and identical rest-state geometry as the compiled Style8Diagonal, across
 * alignments and visibility toggles, through the real generation pipeline.
 *
 * Layer structure (names/order/ty/td/tt) matches exactly in every config, including
 * the per-alignment element-variant split: the compiled style removes the slant at
 * center alignment, modeled as a slanted PolygonElement hidden on center plus a
 * rectangular variant hidden on left+right (identical layer names, exactly one
 * variant builds per alignment). Rest origins are asserted exactly (1e-6) for every
 * layer in the alignment × logo × bg matrix — the compiled style ignores the logo
 * entirely, so those configs are geometry-identical to their no-logo twins.
 *
 * Documented deviations (spec vs compiled):
 *
 * 1. Bar vertical rest position: compiled barFinalY = canvasH − barH/2 with
 *    barH = infoSize + lineSpacing·0.05 + nameSize + 5·0.6 em (+ 2.5 em slant off
 *    center) — content- and hide-toggle-derived. The spec anchors on baseY
 *    (blockHeightEm 3.5 + cfg.marginV, which the compiled style never uses) with
 *    constant offsets (−0.055 em slanted / +1.195 em center), exact at the DEFAULT
 *    config with both lines visible. hideName/hideInfo shrink the compiled bar
 *    (and shift the surviving text line with it), which no constant offset or
 *    LineAnchor can express — for those configs the test compares X only, for
 *    every layer.
 * 2. Text vertical rest positions: same inexpressible contentH math (compiled
 *    infoY = canvasH − nameH − gap − 0.6·infoH − 3·0.6 em, nameY analogous); the
 *    port uses BLOCK_CENTER + 0.2 em / 1.95 em, exact at default
 *    nameSize/infoSize/lineSpacing/marginV with both lines visible.
 * 3. Bar horizontal geometry: compiled bar spans canvasW + 2·2 em and is centered
 *    at canvasW/2 for every alignment. Polygon vertices are static em, so the
 *    ±42 em half-width and the 38 em block-start offset that lands the layer
 *    position on canvasW/2 encode canvasW 1920 / marginH 2 / baseSize 24 —
 *    exact at the default canvas only. Flow-signed vertex x mirrors the slant's
 *    handedness on right alignment automatically (vertex order/winding differs
 *    from compiled; the shape set is identical).
 * 4. Slide-up entrance start offset: compiled slideFrom − barFinalY = 1.5·barH;
 *    spec uses static 11.415 em (slanted) / 7.665 em (center) — equal at the
 *    default config. Non-rest keyframes only, never visible at rest.
 * 5. Text animator per-character offset: compiled −fontSizePx·0.6; spec posOffsetEm
 *    is em of baseSize, so −0.54 em (= −0.6·0.9 infoSize) / −0.72 em
 *    (= −0.6·1.2 nameSize) — exact at the default font sizes, deviates when
 *    nameSize/infoSize change. Animator presence/count is asserted; its inner
 *    values are not.
 * 6. Spec-model gap — stroke width factor: compiled Style 8 scales the diagonal
 *    line as borderThickness·baseSize·0.15; StrokeWidthSpec.FromConfig hardcodes
 *    the classic 0.1 factor and Em cannot reference cfg.borderThickness, so the
 *    port's line renders 2/3 the compiled width (9.6 vs 14.4 px at border 4).
 *    FromConfig was chosen over a static Em(0.6) to preserve proportional response
 *    to the border-thickness setting and the exact >0 on/off gating (BORDER_SET
 *    matches compiled `borderPx > 0`). Width is paint, not rest-origin geometry —
 *    not asserted here.
 */
class SpecPort8Test {

    private val eps = 1e-6

    private fun port() = SpecStyleGenerator.fromResource("/styles/style8_diagonal_port.json")

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
    fun portMatchesCompiledStyle8AtRest() {
        for (cfg in configs()) {
            val expected = LottieGenerator.generate(cfg, Style8Diagonal())
            val actual = LottieGenerator.generate(cfg, port())
            val label = "align=${cfg.align} logo=${cfg.logoEnabled} bg=${cfg.bgEnabled} " +
                "hideName=${cfg.hideName} hideInfo=${cfg.hideInfo} border=${cfg.borderThickness}"

            val expectedLayers = expected["layers"]!!.jsonArray.map { it.jsonObject }
            val actualLayers = actual["layers"]!!.jsonArray.map { it.jsonObject }

            assertEquals(
                expectedLayers.map { it.name() }, actualLayers.map { it.name() },
                "layer names/order [$label]"
            )

            // Hiding a line shrinks the compiled bar height, which shifts every layer's
            // compiled rest Y in a way no constant spec offset can express (documented
            // deviation 1/2) — X stays exact and is always asserted.
            val compareY = !cfg.hideName && !cfg.hideInfo

            val inFrames = (cfg.animDuration * 60).roundToInt()
            for ((exp, act) in expectedLayers.zip(actualLayers)) {
                val layerLabel = "${exp.name()} [$label]"
                assertEquals(exp["ty"]!!.jsonPrimitive.int, act["ty"]!!.jsonPrimitive.int, "layer type $layerLabel")
                assertEquals(exp["td"]?.jsonPrimitive?.int, act["td"]?.jsonPrimitive?.int, "td $layerLabel")
                assertEquals(exp["tt"]?.jsonPrimitive?.int, act["tt"]?.jsonPrimitive?.int, "tt $layerLabel")

                // Compare the shape-origin screen position (position minus anchor):
                // equivalent encodings must land on the same pixels at rest.
                val expOrigin = exp.restOrigin(inFrames)
                val actOrigin = act.restOrigin(inFrames)
                assertEquals(expOrigin[0], actOrigin[0], eps, "rest origin[0] $layerLabel")
                if (compareY) {
                    assertEquals(expOrigin[1], actOrigin[1], eps, "rest origin[1] $layerLabel")
                }

                // Text layers must carry the same per-character reveal animators.
                if (exp["ty"]!!.jsonPrimitive.int == 5) {
                    val expAnimators = exp["t"]!!.jsonObject["a"]!!.jsonArray.size
                    val actAnimators = act["t"]!!.jsonObject["a"]!!.jsonArray.size
                    assertTrue(expAnimators > 0, "compiled text animator expected $layerLabel")
                    assertEquals(expAnimators, actAnimators, "text animator count $layerLabel")
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
     * or the latest animated keyframe at or before inFrames — Style 8's slide-up
     * finishes at 40% of the in phase, so unlike the Style 1/2 ports no keyframe
     * lands exactly on inFrames (the next one is the hold keyframe).
     */
    private fun JsonObject.restValues(prop: String, inFrames: Int): List<Double> {
        val p = this["ks"]!!.jsonObject[prop]!!.jsonObject
        val animated = p["a"]!!.jsonPrimitive.int == 1
        val k = p["k"]!!
        val values: JsonArray = if (!animated) {
            k.jsonArray
        } else {
            val restKf = k.jsonArray.map { it.jsonObject }
                .last { it["t"]!!.jsonPrimitive.int <= inFrames }
            restKf["s"]!!.jsonArray
        }
        return values.map { (it as JsonPrimitive).double }
    }
}
