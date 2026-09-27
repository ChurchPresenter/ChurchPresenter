package org.churchpresenter.app.churchpresenter.dialogs.tabs

import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.settingPaths

/** One row's worth of a [TextLook]: what a row of the Text group changes. */
internal enum class TextLookField {
    FONT, SIZE, AUTO_FIT, COLOR, CHORD_COLOR, STYLE, ALIGNMENT, TRANSFORM, LETTER_SPACING, WORD_SPACING,
    OUTLINE, BACKDROP, SHADOW, SHADOW_DETAIL,
}

/** Which stored settings each row of a Text group writes, for a linked profile to mark them. */
internal class TextLookPaths(private val byField: Map<TextLookField, List<String>>) {
    operator fun get(field: TextLookField): List<String> = byField[field].orEmpty()

    /** Every path the group writes, for its Revert. */
    val all: List<String> get() = byField.values.flatten().distinct()

    /** Both sets of paths, row by row. */
    operator fun plus(other: TextLookPaths): TextLookPaths =
        TextLookPaths(TextLookField.entries.associateWith { this[it] + other[it] })

    companion object {
        val NONE = TextLookPaths(emptyMap())
    }
}

/** [this] with [field] changed, to any value but its own. */
internal fun TextLook.perturbed(field: TextLookField): TextLook = when (field) {
    TextLookField.FONT -> copy(fontType = "$fontType~")
    TextLookField.SIZE -> copy(fontSize = fontSize + 1)
    TextLookField.AUTO_FIT -> copy(autoFit = autoFit?.not())
    TextLookField.COLOR -> copy(color = "$color~")
    TextLookField.CHORD_COLOR -> copy(chordColor = chordColor?.let { "$it~" })
    TextLookField.STYLE -> copy(bold = !bold, italic = !italic, underline = !underline, strikethrough = !strikethrough)
    TextLookField.ALIGNMENT -> copy(alignment = "$alignment~")
    TextLookField.TRANSFORM -> copy(transform = "$transform~")
    TextLookField.LETTER_SPACING -> copy(letterSpacing = letterSpacing + 1)
    TextLookField.WORD_SPACING -> copy(wordSpacing = wordSpacing + 1)
    TextLookField.OUTLINE -> copy(outline = outline.copy(enabled = !outline.enabled))
    TextLookField.BACKDROP -> copy(backdrop = backdrop.copy(lineBackground = !backdrop.lineBackground))
    TextLookField.SHADOW -> copy(shadow = !shadow)
    TextLookField.SHADOW_DETAIL -> copy(
        shadowColor = "$shadowColor~",
        shadowSize = shadowSize + 1,
        shadowOpacity = shadowOpacity + 1,
    )
}

/**
 * Where [write] stores each field of [look] on [profile], found by writing each one changed and
 * seeing which values move -- the Bible and song styles keep one look under a hundred different
 * names, and this is the one place that has to know none of them.
 *
 * An outline or a highlight is one control over a whole object, so its row owns the object rather
 * than the one field the probe happened to change.
 */
internal fun probeTextLookPaths(
    profile: OutputProfile,
    look: TextLook,
    write: (OutputProfile, TextLook) -> OutputProfile,
): TextLookPaths {
    val before = write(profile, look).settingPaths()
    val byField = TextLookField.entries.associateWith { field ->
        val after = write(profile, look.perturbed(field)).settingPaths()
        val moved = after.keys.filter { before[it] != after[it] }
        if (field == TextLookField.OUTLINE || field == TextLookField.BACKDROP) {
            moved.map { it.substringBeforeLast('.') }.distinct()
        } else {
            moved
        }
    }
    return TextLookPaths(byField)
}
