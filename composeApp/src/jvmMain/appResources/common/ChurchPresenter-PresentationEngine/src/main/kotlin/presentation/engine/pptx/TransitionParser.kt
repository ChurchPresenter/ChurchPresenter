package presentation.engine.pptx

import org.apache.poi.xslf.usermodel.XSLFSlide
import org.apache.xmlbeans.XmlCursor
import presentation.engine.model.Direction
import presentation.engine.model.SlideTransitionSpec
import presentation.engine.model.TransitionType

/**
 * Parses `<p:transition>` into a [SlideTransitionSpec]. Unknown transition kinds degrade to
 * fade — the engine-wide rule. Returns null when the slide has no transition (the app then
 * applies its own configured slide animation).
 */
internal object TransitionParser {

    /** PowerPoint 2010+ extension namespace — carries the real millisecond duration (`p14:dur`)
     *  alongside the legacy 3-bucket `spd` attribute, for readers that predate the extension. */
    private const val P14_NS = "http://schemas.microsoft.com/office/powerpoint/2010/main"

    /** The transition's own attributes plus its single content child (the transition-kind
     *  element, e.g. `<p:push dir="l"/>`), extracted as plain strings. Deliberately NOT typed
     *  `CTSlideTransition` — validated against a real deck that POI's typed choice-content
     *  accessors (`isSetPush`/`isSetWipe`/etc.) resolve false even on a correctly-retyped,
     *  standalone-reparsed fragment (an XmlBeans binding limitation with this schema's choice
     *  group when reached via a fragment search rather than direct document navigation) — raw
     *  text extraction sidesteps that entirely and is exactly as reliable for this shape (one
     *  wrapper element, at most one simple self-closing child). */
    private class RawTransition(
        val spd: String?,
        val advTmMs: Long?,
        val p14DurMs: Long?,
        val childName: String?,
        val childDir: String?,
        val childOrient: String?
    )

