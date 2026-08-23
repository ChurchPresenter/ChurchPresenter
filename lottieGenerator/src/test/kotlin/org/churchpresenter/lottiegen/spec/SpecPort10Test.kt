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
import org.churchpresenter.lottiegen.lottie.styles.Style10DoubleLine
import org.churchpresenter.lottiegen.model.LottieGenConfig
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * End-to-end fidelity gate: the Style 10 spec port must produce the same layer
 * structure and identical rest-state geometry as the compiled Style10DoubleLine,
 * across alignments and visibility toggles, through the real generation pipeline.
 *
 * Layer structure (names/order/ty/td/tt) matches exactly in every config. Rest
 * origins are asserted exactly (1e-6) for every non-excluded coordinate. The port's
 * static em constants are tuned to be exact at the DEFAULT config (baseSize 24,
 * nameSize 1.2, infoSize 0.9, logoSize 3.5, marginH/V 2, borderThickness 0 — i.e.
 * hairline linePx = 2 px) — so the whole align × logo × bg matrix (run at the
 * default borderThickness 0) is asserted fully exact except the Info Mask x noted
 * below.
 *
 * Documented deviations (spec vs compiled, and the resulting exclusions):
 *
 * 1. Line thickness (linePx = max(2, borderThickness·baseSize·0.1)) shifts the
 *    whole vertical stack in the compiled style: topLineCY, nameCY/nameTextY and
 *    bottomLineCY carry linePx coefficients −1.5/−1/−0.5 while infoCY carries 0.
 *    The spec model has no borderThickness-driven length, so all y offsets are
 *    static em tuned at linePx = 2 px (borderThickness 0, the app default).
 *    → For configs with borderThickness > 0 the y of Top Line, Bottom Line, Name
 *    and Name Mask is excluded (Info line/mask y stays exact — linePx cancels
 *    there). Their x is exact. The rendered line rect height also stays at the
 *    2 px default instead of thickening (TextWrap padY is a static −0.5583… em;
 *    rect sizes are not asserted by this test).
 * 2. Compiled lineW = max(visible nameW, visible infoW pads incl.) so hiding a
 *    line can shrink it; the layout engine's TEXT slot core always measures both
 *    fields ("hiding a line does not shrink the block") and the line rects size
 *    from TextWrap(NAME). → In the hideName config the lines' x (and width) track
 *    the name instead of the info field: Top/Bottom Line x excluded there. In the
 *    hideInfo config the name field is the widest anyway → x stays exact.
 * 3. Compiled Style 10 collapses vertical gaps for hidden lines (totalH shrinks),
 *    which the fixed-blockHeight spec model cannot express.
 *    → hideName: Top Line y excluded (name row collapse; Bottom Line and Info
 *    keep their exact y — both hang off the bottom edge independent of the name
 *    row). hideInfo: y excluded for all remaining layers (Name Mask, Name,
 *    Top Line, Bottom Line — everything shifts down by the removed info row);
 *    x stays exact.
 * 4. Info Mask x: the compiled info mask is the full line width (driven by the
 *    WIDEST field, i.e. nameW + 1.7 em at defaults) centered on the line; the
 *    MaskRevealSpec can only derive width/position from its OWN field's measured
 *    extent (infoW + padEm). The needed offset ((nameW − infoW)/2) depends on
 *    measured font metrics and is not expressible as a static em (and baking
 *    machine-dependent Verdana metrics into the JSON would be fragile).
 *    → Info Mask x excluded for left/right aligns while the name is visible
 *    (center is exact: both masks center on canvasW/2; hideName is exact too —
 *    the compiled line width degrades to the info field there, matching the
 *    spec's own-field derivation, verified and asserted). Visually equivalent:
 *    the spec mask still covers the info text with 0.85 em to spare on each
 *    side, so the reveal reads the same.
 * 5. Name Mask width/x are exact only while the name line is the widest and the
 *    4 em minimum line width doesn't bind (true at the default texts this matrix
 *    uses); same for the line x offset 0.6416… em, which also folds in the layout
 *    engine's fixed 10 px text-core pad (exact at baseSize 24 only).
 * 6. Logo scale: compiled fits the logo's HEIGHT to logoSize em
 *    (scale = logoSizePx/logoH); the spec engine fits the larger dimension
 *    (max(logoW, logoH)). With the 120×80 test logo the spec logo renders at
 *    70% instead of 105%. Scale is not asserted (rest origin only, p−a), but this
 *    is a visible spec-model gap for non-square logos.
 * 7. Center-aligned logo: compiled hangs it left of the widest VISIBLE text
 *    width; the port's offsetXEm −2.0416… em (= −(1 + logoSize)/2 em + 5 px)
 *    cancels the measured width exactly for any text at default logoSize/baseSize
 *    with both lines visible, but deviates if a line is hidden while the logo is
 *    shown centered (not in this matrix).
 * 8. Entrance slide start offsets (name +2.0616… em / info −1.6566… em, measured
 *    from the bottom line at rest) are static em tuned at the default metrics —
 *    non-rest keyframes only, never visible at rest and not asserted.
 * 9. Spec-model gap: compiled gates the logo on `logoEnabled && logoData != null`;
 *    the LOGO slot rule can only test LOGO_ENABLED, so with logoEnabled=true but
 *    logoData=null the slot would still reserve width (the logo layer itself is
 *    skipped either way). Not representable and not exercised — the app never
 *    enables the logo without data.
 */
