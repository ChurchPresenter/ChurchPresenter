package org.churchpresenter.presentationengine.pptx

import org.apache.poi.sl.usermodel.TextRun
import org.apache.poi.xslf.usermodel.XSLFColor
import org.apache.poi.xslf.usermodel.XSLFSheet
import org.apache.poi.xslf.usermodel.XSLFTextRun
import org.apache.xmlbeans.XmlObject
import org.openxmlformats.schemas.drawingml.x2006.main.CTLineProperties
import org.openxmlformats.schemas.drawingml.x2006.main.CTTextCharacterProperties
import java.awt.Color
import java.text.AttributedCharacterIterator.Attribute
import kotlin.math.cos
import kotlin.math.sin

/**
 * What PowerPoint draws around a text run's glyphs besides their fill: an outline (`<a:ln>`), a
 * glow and an outer shadow (`<a:effectLst>`), all from the run's own `<a:rPr>`.
 *
 * POI lays text out and fills it, and draws none of these. On a slide over a photograph they are
 * what keeps the words readable, so a deck that leans on them came out illegible.
 */
internal data class TextEffects(
    val outline: TextOutline? = null,
    val glow: TextGlow? = null,
    val shadow: TextShadow? = null,
)

internal data class TextOutline(val color: Color, val widthPt: Double)

internal data class TextGlow(val color: Color, val radiusPt: Double)

internal data class TextShadow(val color: Color, val blurPt: Double, val offsetXPt: Double, val offsetYPt: Double)

/** The attribute a run's [TextEffects] ride on through line breaking to the line that draws them. */
internal object TextEffectsKey : Attribute("churchpresenter.textEffects") {
    override fun readResolve(): Any = TextEffectsKey
}

/** The effects set on [run], or null for a run with none (and for any run not from a `.pptx`). */
internal fun textEffectsOf(run: TextRun): TextEffects? {
    val pptxRun = run as? XSLFTextRun ?: return null
    val props = runCatching { pptxRun.getRPr(false) }.getOrNull() ?: return null
    val sheet = runCatching { pptxRun.paragraph.parentShape.sheet }.getOrNull() ?: return null
    val effects = TextEffects(
        outline = props.outline(sheet),
        glow = props.glow(sheet),
        shadow = props.shadow(sheet),
    )
    return effects.takeIf { it.outline != null || it.glow != null || it.shadow != null }
}

private fun CTTextCharacterProperties.outline(sheet: XSLFSheet): TextOutline? {
    if (!isSetLn) return null
    val line = ln
    val color = line.fillColor(sheet) ?: return null
    val width = if (line.isSetW) line.w / EMU_PER_POINT else DEFAULT_OUTLINE_PT
    return TextOutline(color, width).takeIf { width > 0 }
}

private fun CTLineProperties.fillColor(sheet: XSLFSheet): Color? = when {
    isSetNoFill -> null
    isSetSolidFill -> resolve(solidFill, sheet)
    // A gradient outline is drawn in its first stop's color: near enough to keep the contrast.
    isSetGradFill -> gradFill.gsLst?.gsList?.firstOrNull()?.let { resolve(it, sheet) }
    else -> null
}

private fun CTTextCharacterProperties.glow(sheet: XSLFSheet): TextGlow? {
    val glow = effectLst?.takeIf { isSetEffectLst }?.glow ?: return null
    val color = resolve(glow, sheet) ?: return null
    val radius = glow.rad / EMU_PER_POINT
    return TextGlow(color, radius).takeIf { radius > 0 }
}

private fun CTTextCharacterProperties.shadow(sheet: XSLFSheet): TextShadow? {
    val shadow = effectLst?.takeIf { isSetEffectLst }?.outerShdw ?: return null
    val color = resolve(shadow, sheet) ?: return null
    val distance = if (shadow.isSetDist) shadow.dist / EMU_PER_POINT else 0.0
    // `dir` is clockwise from the positive x axis, in 60000ths of a degree; y grows downwards.
    val angle = Math.toRadians(if (shadow.isSetDir) shadow.dir / ANGLE_UNITS_PER_DEGREE else 0.0)
    val blur = if (shadow.isSetBlurRad) shadow.blurRad / EMU_PER_POINT else 0.0
    return TextShadow(color, blur, distance * cos(angle), distance * sin(angle))
}

/**
 * The color [holder] carries as its child, with the theme and the sheet's color map applied; null
 * when it carries none, which POI would otherwise answer with white.
 */
private fun resolve(holder: XmlObject, sheet: XSLFSheet): Color? {
    if (holder.domNode.firstChild == null) return null
    return runCatching { XSLFColor(holder, sheet.theme, null, sheet).color }.getOrNull()
}

private const val EMU_PER_POINT = 12_700.0
private const val ANGLE_UNITS_PER_DEGREE = 60_000.0

/** PowerPoint's width for an outline that names none: three quarters of a point. */
private const val DEFAULT_OUTLINE_PT = 0.75
