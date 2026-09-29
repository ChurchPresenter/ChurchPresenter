package org.churchpresenter.lottiegen

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.churchpresenter.lottiegen.lottie.LottieGenerator
import org.churchpresenter.lottiegen.model.AnimationStyle
import org.churchpresenter.lottiegen.model.LottieAlignment
import org.churchpresenter.lottiegen.model.LottieGenConfig
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Every compiled style (1–12) rendered across the options its generator branches on: alignment,
 * the optional Detail line, the logo, the background and a hidden Name.
 *
 * The spec-based registry styles have their own suites; these twelve are Kotlin generators whose
 * option branches were only ever reached through the default config, so a right-aligned lower third
 * with a logo and a third line was never built by any test.
 */
class CompiledStyleMatrixTest {

    private companion object {
        /** Styles whose generators draw no logo at all. */
        val NO_LOGO = setOf(AnimationStyle.BANNER.id, AnimationStyle.DIAGONAL.id)

        /** A logo as the picker hands it over; the generator embeds it, it never decodes it. */
        const val LOGO_DATA = "data:image/png;base64,iVBORw0KGgo="
        const val LOGO_PX = 64
    }

    private val styles = AnimationStyle.entries.map { it.id }

    private fun render(style: String, configure: LottieGenConfig.() -> LottieGenConfig = { this }): JsonObject =
        LottieGenerator.generate(LottieGenConfig(style = style).configure())

    private fun JsonObject.layerCount() = getValue("layers").jsonArray.size

    private fun JsonObject.assetIds(): List<String> =
        this["assets"]?.jsonArray?.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.content }.orEmpty()

    private fun LottieGenConfig.withDetail() = copy(hideDetail = false, detailText = "Sunday 10:30")

    private fun LottieGenConfig.withLogo() =
        copy(logoEnabled = true, logoData = LOGO_DATA, logoW = LOGO_PX, logoH = LOGO_PX)

    /** One point in the option matrix. */
    private data class Combo(
        val style: String, val align: LottieAlignment, val detail: Boolean, val logo: Boolean, val bg: Boolean,
    ) {
        fun config(): LottieGenConfig {
            var c = LottieGenConfig(style = style, align = align.id, bgEnabled = bg)
            if (detail) c = c.copy(hideDetail = false, detailText = "Sunday 10:30")
            if (logo) c = c.copy(logoEnabled = true, logoData = LOGO_DATA, logoW = LOGO_PX, logoH = LOGO_PX)
            return c
        }
    }

    private val flags = listOf(false, true)

    private val matrix: List<Combo> =
        styles.flatMap { style ->
            LottieAlignment.entries.flatMap { align ->
                flags.flatMap { detail ->
                    flags.flatMap { logo -> flags.map { bg -> Combo(style, align, detail, logo, bg) } }
                }
            }
        }

    @Test
    fun `every style renders every alignment and option combination to finite numbers`() {
        for (combo in matrix) {
            val doc = LottieGenerator.generate(combo.config())
            assertTrue(doc.layerCount() > 0, "$combo rendered no layers")
            val text = doc.toString()
            assertFalse("NaN" in text || "Infinity" in text, "$combo wrote a non-finite number")
        }
    }

    @Test
    fun `showing the detail line adds to every style`() {
        for (style in styles) {
            val without = render(style).layerCount()
            val with = render(style) { withDetail() }.layerCount()
            assertTrue(with > without, "style $style: $with layers with Detail, $without without")
        }
    }

    @Test
    fun `hiding the name takes it out of every style`() {
        for (style in styles) {
            val shown = render(style).layerCount()
            val hidden = render(style) { copy(hideName = true) }.layerCount()
            assertTrue(hidden < shown, "style $style: $hidden layers with Name hidden, $shown shown")
        }
    }

    @Test
    fun `a logo is embedded by every style that draws one, and by no other`() {
        for (style in styles) {
            val plain = render(style).assetIds()
            val withLogo = render(style) { withLogo() }.assetIds()
            if (style in NO_LOGO) {
                assertTrue(withLogo == plain, "style $style has no logo but embedded $withLogo")
            } else {
                assertTrue(withLogo.size > plain.size, "style $style embedded no logo: $withLogo")
            }
        }
    }

    @Test
    fun `a logo enabled with no picture embeds nothing`() {
        for (style in styles) {
            val doc = render(style) { copy(logoEnabled = true, logoData = null) }
            assertTrue(doc.assetIds() == render(style).assetIds(), "style $style embedded a logo it does not have")
        }
    }

    @Test
    fun `right alignment moves every style`() {
        for (style in styles) {
            val left = render(style) { copy(align = LottieAlignment.entries.first().id) }
            val right = render(style) { copy(align = LottieAlignment.entries.last().id) }
            assertNotEquals(left, right, "style $style draws the same whichever side it is on")
        }
    }
}
