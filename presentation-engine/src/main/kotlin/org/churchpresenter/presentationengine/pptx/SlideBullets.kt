package org.churchpresenter.presentationengine.pptx

import org.apache.poi.sl.usermodel.AutoNumberingScheme
import org.apache.poi.sl.usermodel.PaintStyle
import org.apache.poi.sl.usermodel.TextParagraph
import org.apache.poi.sl.usermodel.TextParagraph.BulletStyle
import org.apache.poi.xslf.model.ParagraphPropertyFetcher
import org.apache.poi.xslf.usermodel.XSLFTextParagraph
import java.awt.Color

/**
 * The bullet [paragraph] is drawn with, or null when it has none.
 *
 * POI answers for a paragraph that names a bullet character or a bullet font, and for no other:
 * a numbered paragraph written as `<a:buAutoNum>` alone — what PowerPoint writes for a list
 * numbered in the text's own font — has no bullet style there, so its number was never drawn.
 */
internal fun bulletStyleOf(paragraph: TextParagraph<*, *, *>): BulletStyle? {
    paragraph.bulletStyle?.let { return it }
    val numbered = paragraph as? XSLFTextParagraph ?: return null
    return if (isNumbered(numbered)) NumberedBullet(numbered) else null
}

/**
 * Whether the nearest level that says anything about [paragraph]'s bullet — its own properties,
 * its shape's list style, the placeholder it fills, the master — says it is numbered.
 */
private fun isNumbered(paragraph: XSLFTextParagraph): Boolean {
    val nearest = ParagraphPropertyFetcher<Boolean>(paragraph) { props, value ->
        when {
            props.isSetBuNone || props.isSetBuChar || props.isSetBuBlip -> value.accept(false)
            props.isSetBuAutoNum -> value.accept(true)
        }
    }
    return runCatching { nearest.fetchProperty(paragraph.parentShape) }.getOrNull() == true
}

/** The style of a numbered paragraph, read from the same properties POI's own style reads. */
private class NumberedBullet(private val paragraph: XSLFTextParagraph) : BulletStyle {
    override fun getBulletCharacter(): String? = paragraph.bulletCharacter

    override fun getBulletFont(): String? = paragraph.bulletFont

    override fun getBulletFontSize(): Double? = paragraph.bulletFontSize

    override fun getBulletFontColor(): PaintStyle? = paragraph.bulletFontColor

    override fun setBulletFontColor(color: Color) = paragraph.setBulletFontColor(color)

    override fun setBulletFontColor(color: PaintStyle) = paragraph.setBulletFontColor(color)

    override fun getAutoNumberingScheme(): AutoNumberingScheme? = paragraph.autoNumberingScheme

    override fun getAutoNumberingStartAt(): Int? = paragraph.autoNumberingStartAt
}