class SpecPort10Test {

    private val eps = 1e-6

    private fun port() = SpecStyleGenerator.fromResource("/styles/style10_double_line_port.json")

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
                            logoW = if (logo) 80 else 0,
                            logoH = if (logo) 80 else 0,
                            bgEnabled = bg,
                            // The port is tuned at the default hairline (linePx = 2 px);
                            // borderThickness > 0 is covered by the extra below with its
                            // documented y deviations (deviation 1).
                            borderThickness = 0f
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

    /**
     * Coordinates excluded from the exact rest-origin comparison for this config —
     * each exclusion maps to a numbered deviation in the class KDoc.
     */
    private fun excludedCoords(cfg: LottieGenConfig, layerName: String): Set<Int> {
        val x = 0
        val y = 1
        val excluded = mutableSetOf<Int>()
        // Deviation 4: info mask width derives from the info field, not the widest line.
        // With hideName the compiled line width degrades to the info field too, so the
        // mask x becomes exact again and stays asserted.
        if (layerName == "Info Mask" && cfg.align != "center" && !cfg.hideName) excluded.add(x)
        // Deviation 1: linePx-dependent vertical stack, tuned at borderThickness 0.
        if (cfg.borderThickness > 0 && layerName in setOf("Top Line", "Bottom Line", "Name", "Name Mask", "Logo")) {
            excluded.add(y)
        }
        // Deviations 2 + 3: hidden-line collapse.
        if (cfg.hideName) {
            if (layerName == "Top Line") excluded.add(y)
            if (layerName in setOf("Top Line", "Bottom Line")) excluded.add(x)
        }
        if (cfg.hideInfo && layerName in setOf("Name Mask", "Name", "Top Line", "Bottom Line")) {
            excluded.add(y)
        }
        return excluded
    }

    @Test
    fun portMatchesCompiledStyle10AtRest() {
        for (cfg in configs()) {
            val expected = LottieGenerator.generate(cfg, Style10DoubleLine())
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
                // equivalent encodings (edge + offset anchor vs. center + zero anchor)
                // must land on the same pixels at rest.
                val excluded = excludedCoords(cfg, exp.name())
                val expOrigin = exp.restOrigin(inFrames)
                val actOrigin = act.restOrigin(inFrames)
                expOrigin.zip(actOrigin).forEachIndexed { i, (e, a) ->
                    if (i !in excluded) {
                        assertEquals(e, a, eps, "rest origin[$i] $layerLabel")
                    }
                }
            }
        }
    }


    /**
     * A logo wider than it is tall: layer structure only, deliberately not geometry.
     *
     * The matrix above uses a square logo because that is where the two models agree exactly. The
     * compiled style scales the logo by HEIGHT, so a wide logo draws wider than `logoSize` and the
     * compiled geometry now widens the gutter (and, in Style 5, the plate) to match it. The spec
     * engine cannot follow: `SizeSpec` has no logo-derived variant and the layout slots' gaps are
     * static em, so neither the reserved width nor the plate can track the logo's aspect.
     *
     * Closing that needs two spec-engine features — a logo-derived `SizeSpec` and logo-aware slot
     * gaps — which is its own piece of work. Until then this asserts what still holds for a wide
     * logo, which is that both models build the same layers in the same order, and leaves the
     * geometry to the square configs above rather than pretending the numbers agree.
     */
    @Test
    fun portMatchesCompiledStructureWithAWideLogo() {
        val cfg = LottieGenConfig(
            logoEnabled = true,
            logoData = "data:image/png;base64,iVBORw0KGgo=",
            logoW = 120,
            logoH = 80,
            // Same shape as the matrix's logo configs above, so this isolates the aspect and
            // nothing else — a bare default config reaches a combination the matrix never covers.
            bgEnabled = true,
            borderThickness = 4f,
        )
        val expected = LottieGenerator.generate(cfg, Style10DoubleLine())
        val actual = LottieGenerator.generate(cfg, port())

        val expectedLayers = expected["layers"]!!.jsonArray.map { it.jsonObject }
        val actualLayers = actual["layers"]!!.jsonArray.map { it.jsonObject }

        assertEquals(expectedLayers.map { it.name() }, actualLayers.map { it.name() }, "layer names/order")
        for ((exp, act) in expectedLayers.zip(actualLayers)) {
            assertEquals(exp["ty"]!!.jsonPrimitive.int, act["ty"]!!.jsonPrimitive.int, "layer type ${exp.name()}")
            assertEquals(exp["td"]?.jsonPrimitive?.int, act["td"]?.jsonPrimitive?.int, "td ${exp.name()}")
            assertEquals(exp["tt"]?.jsonPrimitive?.int, act["tt"]?.jsonPrimitive?.int, "tt ${exp.name()}")
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
