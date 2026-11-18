package org.churchpresenter.lottiegen.ui.components

import kotlin.math.roundToLong

/**
 * What a number field makes of the text typed into it. The text is the field's own while it has
 * focus; only a complete number inside the range reaches the config as it is typed, and Enter or
 * leaving the field clamps the rest.
 */
internal object NumberInput {

    /** The number [text] spells, with a comma or a period as the decimal mark; null when it is not one. */
    fun parse(text: String): Double? =
        text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

    /**
     * The value to commit while typing: [text] as a number only when it is complete and already in
     * [min]..[max] (and whole, for a whole field). A partial number such as the "1" of 1920 is
     * null, so it is never clamped under the typist's hands.
     */
    fun live(text: String, min: Double, max: Double, whole: Boolean): Double? {
        val parsed = parse(text) ?: return null
        val shaped = !whole || parsed == parsed.toLong().toDouble()
        return parsed.takeIf { shaped && it in min..max }
    }

    /** The value to commit on Enter or blur: [text] clamped (and rounded, if whole); null when it is not a number. */
    fun finish(text: String, min: Double, max: Double, whole: Boolean): Double? {
        val parsed = parse(text) ?: return null
        val shaped = if (whole) parsed.roundToLong().toDouble() else parsed
        return shaped.coerceIn(min, max)
    }

    /** A value as the field shows it: no ".0" on a whole number. */
    fun format(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
}
