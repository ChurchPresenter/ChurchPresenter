package org.churchpresenter.atem

import java.util.Locale
import kotlin.math.floor

/**
 * An ATEM frame rate as a person reads it: `"25"`, `"59.94"`.
 *
 * Exact without truncation, and locale-independent — the decimal point must be a dot whatever the
 * operator's locale says, because the same string is compared against and round-tripped back into a
 * `Double` by the settings field that shows it.
 *
 * Lives here rather than beside either of the two screens that show it: the ATEM settings tab and
 * the Lower Third tab both format the switcher's fps, and they are now in different modules.
 */
fun formatAtemFps(fps: Double): String =
    if (fps == floor(fps)) fps.toInt().toString()
    else String.format(Locale.US, "%.2f", fps).trimEnd('0').trimEnd('.')
