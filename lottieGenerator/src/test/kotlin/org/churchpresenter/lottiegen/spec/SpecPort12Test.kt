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
import org.churchpresenter.lottiegen.lottie.TextMeasurer
import org.churchpresenter.lottiegen.lottie.styles.Style12NewsBadge
import org.churchpresenter.lottiegen.model.LottieGenConfig
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * End-to-end fidelity gate: the Style 12 spec port must produce the same layer
 * structure and identical rest-state geometry as the compiled Style12NewsBadge,
 * across alignments and visibility toggles, through the real generation pipeline.
 *
 * Layer structure (names/order/ty/td/tt) matches exactly in every config. Like the
 * Style 11 port, the compiled style's canvas-based band geometry is modeled with a
 * single FIXED content-block slot of 76 em = (canvasW − 2·marginH)/baseSize at the
 * default config, which makes the block center land on the canvas center (= the
 * compiled bandCX) for all three alignments; both band Y offsets (−0.81 em band,
 * +1.66 em ticker from BLOCK_CENTER with blockHeightEm 4.82) cancel marginV and
 * canvasH exactly, so they hold for EVERY config. The alignment-side badge
 * parallelogram uses per-alignment element variants via PlacementOverride.hidden
 * with MirrorMode.NONE and world-side vertex sets, because the compiled badge
 * keeps its absolute slant direction and is NOT a mirror image of itself; the two
 * slash dividers DO mirror exactly ("\" on the left, "/" on the right), so a single
 * FLIP_ON_RIGHT polygon covers both sides. The name text uses side/center variants
 * because its mask width differs per alignment (0.96 of the main area).
 *
 * Rest origins are asserted EXACTLY (1e-6) for every layer and coordinate — the
 * text-width-dependent X deviations (4/5 below) are not skipped but asserted
 * against their closed-form value ±measuredTextWidth/2 computed with the same
 * TextMeasurer the layout engine uses.
 *
 * Documented deviations (spec vs compiled):
 *
 * 1. Band widths: SizeSpec.CanvasWidth is exactly canvasW wide, the compiled bands
 *    are canvasW − 2·marginH (1824 vs 1920 px at defaults) — the port's bands
 *    extend into the horizontal margins. Rest origins are unaffected (both are
 *    canvas-centered for every config).
 * 2. All constants derived from compiled bandW bake (canvasW − 2·marginH)/baseSize
 *    = 76 em at the default canvas/margins/baseSize: the 76 em block slot, badge
 *    vertices ±38/±21.28/∓20.33024 em, slash vertices around ∓21.875 em, name
 *    offset ±9.47488 em (= (0.22·bandW + slashLean + 4·slashW)/2), logo offset
 *    8.36 em (= 0.11·bandW), mask widths 54.7682304/70.8194304/75.6 em, the
 *    37 em info-mask gap constant asserted in deviation 5
 *    (= canvasW/2 − marginH − 1 em), and the info slide 30.24 em
 *    (= 0.4·infoMaskW). They deviate when that ratio changes — cfg-derived canvas
 *    math is inexpressible in the spec model.
 * 3. Text Y offsets bake the compiled fontSize·0.35 baseline term at default font
 *    sizes: name −0.39 em = (bandCY − baseY)/em + 0.35·1.2, info +1.975 em =
 *    (tickerCY − baseY)/em + 0.35·0.9 — deviate when nameSize/infoSize change.
 *    The masks' yOffsetFactor −0.35 cancels the text term dynamically, so the mask
 *    Y anchors are exact for all configs given the text rest Y.
 * 4. Name justify (spec-model gap): the compiled style centers the name in the
 *    main band area with Lottie justify 2 at EVERY alignment; the spec derives
 *    justify from the alignment (0 left / 1 right / 2 center, never
 *    spec-controlled). The port compensates with a −0.5 ELEMENT_WIDTH rest offset
 *    in the name's POSITION_OFFSET track, so the VISIBLE glyph extent
 *    [mainCX − w/2, mainCX + w/2] matches the compiled output exactly (equivalent
 *    justify encodings). Consequences, both asserted exactly here: the "Name"
 *    layer origin differs from compiled by ∓nameW/2 at left/right, and the
 *    "Name Mask" origin (extent-anchored in the spec model, band-anchored in the
 *    compiled style) by ±nameW/2. Exact at center alignment (justify 2 matches;
 *    the center variant's mask offsetXEm 1.11488 reproduces the compiled quirk of
 *    a mask center at canvasW/2 + (slashLean + 4·slashW)/2 even though the badge
 *    is dropped).
 * 5. Info Mask X: extent-anchored-mask gap → the port centers the mask on the info
 *    text's extent (offsetXEm 0), the compiled style band-anchors it at bandCX.
 *    Origin differs by ±infoW/2 ∓ 37 em at left/right and by −37 em at center —
 *    asserted exactly. This is a deliberate improvement over the compiled
 *    band-anchor: an extent-anchored mask with a 37 em offset (the literal
 *    translation) clipped the outer half of the info text at rest at left/right
 *    alignment, because the mask edge landed near the text's extent center. With
 *    offset 0 the 75.6 em mask always covers the whole text at rest; only the
 *    info text is mask-tracked, so nothing else can clip.
 * 6. Name slide-in start: compiled slides 5 em; the side variant's track must use
 *    ELEMENT_WIDTH units (the −0.5 rest offset of deviation 4), so 5 em is baked
 *    as 0.3529 element widths (= 120 px at the default measured "CHURCH
 *    PRESENTER" width of 340 px) — the start offset deviates when the name
 *    text/font changes. Non-rest keyframes only. The center variant slides
 *    exactly −5 em.
 * 7. Slash fill alpha: alphaFactor 0.55 multiplies exactly, compiled ROUNDS
 *    ((nameColorAlpha·0.55).roundToInt(), ±0.5 alpha) — identical at the default
 *    alpha 100 (both 55).
 * 8. Ticker stroke width: compiled uses borderPx·0.7; the port expresses the 0.7
 *    factor as a constant STROKE_WIDTH track over a FromConfig stroke — exact
 *    width AND exact vanish-at-zero gating, encoded as an animated (constant)
 *    stroke instead of a static one (structural only, invisible).
 * 9. Slide-from offsets 12.42 em (band group) / 9.95 em (ticker group) bake the
 *    compiled slideFromY − restY = fixed band ems + 60 px + marginV at defaults
 *    (canvasH cancels); non-rest keyframes only, never visible at rest.
 * 10. The two slash dividers are one polygon plus a Repeater (2 copies at
 *     −0.576 em) instead of two explicit compiled path groups — identical
 *     geometry, different shape-item encoding inside the layer.
 * 11. Center-aligned info text replicates the compiled quirk verbatim: justify 2
 *     anchored at the LEFT margin + 1 em (not the canvas center).
 */
class SpecPort12Test {

    private val eps = 1e-6

    private fun port() = SpecStyleGenerator.fromResource("/styles/style12_news_badge_port.json")

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
    fun portMatchesCompiledStyle12AtRest() {
        for (cfg in configs()) {
            val expected = LottieGenerator.generate(cfg, Style12NewsBadge())
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
                // The name text / mask X carry the exact ±textWidth/2 justify-encoding
                // offsets of deviations 4 and 5 — asserted exactly, not skipped.
                val expOrigin = exp.restOrigin(inFrames)
                val actOrigin = act.restOrigin(inFrames)
                val deltaX = expectedDeltaX(exp.name(), cfg)
                expOrigin.zip(actOrigin).forEachIndexed { i, (e, a) ->
                    val expectedValue = if (i == 0) e + deltaX else e
                    assertEquals(expectedValue, a, eps, "rest origin[$i] $layerLabel (deltaX=$deltaX)")
                }
            }
        }
    }

    /**
     * The exact, closed-form X offset the port's rest origin has versus the compiled
     * layer (deviations 4 and 5): the justify-encoding half-text-width shifts at
     * left/right alignment, plus the info mask's 37 em extent-vs-band anchor gap
     * (all alignments). Zero for every other layer and at center alignment.
     */
    private fun expectedDeltaX(layerName: String, cfg: LottieGenConfig): Double {
        // Same baseSize pre-scaling LottieGenerator applies before styles run.
        val scaledBase = (cfg.baseSize * (cfg.canvasH / 1080.0) * cfg.scaleFactor).roundToInt()
        // Deviation 5: the port's info mask centers on the text extent; compiled
        // band-anchors it 37 em inward of that extent center (flow-signed).
        val flowSign = if (cfg.align == "right") -1.0 else 1.0
        if (layerName == "Info Mask") {
            val justifyHalf = if (cfg.align == "center") 0.0
            else flowSign * measuredWidth(
                cfg.infoText, cfg, cfg.infoSize, cfg.infoWeight, cfg.infoTransform, scaledBase,
            ) / 2.0
            return justifyHalf - flowSign * 37.0 * scaledBase
        }
        if (cfg.align == "center") return 0.0
        val sign = flowSign
        return when (layerName) {
            // Port: justify 0/1 + (−0.5 element widths) rest offset; compiled: justify 2.
            "Name" -> -sign * measuredWidth(
                cfg.nameText, cfg, cfg.nameSize, cfg.nameWeight, cfg.nameTransform, scaledBase,
            ) / 2.0
            // Port mask: anchored to the text extent center; compiled: band-anchored.
            "Name Mask" -> sign * measuredWidth(
                cfg.nameText, cfg, cfg.nameSize, cfg.nameWeight, cfg.nameTransform, scaledBase,
            ) / 2.0
            else -> 0.0
        }
    }

    @Suppress("LongParameterList") // Mirrors the compiled style's own signature, field for field.
    private fun measuredWidth(
        text: String,
        cfg: LottieGenConfig,
        sizeEm: Float,
        weight: Int,
        transform: String,
        scaledBase: Int
    ): Double = TextMeasurer.measure(
        text, cfg.fontFamily, sizeEm * scaledBase, weight, transform
    ).width.toDouble()

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
