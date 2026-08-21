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
import org.churchpresenter.lottiegen.lottie.styles.Style2Boxed
import org.churchpresenter.lottiegen.model.LottieGenConfig
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * End-to-end fidelity gate: the Style 2 spec port must produce the same layer
 * structure and identical rest-state geometry as the compiled Style2Boxed, across
 * alignments and visibility toggles, through the real generation pipeline.
 *
 * Layer structure (names/order/ty/td/tt) matches exactly in every config. Rest
 * origins are asserted exactly (1e-6) for every layer in this matrix — all of the
 * approximations below collapse to exact values at the DEFAULT text/logo metrics
 * the matrix uses (baseSize 24, nameSize 1.2, infoSize 0.9, lineSpacing 0.2,
 * logoSize 3.5), because the compiled style derives them from those config values
 * while the spec model can only express static em constants.
 *
 * Documented deviations (spec vs compiled, for configs OUTSIDE this matrix):
 *
 * 1. Block height: compiled totalH = nameBoxH + lineSpacing + infoBoxH
 *    = (nameSize + infoSize + lineSpacing + 2·0.5·2) em, which drives baseY.
 *    The spec's blockHeightEm is a constant 4.3 (exact at defaults) — baseY
 *    deviates when nameSize/infoSize/lineSpacing change.
 * 2. Vertical positions: compiled box centers sit at
 *    baseY − lineSpacing/2 − infoBoxH/2 and baseY + nameBoxH/2 + lineSpacing/2,
 *    and text at boxY + fontSize·0.35 — no LineAnchor matches this box-height
 *    math, so the port uses BLOCK_CENTER + static em offsets (−1.05 / −0.63 /
 *    +1.2 / +1.515), exact only at default sizes.
 * 3. Mask heights: compiled masks are box-sized (fontSize + 2·0.5 em). MaskReveal
 *    has no "font size + em pad" height, so heightEm is 2.2 (name) / 1.9 (info) —
 *    exact at default font sizes. Mask WIDTH (padEm 2.6 = 2·1.3 em paddingX) and
 *    mask X/Y centering (yOffsetFactor −0.35 cancels the text's +0.35 offset) are
 *    exact for ALL configs.
 * 4. Logo slot gaps: compiled logoGap = cfg.lineSpacing em and the badge is
 *    logoSize + 1.2 em wide; the slot core is logoSize em, so the 0.8 em gaps
 *    encode gap(0.2) + badge overhang(0.6) at default lineSpacing — deviates when
 *    lineSpacing changes.
 * 5. Center-aligned logo: compiled hangs the badge left of the widest box without
 *    shifting the boxes; the slot engine has no "outside the block" anchor for
 *    center, so the port uses offsetXEm −3.6416666666666666
 *    = −(2.9 + logoSize) + TEXT_CORE_PAD_PX/2/baseSize em, tuned for default
 *    logoSize/lineSpacing/baseSize (the 5px term is the layout engine's fixed
 *    text-core pad, exact only at baseSize 24).
 * 6. Logo BG size: compiled is (logoSize + 1.2) × (logoSize + 0.8) em; SizeSpec
 *    cannot reference cfg.logoSize, so it is a static Em(4.7, 4.3).
 * 7. Logo slide-in start offset: compiled totalH + logoBgH px; spec uses a static
 *    8.6 em (equal at defaults). Non-rest keyframe only — never visible at rest.
 * 8. Text slide-in start offsets: compiled boxW·1.02; spec uses ±1.3 element
 *    (text) widths, enough to fully clear the mask. Non-rest keyframes only.
 * 9. Spec-model gap: the compiled style gates Logo AND Logo BG on
 *    `logoEnabled && logoData != null`; a RectElement can only test LOGO_ENABLED,
 *    so with logoEnabled=true but logoData=null the port would still build
 *    "Logo BG" (compiled builds neither). Not representable and not exercised
 *    here — the app never enables the logo without data.
 *
 * The horizontal geometry is exact in every configuration: per-box widths come
 * from TextWrap sizing and the box centers from constant ELEMENT_WIDTH position
 * offsets (0.5 of the box width from the box-region edge), which track the
 * measured text exactly. Center alignment (whose boxes stay canvas-centered
 * rather than slot-anchored) is handled by per-alignment element variants using
 * PlacementOverride.hidden — exactly one variant builds per alignment, so layer
 * structure is unaffected.
 */
class SpecPort2Test {

    private val eps = 1e-6

    private fun port() = SpecStyleGenerator.fromResource("/styles/style2_boxed_port.json")

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
    fun portMatchesCompiledStyle2AtRest() {
        for (cfg in configs()) {
            val expected = LottieGenerator.generate(cfg, Style2Boxed())
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
                // land on the same pixels at rest.
                val expOrigin = exp.restOrigin(inFrames)
                val actOrigin = act.restOrigin(inFrames)
                expOrigin.zip(actOrigin).forEachIndexed { i, (e, a) ->
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
