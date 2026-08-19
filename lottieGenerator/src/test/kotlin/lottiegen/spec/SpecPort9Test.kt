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
import lottiegen.lottie.TextMeasurer
import lottiegen.lottie.styles.Style9DiagonalWipe
import lottiegen.model.LottieGenConfig
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end fidelity gate: the Style 9 spec port must produce the same layer
 * structure as the compiled Style9DiagonalWipe in every config, and equivalent
 * rest-state geometry, through the real generation pipeline.
 *
 * Style 9 hand-writes its keyframes: static two-tone text (an accent copy over the
 * normal copy) is revealed by two moving parallelogram masks — the "wipe" mask sweeps
 * across and back (accent flash), the "reveal" mask sweeps across and stays (normal
 * text), with a diagonal accent line riding the leading edge. The port reproduces
 * this with dual TextElements (colorRole ACCENT + normal) whose maskReveal carries a
 * skewXEm parallelogram and a POSITION_OFFSET track on the mask itself.
 *
 * Documented deviations (spec vs compiled):
 *
 * 1. Layer naming (spec-model gap). maskReveal always emits "<element name> Mask", so
 *    to line the mask layers up with the compiled "Name Reveal Mask"/"Info Reveal Mask"
 *    the normal text elements are named "Name Reveal"/"Info Reveal" — their TEXT layers
 *    therefore differ from the compiled "Name"/"Info". [mappedName] applies exactly
 *    this one rename when comparing; every other layer name matches verbatim.
 * 2. Mask geometry. Compiled builds ONE oversized block-height parallelogram per mask
 *    (width = max(name, info) width + logoSpace + cfg-derived margins, height
 *    totalH + 2em, slant 1em over that full height) swept between hand-computed px
 *    offsets (offStart/offEnd) shared by both text lines. The spec mask is per text
 *    line (own width + 2em pad, 1.5 x font size tall) swept in mask-width multiples.
 *    The mask rest COORDINATES are therefore not expressible; what the viewer sees at
 *    rest is identical and is asserted functionally instead: every reveal mask fully
 *    covers its text's horizontal extent at rest, every accent (wipe) mask is fully
 *    clear of it — asserted for the compiled output and the port alike.
 * 3. Diagonal edge continuity. With per-line masks the diagonal edge is per line
 *    rather than one continuous block-height edge, and each line's sweep is
 *    proportional to its own mask width (compiled: both lines share one edge at the
 *    same x). skewXEm 0.2093/0.157 matches the compiled edge slope (1em over
 *    totalH + 2em = 4.3em) at default font sizes.
 * 4. Diagonal line element. The compiled line rides the mask's leading edge
 *    (text-width-dependent px offsets, length totalH + 2em, thickness
 *    max(2, 0.08 x baseSize)). Spec tracks are fixed em: sweep +-10em around the text
 *    slot / block center, length 4.3em, thickness Em(2px/24) — exact at the default
 *    text metrics only. At rest the line is invisible in both (opacity 0 — asserted);
 *    its rest position is a hand-written cfg-derived constant and is not compared.
 * 5. hideInfo. Compiled bottom-anchors the stack, so hiding the info line drops the
 *    name by (lineSpacing + infoSize) em; spec vertical offsets are static
 *    (centerSingleLine is a different behavior). Asserted as that exact delta.
 *    hideName does not move the compiled info line (bottom-anchored) — exact there.
 * 6. Baked constants. blockHeightEm 2.3 (= nameSize + lineSpacing + infoSize),
 *    offsetYEm -0.13/1.015 (text) and 0.075 (logo), and the center-logo override
 *    -2.0416666666666665 em (compiled hangs the logo left of the text block without
 *    shifting the text; folds in logoMargin 1em + logoSize/2 + the layout engine's
 *    fixed 10px text-core pad) are exact at the default sizes/logoSize/baseSize 24.
 * 7. Accent Info opacity quirk. Compiled paints Accent Info with the accent color but
 *    infoColorAlpha as layer opacity; the spec's colorRole ACCENT uses
 *    accentColorAlpha for both. Identical at the default alphas (both 100).
 * 8. Out-phase timing. Compiled hand-writes its out keyframes with mixed per-keyframe
 *    easing; the spec always mirrors the in-phase through the shared builder. The
 *    mirrored shapes happen to match the compiled ones structurally; residual
 *    differences are easing curves and the line-opacity hold (compiled 95%, mirrored
 *    92%). Rest state is unaffected.
 * 9. Logo scale basis (spec-model gap, same as SpecPort2/6). Spec scales the logo by
 *    max(logoW, logoH); compiled by logoH only. Rest origin (asserted) still matches;
 *    a non-square logo renders at a different visual size.
 */
class SpecPort9Test {

    private val eps = 1e-6

    private fun port() = SpecStyleGenerator.fromResource("/styles/style9_diagonal_wipe_port.json")

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

    /** Deviation 1: the only permitted layer rename, compiled name -> port name. */
    private fun mappedName(compiledName: String): String = when (compiledName) {
        "Name" -> "Name Reveal"
        "Info" -> "Info Reveal"
        else -> compiledName
    }

