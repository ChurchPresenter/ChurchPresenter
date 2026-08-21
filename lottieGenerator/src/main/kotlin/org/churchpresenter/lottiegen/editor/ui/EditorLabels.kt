package org.churchpresenter.lottiegen.editor.ui

import org.churchpresenter.lottiegen.spec.AnchorIn
import org.churchpresenter.lottiegen.spec.AnimProperty
import org.churchpresenter.lottiegen.spec.ColorRole
import org.churchpresenter.lottiegen.spec.EasingKind
import org.churchpresenter.lottiegen.spec.LineAnchor
import org.churchpresenter.lottiegen.spec.OffsetUnit
import org.churchpresenter.lottiegen.spec.SlotKind
import org.churchpresenter.lottiegen.spec.TextAnimatorKind
import org.churchpresenter.lottiegen.spec.TextFieldRef
import org.churchpresenter.lottiegen.spec.VisibilityRule
import org.churchpresenter.lottiegen.ui.Strings

/** Display labels for spec enums (localized through the shared Strings bundle). */
object EditorLabels {

    fun rule(rule: VisibilityRule): String = when (rule) {
        VisibilityRule.ALWAYS -> Strings.editorRuleAlways
        VisibilityRule.BG_ENABLED -> Strings.editorRuleBg
        VisibilityRule.LOGO_ENABLED -> Strings.editorRuleLogo
        VisibilityRule.NAME_VISIBLE -> Strings.editorRuleName
        VisibilityRule.INFO_VISIBLE -> Strings.editorRuleInfo
        VisibilityRule.BORDER_SET -> Strings.editorRuleBorder
    }

    fun anchor(anchor: AnchorIn): String = when (anchor) {
        AnchorIn.START -> Strings.editorAnchorStart
        AnchorIn.CENTER -> Strings.editorAnchorCenter
        AnchorIn.END -> Strings.editorAnchorEnd
    }

    fun line(line: LineAnchor): String = when (line) {
        LineAnchor.BLOCK_CENTER -> Strings.editorLineBlock
        LineAnchor.NAME_LINE -> Strings.editorLineName
        LineAnchor.INFO_LINE -> Strings.editorLineInfo
    }

    fun role(role: ColorRole): String = when (role) {
        ColorRole.NAME -> Strings.editorRoleName
        ColorRole.INFO -> Strings.editorRoleInfo
        ColorRole.ACCENT -> Strings.editorRoleAccent
        ColorRole.BG -> Strings.editorRoleBg
        ColorRole.BORDER -> Strings.editorRoleBorder
    }

    fun property(property: AnimProperty): String = when (property) {
        AnimProperty.POSITION_OFFSET -> Strings.editorPropPosition
        AnimProperty.OPACITY -> Strings.editorPropOpacity
        AnimProperty.SCALE -> Strings.editorPropScale
        AnimProperty.ROTATION -> Strings.editorPropRotation
        AnimProperty.RECT_SIZE -> Strings.editorPropRectSize
        AnimProperty.STROKE_WIDTH -> Strings.editorPropStrokeWidth
        AnimProperty.TRIM -> Strings.editorPropTrim
    }

    fun easing(easing: EasingKind): String = when (easing) {
        EasingKind.DEFAULT -> Strings.editorEasingDefault
        EasingKind.LINEAR -> Strings.editorEasingLinear
    }

    fun offsetUnit(unit: OffsetUnit): String = when (unit) {
        OffsetUnit.EM -> Strings.editorUnitEm
        OffsetUnit.ELEMENT_WIDTH -> Strings.editorUnitElementWidth
        OffsetUnit.ELEMENT_HEIGHT -> Strings.editorUnitElementHeight
    }

    fun slotKind(kind: SlotKind): String = when (kind) {
        SlotKind.LOGO -> Strings.editorSlotKindLogo
        SlotKind.FIXED -> Strings.editorSlotKindFixed
        SlotKind.TEXT -> Strings.editorSlotKindText
    }

    fun textField(field: TextFieldRef): String = when (field) {
        TextFieldRef.NAME -> Strings.editorFieldName
        TextFieldRef.INFO -> Strings.editorFieldInfo
    }

    fun animatorKind(kind: TextAnimatorKind): String = when (kind) {
        TextAnimatorKind.SEQUENTIAL_REVEAL -> Strings.editorAnimatorSequential
        TextAnimatorKind.RANDOM_FADE -> Strings.editorAnimatorRandom
    }

    fun align(align: String): String = when (align) {
        "center" -> Strings.editorAlignCenter
        "right" -> Strings.editorAlignRight
        else -> Strings.editorAlignLeft
    }
}
