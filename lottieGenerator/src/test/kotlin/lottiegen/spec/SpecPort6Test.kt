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
import lottiegen.lottie.styles.Style6LineSplit
import lottiegen.model.LottieGenConfig
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end fidelity gate: the Style 6 spec port must produce the same layer
 * structure as the compiled Style6LineSplit in every config, and identical rest-state
 * geometry wherever the spec model can express it, through the real generation pipeline.
 *
 * Documented deviations (excluded from the exact rest-origin assertions below):
 *
 * 1. Border-dependent vertical stack. Style6 anchors the whole name/line/info stack to
 *    the bottom margin with the line's own thickness inside the formula
 *    (linePx = max(2, borderThickness * baseSize * 0.1)). Spec placements only have
 *    constant em offsets from BLOCK_CENTER, so the Name and Line y offsets are baked
 *    for the default-ish borderThickness = 4 (linePx = 9.6px at baseSize 24) and
 *    deviate when borderThickness differs (the border-0 configs use the 2px floor,
 *    a 7.6px shift on Name, 3.8px on Line). The Info y is border-independent in the
 *    compiled math (the linePx terms cancel) and stays exact in every config.
 *
 * 2. hideInfo collapses the info row's space in Style6, moving Name and Line down by
 *    lineGap + infoSizePx; spec offsets cannot be conditional on hide toggles
 *    (centerSingleLine is a different behavior), so Name/Line y deviate under hideInfo.
 *    hideName does NOT shift the compiled stack (it is bottom-anchored), so only the
 *    line-width deviation below applies there.
 *
 * 3. Line width source. Style6's lineW = max(visible name/info widths + 1.2em, 4em);
 *    the spec expresses it as TextWrap(NAME, padXEm 0.6), exact whenever the name is
 *    the widest visible field and the 4em floor is inactive (true for the default
 *    texts). Under hideName the compiled width comes from the info field instead, so
 *    the Line's x (rest center) deviates in that config. The 4em minimum width is not
 *    expressible at all.
 *
 * 4. Fixed-px blending. The layout engine's TEXT slot core adds a fixed 10px pad and
 *    the spec folds it into em offsets at the default baseSize 24
 *    (offsetXEm 0.39166... = 0.6em - 5px on the Line, -0.89166... = -1.1em + 5px on the
 *    center Logo), so those offsets deviate at other baseSize/canvasH/scaleFactor.
 *    The center-logo offset additionally bakes in the default logoSize 3.5 and the
 *    "name is widest" assumption from (3).
 *
 * 5. Per-character animator offsets. Compiled passes +-0.8 * fontSizePx; the spec's
 *    posOffsetEm is em of baseSize, so 0.96 / -0.72 are exact only for the default
 *    nameSize 1.2 / infoSize 0.9 (animator internals are not numerically asserted here,
 *    only presence).
 *
 * 6. Logo scale basis (spec-model gap). The spec engine scales the logo by
 *    max(logoW, logoH); Style6 scales by logoH only, so a non-square logo renders at a
 *    different visual size (rest position and anchor still match exactly, which is what
 *    is asserted). There is no height-based scale option in the spec model.
 *
 * 7. Line height. The rect's resolved height (TextWrap: nameSizePx - 0.8em = 9.6px at
 *    defaults) equals linePx only at borderThickness 4; border-0 configs compiled draw a
 *    2px line. Height is not part of the rest-origin assertions.
 *
 * Rest origins are read as "the value held at the end of the in phase": the static k,
 * or the animated keyframe with the largest t <= inFrames — the spec engine's
 * ALIGN_EDGE grow synthesizes position keyframes that END before inFrames (pct 40)
 * and hold, where the compiled Line uses a static position, so requiring t == inFrames
 * exactly would miss equivalent encodings.
 */
class SpecPort6Test {

    private val eps = 1e-6

    private fun port() = SpecStyleGenerator.fromResource("/styles/style6_line_split_port.json")

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

    /**
     * Axes (0 = x, 1 = y) excluded from the exact rest-origin comparison for a layer,
     * per the deviations documented in the class KDoc.
     */
    private fun excludedAxes(cfg: LottieGenConfig, layerName: String): Set<Int> {
        val axes = mutableSetOf<Int>()
        // Vertical offsets are baked for linePx at borderThickness 4 (deviations 1, 2).
        val lineYExact = cfg.borderThickness == 4f && !cfg.hideInfo
        when (layerName) {
            "Name" -> if (!lineYExact) axes.add(1)
            "Line" -> {
                if (!lineYExact) axes.add(1)
                // Line width comes from the info field when the name is hidden (deviation 3).
                if (cfg.hideName) axes.add(0)
            }
        }
        return axes
    }

    @Test
    fun portMatchesCompiledStyle6AtRest() {
        for (cfg in configs()) {
            val expected = LottieGenerator.generate(cfg, Style6LineSplit())
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

                // Text layers that carry per-character animators in the compiled style
                // must carry them in the port too.
                if (exp["ty"]!!.jsonPrimitive.int == 5) {
                    val expAnimators = exp["t"]!!.jsonObject["a"]!!.jsonArray
                    val actAnimators = act["t"]!!.jsonObject["a"]!!.jsonArray
                    if (expAnimators.isNotEmpty()) {
                        assertTrue(actAnimators.isNotEmpty(), "text animators missing $layerLabel")
                    }
                    assertEquals(
                        expAnimators.isEmpty(), actAnimators.isEmpty(),
                        "text animator presence $layerLabel"
                    )
                }

                // Compare the shape-origin screen position (position minus anchor):
                // equivalent encodings (edge anchor + static position vs. zero anchor +
                // edge-grow position keyframes) must land on the same pixels.
                val excluded = excludedAxes(cfg, exp.name())
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

    private fun JsonObject.name(): String = this["nm"]!!.jsonPrimitive.content

    /** Screen position of the layer's local origin at rest: position minus anchor (x, y). */
    private fun JsonObject.restOrigin(inFrames: Int): List<Double> {
        val p = restValues("p", inFrames)
        val a = restValues("a", inFrames)
        return listOf(p[0] - a[0], p[1] - a[1])
    }

    /**
     * The value a transform property holds at the end of the in phase: the static k,
     * or the animated keyframe with the largest t <= inFrames (in-phase keyframes may
     * end before inFrames and hold — see the class KDoc).
     */
    private fun JsonObject.restValues(prop: String, inFrames: Int): List<Double> {
        val p = this["ks"]!!.jsonObject[prop]!!.jsonObject
        val animated = p["a"]!!.jsonPrimitive.int == 1
        val k = p["k"]!!
        val values: JsonArray = if (!animated) {
            k.jsonArray
        } else {
            k.jsonArray.map { it.jsonObject }
                .filter { it["t"]!!.jsonPrimitive.int <= inFrames }
                .maxBy { it["t"]!!.jsonPrimitive.int }["s"]!!.jsonArray
        }
        return values.map { (it as JsonPrimitive).double }
    }
}
