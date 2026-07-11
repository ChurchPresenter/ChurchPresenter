package presentation.engine.pptx

import org.apache.poi.xslf.usermodel.XSLFSlide
import org.openxmlformats.schemas.presentationml.x2006.main.CTSlideTransition
import presentation.engine.model.Direction
import presentation.engine.model.SlideTransitionSpec
import presentation.engine.model.TransitionType

/**
 * Parses `<p:transition>` into a [SlideTransitionSpec]. Unknown transition kinds degrade to
 * fade — the engine-wide rule. Returns null when the slide has no transition (the app then
 * applies its own configured slide animation).
 */
internal object TransitionParser {

    fun parse(slide: XSLFSlide): SlideTransitionSpec? {
        return try {
            val ct = slide.xmlObject.takeIf { it.isSetTransition }?.transition ?: return null
            build(ct)
        } catch (_: Exception) {
            null
        }
    }

    private fun build(ct: CTSlideTransition): SlideTransitionSpec {
        val durationMs = when (ct.spd?.toString()) {
            "slow" -> 1000L
            "med" -> 750L
            else -> 500L
        }
        val advanceAfterMs = if (ct.isSetAdvTm) ct.advTm else null

        fun spec(type: TransitionType, direction: Direction? = null) =
            SlideTransitionSpec(type, durationMs, direction, advanceAfterMs)

        return when {
            ct.isSetCut -> spec(TransitionType.NONE)
            ct.isSetFade -> spec(TransitionType.FADE)
            ct.isSetPush -> spec(TransitionType.PUSH, sideDirection(ct.push?.dir?.toString()))
            ct.isSetWipe -> spec(TransitionType.WIPE, sideDirection(ct.wipe?.dir?.toString()))
            ct.isSetStrips -> spec(TransitionType.WIPE, cornerToSide(ct.strips?.dir?.toString()))
            ct.isSetBlinds -> spec(TransitionType.WIPE, orientDirection(ct.blinds?.dir?.toString()))
            ct.isSetComb -> spec(TransitionType.WIPE, orientDirection(ct.comb?.dir?.toString()))
            ct.isSetRandomBar -> spec(TransitionType.FADE)
            ct.isSetSplit -> spec(
                TransitionType.SPLIT,
                if (ct.split?.orient?.toString() == "vert") Direction.LEFT else Direction.UP
            )
            ct.isSetCover -> spec(TransitionType.COVER, cornerToSide(ct.cover?.dir))
            ct.isSetPull -> spec(TransitionType.COVER, cornerToSide(ct.pull?.dir))
            // checker, circle, diamond, dissolve, newsflash, plus, random, wedge, wheel, zoom:
            // no faithful compositor implementation yet — fade preserves timing and content.
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
