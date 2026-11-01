package org.churchpresenter.presentationengine

import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.openxmlformats.schemas.drawingml.x2006.main.CTTextCharacterProperties
import java.awt.Color
import java.awt.Dimension
import java.awt.Rectangle
import java.io.File

private const val EMU_PER_POINT = 12_700.0
private const val ANGLE_UNITS_PER_DEGREE = 60_000

/** One line of [textEffectsPptx]: its words, and what is set on its run. */
class EffectLine(val text: String, val effect: (CTTextCharacterProperties) -> Unit = {})

/** The lines [textEffectsPptx] writes when it is given none: one of each effect, and one without. */
val EVERY_TEXT_EFFECT = listOf(
    EffectLine("No effect"),
    EffectLine("Black glow") { it.glow(radiusPt = 10.0, rgb = Color.BLACK) },
    EffectLine("Black outline") { it.outline(widthPt = 2.0, rgb = Color.BLACK) },
    EffectLine("Drop shadow") { it.shadow(blurPt = 4.0, distancePt = 4.0, angleDeg = 45, rgb = Color.BLACK) },
)

/**
 * A one-slide 16:9 deck of white lines over a light top and an orange bottom -- the backgrounds
 * white text cannot be read on without its effects -- one line per effect, written to [file].
 * Its signature names no POI type, so a module without POI can call it.
 */
fun textEffectsPptx(file: File, fontFamily: String = "Carlito"): File = effectLinesPptx(file, EVERY_TEXT_EFFECT, fontFamily)

/** [textEffectsPptx] with [lines] of its own. */
fun effectLinesPptx(file: File, lines: List<EffectLine>, fontFamily: String = "Carlito"): File {
    XMLSlideShow().use { ppt ->
        ppt.pageSize = Dimension(720, 405)
        val slide = ppt.createSlide()
        listOf(
            Color(0xF2, 0xE6, 0xC8) to Rectangle(0, 0, 720, 203),
            Color(0xE0, 0x8A, 0x2E) to Rectangle(0, 202, 720, 203),
        ).forEach { (color, area) ->
            slide.createAutoShape().apply {
                anchor = area
                fillColor = color
                lineColor = null
            }
        }
        lines.forEachIndexed { index, line ->
            val box = slide.createTextBox()
            box.anchor = Rectangle(40, 20 + index * 92, 640, 80)
            box.clearText()
            val run = box.addNewTextParagraph().addNewTextRun()
            run.setText(line.text)
            run.fontSize = 48.0
            run.fontFamily = fontFamily
            run.isBold = true
            run.setFontColor(Color.WHITE)
            line.effect(run.getRPr(true))
        }
        file.outputStream().use { ppt.write(it) }
    }
    return file
}

/** Sets a `<a:glow>` of [radiusPt] in [rgb] on these run properties. */
fun CTTextCharacterProperties.glow(radiusPt: Double, rgb: Color) {
    val effects = if (isSetEffectLst) effectLst else addNewEffectLst()
    effects.addNewGlow().apply {
        rad = (radiusPt * EMU_PER_POINT).toLong()
        addNewSrgbClr().setVal(rgb.bytes())
    }
}

/** Sets a solid `<a:ln>` of [widthPt] in [rgb] on these run properties. */
fun CTTextCharacterProperties.outline(widthPt: Double, rgb: Color) {
    addNewLn().apply {
        w = (widthPt * EMU_PER_POINT).toInt()
        addNewSolidFill().addNewSrgbClr().setVal(rgb.bytes())
    }
}

/** Sets an `<a:outerShdw>` [distancePt] away at [angleDeg] (clockwise from right), blurred by [blurPt]. */
fun CTTextCharacterProperties.shadow(blurPt: Double, distancePt: Double, angleDeg: Int, rgb: Color) {
    val effects = if (isSetEffectLst) effectLst else addNewEffectLst()
    effects.addNewOuterShdw().apply {
        blurRad = (blurPt * EMU_PER_POINT).toLong()
        dist = (distancePt * EMU_PER_POINT).toLong()
        dir = angleDeg * ANGLE_UNITS_PER_DEGREE
        addNewSrgbClr().setVal(rgb.bytes())
    }
}

private fun Color.bytes() = byteArrayOf(red.toByte(), green.toByte(), blue.toByte())
