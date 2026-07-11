package presentation.engine.pptx

import org.apache.poi.xslf.usermodel.XSLFSlide
import org.apache.xmlbeans.XmlObject
import org.openxmlformats.schemas.presentationml.x2006.main.CTTLAnimateBehavior
import org.openxmlformats.schemas.presentationml.x2006.main.CTTLAnimateEffectBehavior
import org.openxmlformats.schemas.presentationml.x2006.main.CTTLAnimateMotionBehavior
import org.openxmlformats.schemas.presentationml.x2006.main.CTTLAnimateRotationBehavior
import org.openxmlformats.schemas.presentationml.x2006.main.CTTLAnimateScaleBehavior
import org.openxmlformats.schemas.presentationml.x2006.main.CTTLCommonBehaviorData
import org.openxmlformats.schemas.presentationml.x2006.main.CTTLCommonTimeNodeData
import org.openxmlformats.schemas.presentationml.x2006.main.CTTLSetBehavior
import org.openxmlformats.schemas.presentationml.x2006.main.CTTLTimeCondition
import org.openxmlformats.schemas.presentationml.x2006.main.CTTLTimeNodeExclusive
import org.openxmlformats.schemas.presentationml.x2006.main.CTTLTimeNodeParallel
import org.openxmlformats.schemas.presentationml.x2006.main.CTTLTimeNodeSequence
import org.openxmlformats.schemas.presentationml.x2006.main.CTTLTimeTargetElement
import org.openxmlformats.schemas.presentationml.x2006.main.CTTimeNodeList

/**
 * Parses a slide's `<p:timing>` tree into [TimeNode]s, preserving document order (which encodes
 * with/after-previous chains — XmlBeans' per-type list getters would lose it, so children are
 * walked with a cursor and dispatched by element name).
 *
 * Returns null when the slide has no timing tree. Never throws — a malformed tree returns null
 * (the slide stays static).
 */
internal object TimingParser {

    fun parse(slide: XSLFSlide): TimeNode? {
        return try {
            val timing = slide.xmlObject.takeIf { it.isSetTiming }?.timing ?: return null
            val tnLst = timing.tnLst ?: return null
            childTimeNodes(tnLst).firstOrNull()
        } catch (_: Exception) {
            null
        }
    }

    /** Ordered walk of a childTnLst, dispatching each element to its typed parser. */
    private fun childTimeNodes(list: CTTimeNodeList): List<TimeNode> {
        val nodes = mutableListOf<TimeNode>()
        val cursor = list.newCursor()
        try {
            if (!cursor.toFirstChild()) return emptyList()
            do {
                val name = cursor.name?.localPart ?: continue
                val obj = cursor.`object`
                parseElement(name, obj)?.let { nodes.add(it) }
            } while (cursor.toNextSibling())
        } finally {
            cursor.close()
        }
        return nodes
    }

