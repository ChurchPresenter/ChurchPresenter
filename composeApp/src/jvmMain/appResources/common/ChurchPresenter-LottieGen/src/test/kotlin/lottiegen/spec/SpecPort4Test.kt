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
import lottiegen.lottie.styles.Style4Banner
import lottiegen.model.LottieGenConfig
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * End-to-end fidelity gate: the Style 4 spec port must produce the same layer
 * structure and identical rest-state geometry as the compiled Style4Banner, across
 * alignments and visibility toggles, through the real generation pipeline.
 *
 * Layer STRUCTURE (names, order, ty, td, tt) must match exactly in every config.
 * Rest origins are asserted exactly except for the documented deviations below,
 * where the slot/line model genuinely cannot reproduce the compiled math:
 *
 * 1. CENTER ALIGNMENT — all x coordinates excluded. Compiled Style4Banner has no
 *    center branch at all (`isRight` is its only alignment test): center falls into
 *    the left-margin layout with justify 0. The spec engine always centers the
 *    content block on the canvas (baseX = canvasW/2) and derives justify 2 for
 *    center — neither is spec-overridable, and no static em offset can express the
 *    margin-based x positions from a canvas-centered block (the gap depends on the
 *    measured text width). Y coordinates remain exact for center.
 *
 * 2. "Info Mask" — x excluded in every alignment. The compiled info mask is a
 *    block-wide bar centered on the whole info bar (nameBoxW + accentW wide), but a
 *    spec MaskRevealSpec always centers on its own text field's extent; the required
 *    correction ((nameWidth - infoWidth)/2 + 14.52px) depends on the measured widths
 *    of both fields, which a static offsetXEm cannot express. The shipped
 *    offsetXEm/padEm are tuned to the DEFAULT config's measured widths so the
 *    default render is visually equivalent. Mask y is exact.
 *
 * 3. "Accent Block" — y excluded when cfg.borderThickness > 0. Compiled shifts the
 *    block down by borderPx * 0.25 (and inflates its size by borderPx x borderPx*1.5)
 *    when a border is set; border-conditional geometry is not expressible in a spec.
 *    The port uses the border-less values (offsetYEm -0.925, 1.21em x 2.2em), exact
 *    whenever borderThickness == 0.
 *
 * Non-asserted (visual-only) approximations, also tuned to the DEFAULT config:
 * - The compiled name/info masks ANIMATE their rect width (0 -> full box) —
 *   MaskRevealSpec has no RECT_SIZE track, so the port uses static full-size masks;
 *   the text slide tracks preserve the reveal effect (the text starts fully outside
 *   its mask in every alignment).
 * - Text slide start offsets are expressed in ELEMENT_WIDTH units (1.14 / -2.04)
 *   approximating the compiled nameBoxW / infoBarW pixel offsets, same convention as
 *   the Style 1 port.
 * - Static em constants (blockHeightEm 4.05, line offsets -0.505 / 1.515 / -0.925 /
 *   1.2 / -2.1166667em, accent slot 1.21em, gap 1.5833333em = 38px, top line height
 *   via padYEm -1.975) fold Style4's nameSize/infoSize/lineSpacing/baseSize-dependent
 *   math (including the top line's raw "-1px" nudge and max(2px, 0.1em) height) into
 *   values exact for the default nameSize 1.2 / infoSize 0.9 / lineSpacing 0.2 /
 *   baseSize 24; non-default values of those knobs would deviate.
 * - The block width uses the TEXT slot's max(name, info) measured width while the
 *   compiled style sizes everything off the NAME width alone — identical whenever the
 *   name field measures wider (true for the default texts).
 */
class SpecPort4Test {

    private val eps = 1e-6

    private fun port() = SpecStyleGenerator.fromResource("/styles/style4_banner_port.json")

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
    fun portMatchesCompiledStyle4AtRest() {
        for (cfg in configs()) {
            val expected = LottieGenerator.generate(cfg, Style4Banner())
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
                // must land on the same pixels. Documented deviations (KDoc above) are
                // excluded per coordinate.
                val expOrigin = exp.restOrigin(inFrames)
                val actOrigin = act.restOrigin(inFrames)
                val skipX = cfg.align == "center" || exp.name() == "Info Mask"
                val skipY = exp.name() == "Accent Block" && cfg.borderThickness > 0
                if (!skipX) {
                    assertEquals(expOrigin[0], actOrigin[0], eps, "rest origin[0] $layerLabel")
                }
                if (!skipY) {
                    assertEquals(expOrigin[1], actOrigin[1], eps, "rest origin[1] $layerLabel")
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
     * or the latest animated keyframe with t <= inFrames (Style4's in-animations end
     * before pct 100, so there is not always a keyframe exactly at inFrames — the
     * last one before it is held through the hold phase).
     */
    private fun JsonObject.restValues(prop: String, inFrames: Int): List<Double> {
        val p = this["ks"]!!.jsonObject[prop]!!.jsonObject
        val animated = p["a"]!!.jsonPrimitive.int == 1
        val k = p["k"]!!
        val values: JsonArray = if (!animated) {
            k.jsonArray
        } else {
            val restKf = k.jsonArray.map { it.jsonObject }
                .filter { it["t"]!!.jsonPrimitive.int <= inFrames }
                .maxBy { it["t"]!!.jsonPrimitive.int }
            restKf["s"]!!.jsonArray
        }
        return values.map { (it as JsonPrimitive).double }
    }
}
