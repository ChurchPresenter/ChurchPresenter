package org.churchpresenter.presentationengine.pptx

import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.xmlbeans.XmlObject
import org.apache.xmlbeans.XmlCursor.TokenType
import javax.xml.namespace.QName

private const val DRAWINGML_NS = "http://schemas.openxmlformats.org/drawingml/2006/main"
private val HIGHLIGHT = QName(DRAWINGML_NS, "highlight")
private val SRGB_CLR = QName(DRAWINGML_NS, "srgbClr")

/**
 * Removes `<a:highlight>` elements that carry any colour form other than `<a:srgbClr>`.
 *
 * POI reads a run's highlight as `getHighlight().getSrgbClr()` and passes the result straight to
 * `DrawPaint.applyColorTransform` without a null check (`XSLFTextRun.fetchHighlightColor`), so a
 * highlight written as a scheme, preset, system or HSL colour — all legal in the schema, and what
 * PowerPoint writes for a theme-coloured text highlight — throws
 * `NullPointerException: … because "rgbCol" is null` from inside `DrawTextParagraph`. That kills
 * the whole slide render, not just the highlight: nothing on the slide is drawn.
 *
 * Dropping the element loses the highlight's background wash on those runs and keeps everything
 * else on the slide. Resolving a scheme colour ourselves would need the theme the fetcher was
 * about to consult, and there is no POI entry point that returns it for a highlight — so the
 * faithful-looking option is not available, while a blank slide is the alternative.
 */
internal fun stripUnsupportedHighlights(xml: XmlObject): Int {
    var removed = 0
    val cursor = xml.newCursor()
    try {
        // Advancing is what ends the walk: `toNextToken` reports NONE past the last token, whereas
        // the cursor's own token type stays ENDDOC for ever once it lands there.
        var token = cursor.currentTokenType()
        while (token != TokenType.NONE) {
            token = if (cursor.isStart && cursor.name == HIGHLIGHT && !hasSrgbColour(cursor.`object`)) {
                // removeXml leaves the cursor on the token that followed the element, which still
                // has to be examined — so read the type here instead of stepping over it.
                cursor.removeXml()
                removed++
                cursor.currentTokenType()
            } else {
                cursor.toNextToken()
            }
        }
    } finally {
        cursor.dispose()
    }
    return removed
}

/** Whether this `<a:highlight>` holds the one colour form POI's highlight reader can handle. */
private fun hasSrgbColour(highlight: XmlObject): Boolean {
    val cursor = highlight.newCursor()
    try {
        if (!cursor.toFirstChild()) return false
        do {
            if (cursor.name == SRGB_CLR) return true
        } while (cursor.toNextSibling())
        return false
    } finally {
        cursor.dispose()
    }
}

/**
 * Applies [stripUnsupportedHighlights] to every part a slide render reads text properties from —
 * the slide itself plus the layout and master it inherits from, since POI's property fetcher walks
 * that chain and can hit the same highlight there.
 *
 * Best-effort: a deck that cannot be walked is left exactly as it was, because failing to sanitize
 * is the state every deck was in before this existed.
 */
internal fun stripUnsupportedHighlights(show: XMLSlideShow): Int {
    var removed = 0
    val seen = mutableSetOf<XmlObject>()
    fun strip(xml: XmlObject?) {
        if (xml == null || !seen.add(xml)) return
        removed += stripUnsupportedHighlights(xml)
    }
    try {
        for (slide in show.slides) {
            strip(slide.xmlObject)
            val layout = slide.slideLayout
            strip(layout.xmlObject)
            strip(layout.slideMaster.xmlObject)
        }
    } catch (_: Exception) {
        // A malformed or partially readable deck keeps whatever was already stripped.
    }
    return removed
}
