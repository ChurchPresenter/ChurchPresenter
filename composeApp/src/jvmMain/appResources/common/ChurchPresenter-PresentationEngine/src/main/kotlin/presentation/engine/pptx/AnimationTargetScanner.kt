package presentation.engine.pptx

import org.apache.poi.xslf.usermodel.XSLFSlide
import org.apache.xmlbeans.XmlCursor
import javax.xml.namespace.QName

/**
 * First-pass scan of a slide's `<p:timing>` tree: which shapes are animation targets, and which
 * of them are built paragraph-by-paragraph. This is all the layer planner needs — full timing
 * semantics (effects, ordering) are parsed separately by the timeline compiler.
 */
internal object AnimationTargetScanner {

    const val P_NS = "http://schemas.openxmlformats.org/presentationml/2006/main"

    data class Targets(
        /** Shape ids (`spid`) that any behavior targets. */
        val shapeIds: Set<Long>,
        /** Shape ids whose targeting uses paragraph ranges (`<p:pRg>`) — by-paragraph builds. */
        val paragraphBuiltShapeIds: Set<Long>
    ) {
        val isEmpty: Boolean get() = shapeIds.isEmpty()

        companion object {
            val EMPTY = Targets(emptySet(), emptySet())
        }
    }

    fun scan(slide: XSLFSlide): Targets {
        val shapeIds = mutableSetOf<Long>()
        val paragraphShapeIds = mutableSetOf<Long>()
        val cursor: XmlCursor = slide.xmlObject.newCursor()
        try {
            cursor.selectPath("declare namespace p='$P_NS' .//p:timing//p:spTgt")
            while (cursor.toNextSelection()) {
                val spid = cursor.getAttributeText(QName("", "spid"))?.toLongOrNull() ?: continue
                shapeIds.add(spid)
                if (hasParagraphRange(cursor)) paragraphShapeIds.add(spid)
            }
        } catch (_: Exception) {
            // A malformed timing tree must never break loading — the slide simply stays static.
            return Targets.EMPTY
        } finally {
            cursor.close()
        }
        return Targets(shapeIds, paragraphShapeIds)
    }

    /** True when the spTgt element the cursor points at contains `<p:txEl><p:pRg …/>`. */
    private fun hasParagraphRange(spTgtCursor: XmlCursor): Boolean {
        val probe = spTgtCursor.newCursor()
        try {
            probe.selectPath("declare namespace p='$P_NS' ./p:txEl/p:pRg")
            return probe.toNextSelection()
        } catch (_: Exception) {
            return false
        } finally {
            probe.close()
        }
    }
}
