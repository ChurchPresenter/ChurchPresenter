package org.churchpresenter.presentationengine.pptx

import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.xmlbeans.XmlObject
import org.openxmlformats.schemas.drawingml.x2006.main.CTStretchInfoProperties

private const val STRETCH_PATH =
    "declare namespace a='http://schemas.openxmlformats.org/drawingml/2006/main' .//a:stretch"

/**
 * Gives every bare `<a:stretch/>` an empty `<a:fillRect/>`.
 *
 * The two mean the same thing — a picture fill stretched over the whole shape, PowerPoint writes
 * either — but POI reads the stretch as `getStretch().getFillRect()` and treats a missing fillRect
 * as no stretch at all (`XSLFTexturePaint.getStretch` returns null), so `DrawTexturePaint` tiles
 * the picture at its own pixel size instead. A theme background picture — `<p:bgRef idx="1003">`
 * into `bgFillStyleLst`, which carries a bare stretch in the stock Office themes — then shows as a
 * small corner of the picture rather than the whole of it.
 */
internal fun addMissingFillRects(xml: XmlObject): Int {
    var added = 0
    for (found in xml.selectPath(STRETCH_PATH)) {
        val stretch = found as? CTStretchInfoProperties ?: continue
        if (!stretch.isSetFillRect) {
            stretch.addNewFillRect()
            added++
        }
    }
    return added
}

/**
 * Applies [addMissingFillRects] to every part a slide render reads a picture fill from — the slide,
 * its layout, its master and the master's theme, where the background fill styles live.
 *
 * Best-effort, like [stripUnsupportedHighlights]: a deck that cannot be walked is left as it was.
 */
internal fun addMissingFillRects(show: XMLSlideShow): Int {
    var added = 0
    val seen = mutableSetOf<XmlObject>()
    fun normalize(xml: XmlObject?) {
        if (xml == null || !seen.add(xml)) return
        added += addMissingFillRects(xml)
    }
    try {
        for (slide in show.slides) {
            normalize(slide.xmlObject)
            val layout = slide.slideLayout
            normalize(layout.xmlObject)
            val master = layout.slideMaster
            normalize(master.xmlObject)
            normalize(master.theme?.xmlObject)
        }
    } catch (_: Exception) {
        // A malformed or partially readable deck keeps whatever was already normalized.
    }
    return added
}