    fun parse(slide: XSLFSlide): SlideTransitionSpec? {
        return try {
            val raw = findTransition(slide) ?: return null
            build(raw)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * `<p:transition>` is usually a direct child of `<p:sld>`, but PowerPoint 2010+ extension
     * transitions (`p14:flip`/`warp`/`prism`/…) wrap it in `<mc:AlternateContent><mc:Choice>`
     * …richer…`</mc:Choice><mc:Fallback>`…plain…`</mc:Fallback></mc:AlternateContent>` for
     * backward compatibility — `isSetTransition` only sees a direct, unwrapped child and returns
     * false for any AlternateContent-wrapped slide (validated against a real deck: 10 of 11
     * slides, all using p14: extensions, silently lost their transition entirely with no
     * warning). Falls back to a raw XPath search reaching inside the wrapper, preferring
     * whichever candidate carries `p14:dur` (the Choice branch — richer data) over a plain
     * Fallback.
     */
    private fun findTransition(slide: XSLFSlide): RawTransition? {
        if (slide.xmlObject.isSetTransition) {
            val cursor = slide.xmlObject.transition.newCursor()
            return try {
                parseRaw(cursor)
            } finally {
                cursor.close()
            }
        }
        val cursor: XmlCursor = slide.xmlObject.newCursor()
        return try {
            cursor.selectPath("declare namespace p='${AnimationTargetScanner.P_NS}' .//p:transition")
            var fallback: RawTransition? = null
            while (cursor.toNextSelection()) {
                val candidate = parseRaw(cursor) ?: continue
                if (candidate.p14DurMs != null) return candidate
                if (fallback == null) fallback = candidate
            }
            fallback
        } catch (_: Exception) {
            null
        } finally {
            cursor.close()
        }
    }

    /** `cursor` must be positioned at (or on the document containing only) the `<p:transition>`
     *  element. Reads its own attributes plus its single content child's name/direction. */
    private fun parseRaw(cursor: XmlCursor): RawTransition? {
        val probe = cursor.newCursor()
        return try {
            if (probe.isStartdoc) probe.toFirstContentToken()
            if (!probe.isStart) return null
            val spd = probe.getAttributeText(javax.xml.namespace.QName("", "spd"))
            val advTmMs = probe.getAttributeText(javax.xml.namespace.QName("", "advTm"))?.toLongOrNull()
            val p14DurMs = probe.getAttributeText(javax.xml.namespace.QName(P14_NS, "dur"))?.toLongOrNull()
            var childName: String? = null
            var childDir: String? = null
            var childOrient: String? = null
            if (probe.toFirstChild()) {
                childName = probe.name.localPart
                childDir = probe.getAttributeText(javax.xml.namespace.QName("", "dir"))
                childOrient = probe.getAttributeText(javax.xml.namespace.QName("", "orient"))
            }
            RawTransition(spd, advTmMs, p14DurMs, childName, childDir, childOrient)
        } catch (_: Exception) {
            null
        } finally {
            probe.close()
        }
    }

    private fun build(raw: RawTransition): SlideTransitionSpec {
        // p14:dur carries the deck's real, precise duration (PowerPoint always writes both this
        // and the legacy 3-bucket spd for backward compatibility) — prefer it when present, since
        // spd alone quantizes everything to 500/750/1000ms regardless of the deck's actual intent
        // (validated against a real deck: spd="slow" on every transitioned slide, durations
        // actually ranging 1200-2900ms via p14:dur).
        val durationMs = raw.p14DurMs ?: when (raw.spd) {
            "slow" -> 1000L
            "med" -> 750L
            else -> 500L
        }

        fun spec(type: TransitionType, direction: Direction? = null) =
            SlideTransitionSpec(type, durationMs, direction, raw.advTmMs)

        return when (raw.childName) {
            "cut" -> spec(TransitionType.NONE)
            "fade" -> spec(TransitionType.FADE)
            "push" -> spec(TransitionType.PUSH, sideDirection(raw.childDir))
            "wipe" -> spec(TransitionType.WIPE, sideDirection(raw.childDir))
            "strips" -> spec(TransitionType.WIPE, cornerToSide(raw.childDir))
            "blinds" -> spec(TransitionType.WIPE, orientDirection(raw.childDir))
            "comb" -> spec(TransitionType.WIPE, orientDirection(raw.childDir))
            "randomBar" -> spec(TransitionType.FADE)
            "split" -> spec(TransitionType.SPLIT, if (raw.childOrient == "vert") Direction.LEFT else Direction.UP)
            "cover" -> spec(TransitionType.COVER, cornerToSide(raw.childDir))
            "pull" -> spec(TransitionType.COVER, cornerToSide(raw.childDir))
            // checker, circle, diamond, dissolve, newsflash, plus, random, wedge, wheel, zoom,
            // and any p14: extension kind (flip/warp/prism/ripple/…): no faithful compositor
            // implementation yet — fade preserves timing and content.
            else -> spec(TransitionType.FADE)
        }
    }

    /** l/r/u/d — the direction the incoming slide moves. */
    private fun sideDirection(dir: String?): Direction = when (dir) {
        "l" -> Direction.LEFT
        "r" -> Direction.RIGHT
        "u" -> Direction.UP
        "d" -> Direction.DOWN
        else -> Direction.LEFT
    }

    /** Corner directions (lu/ru/ld/rd) collapse to their dominant horizontal side. */
    private fun cornerToSide(dir: String?): Direction = when (dir) {
        null -> Direction.LEFT
        "l", "lu", "ld" -> Direction.LEFT
        "r", "ru", "rd" -> Direction.RIGHT
        "u" -> Direction.UP
        "d" -> Direction.DOWN
        else -> Direction.LEFT
    }

    private fun orientDirection(dir: String?): Direction =
        if (dir == "vert") Direction.RIGHT else Direction.DOWN
}