    private fun parseElement(name: String, obj: XmlObject): TimeNode? {
        return when (name) {
            "par" -> containerNode((obj as CTTLTimeNodeParallel).cTn, TimeNodeKind.PAR)
            "seq" -> containerNode((obj as CTTLTimeNodeSequence).cTn, TimeNodeKind.SEQ)
            "excl" -> containerNode((obj as CTTLTimeNodeExclusive).cTn, TimeNodeKind.EXCL)
            "set" -> behaviorNode((obj as CTTLSetBehavior).cBhvr) { cBhvr, dur, delay ->
                TimingBehavior.SetValue(
                    target = target(cBhvr.tgtEl),
                    durMs = dur, delayMs = delay,
                    attribute = firstAttrName(cBhvr),
                    toValue = obj.to?.strVal?.`val`
                )
            }
            "animEffect" -> behaviorNode((obj as CTTLAnimateEffectBehavior).cBhvr) { cBhvr, dur, delay ->
                TimingBehavior.AnimEffect(
                    target = target(cBhvr.tgtEl),
                    durMs = dur, delayMs = delay,
                    transition = obj.transition?.toString() ?: "in",
                    filter = if (obj.isSetFilter) obj.filter else null
                )
            }
            "anim" -> behaviorNode((obj as CTTLAnimateBehavior).cBhvr) { cBhvr, dur, delay ->
                TimingBehavior.AnimateValue(
                    target = target(cBhvr.tgtEl),
                    durMs = dur, delayMs = delay,
                    attribute = firstAttrName(cBhvr),
                    from = if (obj.isSetFrom) obj.from else null,
                    to = if (obj.isSetTo) obj.to else null,
                    by = if (obj.isSetBy) obj.by else null,
                    keyframes = parseKeyframes(obj)
                )
            }
            "animMotion" -> behaviorNode((obj as CTTLAnimateMotionBehavior).cBhvr) { cBhvr, dur, delay ->
                TimingBehavior.AnimateMotion(
                    target = target(cBhvr.tgtEl),
                    durMs = dur, delayMs = delay,
                    path = if (obj.isSetPath) obj.path else null
                )
            }
            "animRot" -> behaviorNode((obj as CTTLAnimateRotationBehavior).cBhvr) { cBhvr, dur, delay ->
                TimingBehavior.AnimateRotation(
                    target = target(cBhvr.tgtEl),
                    durMs = dur, delayMs = delay,
                    fromDeg = if (obj.isSetFrom) obj.from / 60000.0 else null,
                    toDeg = if (obj.isSetTo) obj.to / 60000.0 else null,
                    byDeg = if (obj.isSetBy) obj.by / 60000.0 else null
                )
            }
            "animScale" -> behaviorNode((obj as CTTLAnimateScaleBehavior).cBhvr) { cBhvr, dur, delay ->
                TimingBehavior.AnimateScale(
                    target = target(cBhvr.tgtEl),
                    durMs = dur, delayMs = delay,
                    fromX = if (obj.isSetFrom) parsePercentFactor(obj.from.x) else null,
                    fromY = if (obj.isSetFrom) parsePercentFactor(obj.from.y) else null,
                    toX = if (obj.isSetTo) parsePercentFactor(obj.to.x) else null,
                    toY = if (obj.isSetTo) parsePercentFactor(obj.to.y) else null,
                    byX = if (obj.isSetBy) parsePercentFactor(obj.by.x) else null,
                    byY = if (obj.isSetBy) parsePercentFactor(obj.by.y) else null
                )
            }
            // audio, video, cmd — media/verb behaviors, not visual: skipped.
            else -> null
        }
    }

    private fun containerNode(cTn: CTTLCommonTimeNodeData, kind: TimeNodeKind): TimeNode {
        return TimeNode(
            id = if (cTn.isSetId) cTn.id else 0L,
            kind = kind,
            nodeType = if (cTn.isSetNodeType) cTn.nodeType.toString() else null,
            beginConditions = conditions(cTn),
            durMs = if (cTn.isSetDur) parseTlTime(cTn.dur) else null,
            repeatCount = if (cTn.isSetRepeatCount) parseRepeat(cTn.repeatCount) else null,
            autoReverse = cTn.isSetAutoRev && cTn.autoRev,
            fill = if (cTn.isSetFill) cTn.fill.toString() else null,
            restart = if (cTn.isSetRestart) cTn.restart.toString() else null,
            presetId = if (cTn.isSetPresetID) cTn.presetID else null,
            presetClass = if (cTn.isSetPresetClass) cTn.presetClass.toString() else null,
            presetSubtype = if (cTn.isSetPresetSubtype) cTn.presetSubtype else null,
            iterateType = if (cTn.isSetIterate && cTn.iterate.isSetType) cTn.iterate.type.toString() else null,
            children = if (cTn.isSetChildTnLst) childTimeNodes(cTn.childTnLst) else emptyList(),
            behavior = null
        )
    }

