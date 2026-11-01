package org.churchpresenter.presentationengine

import org.apache.poi.hslf.usermodel.HSLFSlideShow
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFTextRun
import org.churchpresenter.presentationengine.pptx.TextEffects
import org.churchpresenter.presentationengine.pptx.textEffectsOf
import org.openxmlformats.schemas.drawingml.x2006.main.CTTextCharacterProperties
import org.openxmlformats.schemas.drawingml.x2006.main.STSchemeColorVal
import java.awt.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TextEffectsReadTest {

    private fun effectsOf(set: (CTTextCharacterProperties) -> Unit): TextEffects? =
        XMLSlideShow().use { ppt ->
            val run = ppt.createSlide().createTextBox().addNewTextParagraph().addNewTextRun()
            run.setText("Words")
            set(run.getRPr(true))
            textEffectsOf(run)
        }

    private fun accent4(): Color = XMLSlideShow().use { ppt ->
        val run: XSLFTextRun = ppt.createSlide().createTextBox().addNewTextParagraph().addNewTextRun()
        run.getRPr(true).addNewEffectLst().addNewGlow().apply {
            rad = 12_700
            addNewSchemeClr().setVal(STSchemeColorVal.ACCENT_4)
        }
        assertNotNull(textEffectsOf(run)?.glow).color
    }

    @Test
    fun `a run with no outline or effects has none`() {
        assertNull(effectsOf { })
        assertNull(effectsOf { it.addNewEffectLst() }, "an empty effect list draws nothing")
    }

    @Test
    fun `a glow is read with its radius in points and its color`() {
        val glow = assertNotNull(effectsOf { it.glow(radiusPt = 10.0, rgb = Color.RED) }?.glow)

        assertEquals(10.0, glow.radiusPt, 0.001)
        assertEquals(Color.RED.rgb, glow.color.rgb)
    }

    @Test
    fun `a glow in a theme color is resolved through the sheet's color map`() {
        val glow = assertNotNull(
            effectsOf { props ->
                props.addNewEffectLst().addNewGlow().apply {
                    rad = 127_000
                    addNewSchemeClr().setVal(STSchemeColorVal.TX_1)
                }
            }?.glow,
        )

        assertEquals(Color.BLACK.rgb, glow.color.rgb, "tx1 maps to dk1, black in the default theme")
    }

    @Test
    fun `a theme color's luminance changes are applied`() {
        val tinted = assertNotNull(
            effectsOf { props ->
                props.addNewEffectLst().addNewGlow().apply {
                    rad = 127_000
                    addNewSchemeClr().apply {
                        setVal(STSchemeColorVal.ACCENT_4)
                        addNewLumMod().setVal(20_000)
                        addNewLumOff().setVal(80_000)
                    }
                }
            }?.glow,
        ).color
        val plain = accent4()

        assertTrue(
            tinted.red + tinted.green + tinted.blue > plain.red + plain.green + plain.blue,
            "20% of the color plus 80% light is paler than the color ($tinted vs $plain)",
        )
    }

    @Test
    fun `a color's transparency is kept`() {
        val glow = assertNotNull(
            effectsOf { props ->
                props.addNewEffectLst().addNewGlow().apply {
                    rad = 127_000
                    addNewSrgbClr().apply {
                        setVal(byteArrayOf(0, 0, 0))
                        addNewAlpha().setVal(50_000)
                    }
                }
            }?.glow,
        )

        assertEquals(128.0, glow.color.alpha.toDouble(), 1.0)
    }

    @Test
    fun `a glow of no size is no glow`() {
        assertNull(effectsOf { it.glow(radiusPt = 0.0, rgb = Color.BLACK) })
    }

    @Test
    fun `an outline is read with its width and color`() {
        val outline = assertNotNull(effectsOf { it.outline(widthPt = 2.5, rgb = Color.BLUE) }?.outline)

        assertEquals(2.5, outline.widthPt, 0.001)
        assertEquals(Color.BLUE.rgb, outline.color.rgb)
    }

    @Test
    fun `an outline that names no width is three quarters of a point`() {
        val outline = assertNotNull(
            effectsOf { it.addNewLn().addNewSolidFill().addNewSrgbClr().setVal(byteArrayOf(0, 0, 0)) }?.outline,
        )

        assertEquals(0.75, outline.widthPt, 0.001)
    }

    @Test
    fun `an outline with no fill, no color or no width is not drawn`() {
        assertNull(effectsOf { it.addNewLn().addNewNoFill() })
        assertNull(effectsOf { it.addNewLn() })
        assertNull(effectsOf { it.outline(widthPt = 0.0, rgb = Color.BLACK) })
    }

    @Test
    fun `a gradient outline takes its first stop's color`() {
        val outline = assertNotNull(
            effectsOf { props ->
                props.addNewLn().addNewGradFill().addNewGsLst().apply {
                    addNewGs().apply {
                        pos = 0
                        addNewSrgbClr().setVal(byteArrayOf(0, -128, 0))
                    }
                    addNewGs().apply {
                        pos = 100_000
                        addNewSrgbClr().setVal(byteArrayOf(0, 0, -1))
                    }
                }
            }?.outline,
        )

        assertEquals(Color(0, 128, 0).rgb, outline.color.rgb)
    }

    @Test
    fun `a shadow's distance and direction become an offset`() {
        val shadow = assertNotNull(
            effectsOf { it.shadow(blurPt = 3.0, distancePt = 10.0, angleDeg = 90, rgb = Color.BLACK) }?.shadow,
        )

        assertEquals(0.0, shadow.offsetXPt, 0.001)
        assertEquals(10.0, shadow.offsetYPt, 0.001, "90 degrees is straight down")
        assertEquals(3.0, shadow.blurPt, 0.001)
    }

    @Test
    fun `a shadow with nothing but a color sits under the text, unblurred`() {
        val shadow = assertNotNull(
            effectsOf { it.addNewEffectLst().addNewOuterShdw().addNewSrgbClr().setVal(byteArrayOf(0, 0, 0)) }?.shadow,
        )

        assertEquals(0.0, shadow.offsetXPt, 0.001)
        assertEquals(0.0, shadow.offsetYPt, 0.001)
        assertEquals(0.0, shadow.blurPt, 0.001)
    }

    @Test
    fun `an effect with no color is not drawn`() {
        assertNull(effectsOf { it.addNewEffectLst().addNewGlow().rad = 127_000 })
        assertNull(effectsOf { it.addNewEffectLst().addNewOuterShdw() })
    }

    @Test
    fun `all three are read off one run`() {
        val effects = assertNotNull(
            effectsOf {
                it.outline(widthPt = 1.0, rgb = Color.BLACK)
                it.glow(radiusPt = 5.0, rgb = Color.BLACK)
                it.shadow(blurPt = 2.0, distancePt = 2.0, angleDeg = 45, rgb = Color.BLACK)
            },
        )

        assertNotNull(effects.outline)
        assertNotNull(effects.glow)
        assertNotNull(effects.shadow)
    }

    @Test
    fun `a run from a ppt file has none`() {
        HSLFSlideShow().use { ppt ->
            val box = ppt.createSlide().createTextBox()
            box.text = "Words"
            assertNull(textEffectsOf(box.textParagraphs.first().textRuns.first()))
        }
    }
}
