package lottiegen.spec

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import lottiegen.lottie.LottieGenerator
import lottiegen.lottie.styles.Style3Circular
import lottiegen.model.LottieGenConfig
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * End-to-end fidelity gate: the Style 3 spec port must produce the same layer
 * structure and identical rest-state geometry as the compiled Style3Circular, across
 * alignments and visibility toggles, through the real generation pipeline.
 *
 * Layer structure (names/order/ty/td/tt) matches exactly in every config. Rest
 * origins are asserted exactly (1e-6) for every layer and both coordinates, with a
 * single carve-out: the Name Mask / Info Mask x coordinate at CENTER alignment
 * (deviation 1 below) — their y is still asserted exactly.
 *
 * Layout mapping (exact for ALL configs unless noted): the 5.5 em circle badge is a
 * LOGO_ENABLED-gated FIXED slot, so slot collapse reproduces the compiled
 * hasLogo/no-logo text shift; blockHeightEm 5.5 = circleSize reproduces baseY
 * exactly; the text slot's 0.75 em gapBefore is the compiled textOffset.
 *
 * Documented deviations (spec vs compiled):
 *
 * 1. Center-alignment justify: compiled Style 3 uses `if (isRight) 1 else 0` — its
 *    center-aligned text is LEFT-justified, left edge anchored at the layer position
 *    (textBaseX), extending rightward. The spec model derives justify from alignment
 *    (center → 2), which is not overridable. Per the port convention the rest x is
 *    kept equal (the text layers' p matches compiled exactly in every config,
 *    including center), so the deviation is purely in the text data's justify code:
 *    the spec's center-aligned text draws centered ON the rest position rather than
 *    extending rightward from it — the visual text extent shifts left by
 *    textWidth/2 relative to compiled. The masks stay centered on the spec's own
 *    justify-2 text (the generator derives the mask x from the justify code), so the
 *    spec output is self-consistent (text never clipped), but the mask x at center
 *    is rest.x where compiled is rest.x + textWidth/2 — textWidth is a measured
 *    value the spec model cannot express, hence the mask-x-at-center exclusion.
 * 2. "pad" slot width 0.6666666666666666 em encodes 0.25 em + 10 px: compiled
 *    textBlockW pads the measured text by 20 px while the layout engine's TEXT core
 *    pad is a fixed 10 px, and the compiled bar overhang is 1.0 em while the text
 *    gap is 0.75 em — the pad slot absorbs both remainders so the block width equals
 *    the compiled totalW for every logo/alignment combination. The 10 px term is
 *    exact only at baseSize 24 (default canvas/scaleFactor, as in this matrix).
 * 3. Background Bar size is TextWrap(NAME, padXEm 0.9166666666666666, padYEm 1.9):
 *    compiled bgBarW = max(nameW, infoW) + 20 px + 1 em. No SizeSpec can express
 *    max() (ContentDerived includes the badge slot when the logo is on, and there
 *    is no LOGO_DISABLED rule to build per-logo-state variants), so the port wraps
 *    the NAME field — exact whenever the name measures at least as wide as the info
 *    line (true at the default texts/sizes and for this whole matrix; deviates when
 *    the info line is wider). padXEm encodes 10 px + 0.5 em per side (10 px term
 *    exact at baseSize 24); padYEm 1.9 encodes (5.5 − 0.5 − nameSize) em / 2, exact
 *    at the default nameSize 1.2 (bar height only — never position-relevant).
 *    The bar's rest position is exact in every config regardless of logo state:
 *    anchored at the block END with a constant −0.5 ELEMENT_WIDTH offset (and the
 *    grow animation starts at −1.0 = the compiled badge-edge/margin-edge origin).
 * 4. Spec-model gap (same as the Style 2 port): compiled gates Logo AND Logo BG on
 *    `logoEnabled && logoData != null`; a RectElement can only test LOGO_ENABLED,
 *    so with logoEnabled=true but logoData=null the port would still build
 *    "Logo BG" (compiled builds neither). Not representable and not exercised —
 *    the app never enables the logo without data.
 * 5. Text slide-in start offset 5.61 em = the compiled circleSize · 1.02; identical
 *    up to floating-point rounding, and a non-rest keyframe only.
 */
class SpecPort3Test {

    private val eps = 1e-6

    private fun port() = SpecStyleGenerator.fromResource("/styles/style3_circular_port.json")

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
    fun portMatchesCompiledStyle3AtRest() {
        for (cfg in configs()) {
            val expected = LottieGenerator.generate(cfg, Style3Circular())
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

                // Compare the shape-origin screen position (position minus anchor):
                // equivalent encodings (static vs constant-animated position) must
                // land on the same pixels at rest. The mask x at center alignment is
                // the one documented exclusion (KDoc deviation 1): the compiled mask
                // sits at textBaseX + textWidth/2 (left-justified text), the spec's
                // at its own justify-2 text center — a measured-width offset the
                // spec model cannot express.
                val skipX = cfg.align == "center" && exp.name().endsWith(" Mask")
                val expOrigin = exp.restOrigin(inFrames)
                val actOrigin = act.restOrigin(inFrames)
                if (!skipX) {
                    assertEquals(expOrigin[0], actOrigin[0], eps, "rest origin[0] $layerLabel")
                }
                assertEquals(expOrigin[1], actOrigin[1], eps, "rest origin[1] $layerLabel")
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
