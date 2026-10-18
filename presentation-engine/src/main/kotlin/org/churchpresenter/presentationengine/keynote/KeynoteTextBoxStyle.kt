package org.churchpresenter.presentationengine.keynote

import org.churchpresenter.presentationengine.keynote.KnFields as F

/** A text box's own style: Keynote's "Shrink text to fit", vertical alignment and padding. */
internal object KeynoteTextBoxStyle {

    /** How a text box fits and places its text. */
    class TextBoxLayout(val shrinkToFit: Boolean, val verticalAlignment: Int, val padding: KnPadding)

    /**
     * A text box style and its TSS parents, nearest first. A text box's style (TSWP) wraps a
     * shape style (TSD) as its super, so its parent reference is one level below where other
     * styles keep it; both places are tried, the shallow one first.
     */
    private fun textStyleChain(index: ObjectIndex, styleId: Long?): Sequence<IwaMessage> =
        generateSequence(styleId?.let { index.message(it) }) { style ->
            val superStyle = style.message(F.STYLE_SUPER)
            val parent = superStyle?.message(F.TSS_STYLE_PARENT)
                ?: superStyle?.message(F.STYLE_SUPER)?.message(F.TSS_STYLE_PARENT)
            parent?.varint(F.REFERENCE_IDENTIFIER)?.let { index.message(it) }
        }.take(KeynoteStyleResolver.MAX_STYLE_CHAIN)

    /** Shrink-to-fit, vertical alignment and padding, each from the nearest style in the chain that sets it. */
    fun resolveTextBoxLayout(index: ObjectIndex, styleId: Long?): TextBoxLayout {
        val props = textStyleChain(index, styleId).mapNotNull { it.message(F.TSWP_SHAPE_STYLE_PROPERTIES) }.toList()
        val shrink = props.firstNotNullOfOrNull { it.varint(F.TSWP_SHAPE_PROPS_SHRINK) }
        val alignment = props.firstNotNullOfOrNull { it.varint(F.TSWP_SHAPE_PROPS_VALIGN) }
        val padding = props.firstNotNullOfOrNull { it.message(F.TSWP_SHAPE_PROPS_PADDING) }?.let { pad ->
            fun side(field: Int) = pad.float(field)?.toDouble() ?: 0.0
            KnPadding(side(F.PADDING_LEFT), side(F.PADDING_TOP), side(F.PADDING_RIGHT), side(F.PADDING_BOTTOM))
        }
        return TextBoxLayout(
            shrinkToFit = shrink == 1L,
            verticalAlignment = alignment?.toInt() ?: 0,
            padding = padding ?: KnPadding.NONE,
        )
    }
}