    private inline fun behaviorNode(
        cBhvr: CTTLCommonBehaviorData,
        build: (CTTLCommonBehaviorData, Long?, Long) -> TimingBehavior
    ): TimeNode {
        val cTn = cBhvr.cTn
        val dur = if (cTn.isSetDur) parseTlTime(cTn.dur) else null
        val delay = conditions(cTn).firstOrNull()?.delayMs?.takeIf { it >= 0 } ?: 0L
        return TimeNode(
            id = if (cTn.isSetId) cTn.id else 0L,
            kind = TimeNodeKind.BEHAVIOR,
            nodeType = null,
            beginConditions = conditions(cTn),
            durMs = dur,
            repeatCount = if (cTn.isSetRepeatCount) parseRepeat(cTn.repeatCount) else null,
            autoReverse = cTn.isSetAutoRev && cTn.autoRev,
            fill = if (cTn.isSetFill) cTn.fill.toString() else null,
            restart = if (cTn.isSetRestart) cTn.restart.toString() else null,
            presetId = null, presetClass = null, presetSubtype = null,
            iterateType = null,
            children = emptyList(),
            behavior = build(cBhvr, dur, delay)
        )
    }

    private fun conditions(cTn: CTTLCommonTimeNodeData): List<TimeCondition> {
        if (!cTn.isSetStCondLst) return emptyList()
        return cTn.stCondLst.condList.map { cond -> condition(cond) }
    }

    private fun condition(cond: CTTLTimeCondition): TimeCondition {
        return TimeCondition(
            delayMs = if (cond.isSetDelay) parseTlTime(cond.delay) else null,
            event = if (cond.isSetEvt) cond.evt.toString() else null,
            triggerShapeId = cond.tgtEl?.spTgt?.spid,
            triggerNodeId = if (cond.isSetTn) cond.tn.`val` else null
        )
    }

    private fun target(tgtEl: CTTLTimeTargetElement?): BehaviorTarget? {
        val spTgt = tgtEl?.spTgt ?: return null
        val txEl = if (spTgt.isSetTxEl) spTgt.txEl else null
        val pRg = txEl?.takeIf { it.isSetPRg }?.pRg
        return if (pRg != null) {
            val st = pRg.st.toInt()
            val end = pRg.end.toInt()
            BehaviorTarget(
                shapeId = spTgt.spid,
                paragraphIndex = st,
                widensToShape = end != st
            )
        } else {
            BehaviorTarget(shapeId = spTgt.spid, paragraphIndex = null)
        }
    }

    private fun firstAttrName(cBhvr: CTTLCommonBehaviorData): String? =
        cBhvr.attrNameLst?.attrNameList?.firstOrNull()

    private fun parseKeyframes(anim: CTTLAnimateBehavior): List<Pair<Double, String>> {
        if (!anim.isSetTavLst) return emptyList()
        return anim.tavLst.tavList.mapNotNull { tav ->
            val time = tav.tm?.toString()?.toDoubleOrNull()?.div(100000.0) ?: return@mapNotNull null
            val value = tav.`val`?.let { v ->
                when {
                    v.isSetStrVal -> v.strVal.`val`
                    v.isSetFltVal -> v.fltVal.`val`.toString()
                    v.isSetIntVal -> v.intVal.`val`.toString()
                    v.isSetBoolVal -> v.boolVal.`val`.toString()
                    else -> null
                }
            } ?: return@mapNotNull null
            time to value
        }
    }

    /**
     * ST_Percentage union: either an integer in thousandths-of-a-percent ("150000" = 1.5×) or a
     * percent string ("150%"). Returns a plain factor (1.0 = 100%).
     */
    private fun parsePercentFactor(value: Any?): Double? {
        val text = value?.toString()?.trim() ?: return null
        return if (text.endsWith("%")) {
            text.dropLast(1).toDoubleOrNull()?.div(100.0)
        } else {
            text.toDoubleOrNull()?.div(100000.0)
        }
    }

    /** ST_TLTime: a millisecond count or the token "indefinite". */
    private fun parseTlTime(value: Any?): Long? {
        val text = value?.toString() ?: return null
        if (text.equals("indefinite", ignoreCase = true)) return TimeNode.INDEFINITE_MS
        return text.toLongOrNull()
    }

    /** repeatCount is in 1000ths of an iteration ("3000" = 3×); "indefinite" = -1.0. */
    private fun parseRepeat(value: Any?): Double? {
        val text = value?.toString() ?: return null
        if (text.equals("indefinite", ignoreCase = true)) return -1.0
        return text.toDoubleOrNull()?.div(1000.0)
    }
}