    @Test
    fun portMatchesCompiledStyle9AtRest() {
        for (cfg in configs()) {
            val expected = LottieGenerator.generate(cfg, Style9DiagonalWipe())
            val actual = LottieGenerator.generate(cfg, port())
            val label = "align=${cfg.align} logo=${cfg.logoEnabled} bg=${cfg.bgEnabled} " +
                "hideName=${cfg.hideName} hideInfo=${cfg.hideInfo} border=${cfg.borderThickness}"

            val expectedLayers = expected["layers"]!!.jsonArray.map { it.jsonObject }
            val actualLayers = actual["layers"]!!.jsonArray.map { it.jsonObject }

            assertEquals(
                expectedLayers.map { mappedName(it.name()) }, actualLayers.map { it.name() },
                "layer names/order [$label]"
            )

            val inFrames = (cfg.animDuration * 60).roundToInt()
            val scaledBase = (cfg.baseSize * (cfg.canvasH / 1080.0) * cfg.scaleFactor)
                .roundToInt().toDouble()

            for (i in expectedLayers.indices) {
                val exp = expectedLayers[i]
                val act = actualLayers[i]
                val layerLabel = "${exp.name()} [$label]"
                assertEquals(exp["ty"]!!.jsonPrimitive.int, act["ty"]!!.jsonPrimitive.int, "layer type $layerLabel")
                assertEquals(exp["td"]?.jsonPrimitive?.int, act["td"]?.jsonPrimitive?.int, "td $layerLabel")
                assertEquals(exp["tt"]?.jsonPrimitive?.int, act["tt"]?.jsonPrimitive?.int, "tt $layerLabel")

                when {
                    // Deviation 4: the line's rest position is a hand-written cfg-derived
                    // constant; what matters at rest is that it is invisible in both.
                    exp.name() == "Diagonal Line" -> {
                        assertEquals(0.0, exp.restValues("o", inFrames)[0], eps, "compiled line rest opacity [$label]")
                        assertEquals(0.0, act.restValues("o", inFrames)[0], eps, "port line rest opacity [$label]")
                    }

                    // Deviation 2: mask rest coordinates are not expressible — assert the
                    // functional rest state (reveal covers its text, wipe is clear of it)
                    // on the compiled output and the port alike.
                    exp["td"]?.jsonPrimitive?.int == 1 -> {
                        val expectClear = exp.name().startsWith("Accent")
                        assertMaskRestState(
                            expectedLayers[i], expectedLayers[i + 1], cfg, inFrames, scaledBase,
                            expectClear, "compiled $layerLabel"
                        )
                        assertMaskRestState(
                            actualLayers[i], actualLayers[i + 1], cfg, inFrames, scaledBase,
                            expectClear, "port $layerLabel"
                        )
                    }

                    // Text and logo layers: exact rest origins (deviation 5's exact
                    // hideInfo delta on the name line's y).
                    else -> {
                        val expOrigin = exp.restOrigin(inFrames)
                        val actOrigin = act.restOrigin(inFrames)
                        val nameLineLayer = exp.name() == "Accent Name" || exp.name() == "Name"
                        expOrigin.zip(actOrigin).forEachIndexed { axis, (e, a) ->
                            if (axis == 1 && nameLineLayer && cfg.hideInfo) {
                                val delta = (cfg.lineSpacing + cfg.infoSize) * scaledBase
                                assertEquals(e, a + delta, eps, "rest origin[y] hideInfo delta $layerLabel")
                            } else {
                                assertEquals(e, a, eps, "rest origin[$axis] $layerLabel")
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Asserts the functional rest state of a wipe/reveal mask against the horizontal
     * extent of the text layer it mattes: fully clear of it (accent wipe masks) or
     * fully covering it (reveal masks).
     */
    private fun assertMaskRestState(
        mask: JsonObject,
        textLayer: JsonObject,
        cfg: LottieGenConfig,
        inFrames: Int,
        scaledBase: Double,
        expectClear: Boolean,
        label: String
    ) {
        val maskX = mask.restValues("p", inFrames)[0]
        val vertexXs = mask["shapes"]!!.jsonArray[0].jsonObject["it"]!!.jsonArray[0].jsonObject
            .get("ks")!!.jsonObject["k"]!!.jsonObject["v"]!!.jsonArray
            .map { (it.jsonArray[0] as JsonPrimitive).double }
        val maskMin = maskX + vertexXs.min()
        val maskMax = maskX + vertexXs.max()

        val isInfo = textLayer.name().contains("Info")
        val sizePx = (if (isInfo) cfg.infoSize else cfg.nameSize) * scaledBase
        val measured = TextMeasurer.measure(
            if (isInfo) cfg.infoText else cfg.nameText,
            cfg.fontFamily,
            sizePx.toFloat(),
            if (isInfo) cfg.infoWeight else cfg.nameWeight,
            if (isInfo) cfg.infoTransform else cfg.nameTransform
        )
        val textW = measured.width.toDouble()
        val textX = textLayer.restValues("p", inFrames)[0]
        val justify = textLayer["t"]!!.jsonObject["d"]!!.jsonObject["k"]!!.jsonArray[0]
            .jsonObject["s"]!!.jsonObject["j"]!!.jsonPrimitive.int
        val textMin = when (justify) {
            1 -> textX - textW
            2 -> textX - textW / 2
            else -> textX
        }
        val textMax = textMin + textW

        if (expectClear) {
            assertTrue(
                maskMax < textMin || maskMin > textMax,
                "$label: wipe mask must be clear of its text at rest " +
                    "(mask [$maskMin, $maskMax] vs text [$textMin, $textMax])"
            )
        } else {
            assertTrue(
                maskMin <= textMin + eps && maskMax >= textMax - eps,
                "$label: reveal mask must cover its text at rest " +
                    "(mask [$maskMin, $maskMax] vs text [$textMin, $textMax])"
            )
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
     * or the animated keyframe with the largest t <= inFrames.
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
