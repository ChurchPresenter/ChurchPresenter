package org.churchpresenter.presentationengine.keynote

import org.churchpresenter.presentationengine.keynote.KnFields as F
import java.awt.Color

/** Shape and character styles, fills and colours, resolved through the TSS parent chain. */
internal object KeynoteStyleResolver {

    /** A style/template chain that long is a cycle in the document, not a real inheritance. */
    const val MAX_STYLE_CHAIN = 8

    private val RENDERABLE_IMAGE_EXTENSIONS =
        setOf("jpg", "jpeg", "png", "gif", "tiff", "tif", "bmp")

    class ResolvedShapeStyle(
        val fill: KnFill?,
        val strokeColor: Color?,
        val strokeWidthPt: Double,
        val opacity: Double
    )

    class CharProps(
        val fontName: String?,
        val fontSize: Double?,
        val bold: Boolean?,
        val italic: Boolean?,
        val color: Color?
    )

    /** Whether [fileName]'s extension is one the renderer can draw. */
    fun isRenderableImage(fileName: String): Boolean =
        fileName.substringAfterLast('.', "").lowercase() in RENDERABLE_IMAGE_EXTENSIONS

    /**
     * The style [styleId] names and its TSS parents, nearest first: at most [MAX_STYLE_CHAIN] of
     * them, ending at the first that cannot be read.
     */
    private fun styleChain(index: ObjectIndex, styleId: Long?): Sequence<IwaMessage> =
        generateSequence(styleId?.let { index.message(it) }) { style ->
            style.message(F.STYLE_SUPER)?.message(F.TSS_STYLE_PARENT)?.varint(F.REFERENCE_IDENTIFIER)
                ?.let { index.message(it) }
        }.take(MAX_STYLE_CHAIN)

    /**
     * Resolves fill/stroke/opacity, walking the TSS parent chain for inherited values: each is
     * taken from the nearest style that sets it. The stroke width is the one beside the stroke
     * colour taken, or with no stroke colour anywhere, the farthest stroke's width.
     */
    fun resolveShapeStyle(index: ObjectIndex, styleId: Long?): ResolvedShapeStyle? {
        val props = styleChain(index, styleId).mapNotNull { it.message(F.SHAPE_STYLE_PROPERTIES) }.toList()
        val fill = props.firstNotNullOfOrNull { p -> p.message(F.SHAPE_PROPS_FILL)?.let { parseFill(index, it) } }
        val strokes = props.mapNotNull { it.message(F.SHAPE_PROPS_STROKE) }
        val coloured = strokes.firstOrNull { it.message(F.STROKE_COLOR) != null }
        val strokeColor = coloured?.message(F.STROKE_COLOR)?.let { parseColor(it) }
        val strokeWidth = (coloured ?: strokes.lastOrNull())?.float(F.STROKE_WIDTH)?.toDouble()
        val opacity = props.firstNotNullOfOrNull { it.float(F.SHAPE_PROPS_OPACITY)?.toDouble() }
        if (fill == null && strokeColor == null && opacity == null) return null
        return ResolvedShapeStyle(fill, strokeColor, strokeWidth ?: 1.0, opacity ?: 1.0)
    }

    fun parseFill(index: ObjectIndex, fill: IwaMessage): KnFill? =
        fill.message(F.FILL_COLOR)?.let { KnFill(color = parseColor(it)) }
            ?: fill.message(F.FILL_GRADIENT)?.let { gradientFill(it) }
            ?: fill.message(F.FILL_IMAGE)?.let { imageFill(index, it) }

    /** Approximated as the first stop's solid color (documented degrade). */
    private fun gradientFill(gradient: IwaMessage): KnFill? =
        gradient.messages(F.GRADIENT_STOPS).firstOrNull()
            ?.message(F.GRADIENT_STOP_COLOR)
            ?.let { KnFill(color = parseColor(it)) }

    private fun imageFill(index: ObjectIndex, image: IwaMessage): KnFill? =
        image.message(F.IMAGE_FILL_DATA)?.varint(F.DATA_REFERENCE_IDENTIFIER)
            ?.let { index.dataFileNames[it] }
            ?.takeIf { isRenderableImage(it) }
            ?.let { KnFill(imageFile = it) }

    fun parseColor(color: IwaMessage): Color {
        val r = color.float(F.COLOR_R) ?: 0f
        val g = color.float(F.COLOR_G) ?: 0f
        val b = color.float(F.COLOR_B) ?: 0f
        val a = color.float(F.COLOR_A) ?: 1f
        return Color(r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), b.coerceIn(0f, 1f), a.coerceIn(0f, 1f))
    }

    /**
     * The character properties [styleId] sets or inherits, each from the nearest style naming it;
     * the walk stops once a family, a size and a colour are known. Null when no style in the chain
     * carries character properties at all.
     */
    fun resolveCharProps(index: ObjectIndex, styleId: Long?): CharProps? {
        var fontName: String? = null
        var fontSize: Double? = null
        var bold: Boolean? = null
        var italic: Boolean? = null
        var color: Color? = null
        var sawAny = false
        for (props in styleChain(index, styleId).mapNotNull { it.message(F.CHARACTER_STYLE_PROPERTIES) }) {
            sawAny = true
            if (fontName == null) fontName = props.string(F.CHAR_PROPS_FONT_NAME)
            if (fontSize == null) fontSize = props.float(F.CHAR_PROPS_FONT_SIZE)?.toDouble()
            if (bold == null) bold = props.bool(F.CHAR_PROPS_BOLD)
            if (italic == null) italic = props.bool(F.CHAR_PROPS_ITALIC)
            if (color == null) color = props.message(F.CHAR_PROPS_FONT_COLOR)?.let { parseColor(it) }
            if (fontName != null && fontSize != null && color != null) break
        }
        return if (sawAny) CharProps(fontName, fontSize, bold, italic, color) else null
    }
}
