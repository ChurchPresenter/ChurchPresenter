package org.churchpresenter.atem

import java.util.Locale
import kotlin.math.floor

/** "25", "59.94" — a frame rate as a switcher names it: exact, untruncated, with a `.` whatever the locale. */
fun formatAtemFps(fps: Double): String =
    if (fps == floor(fps)) fps.toInt().toString()
    else String.format(Locale.US, "%.2f", fps).trimEnd('0').trimEnd('.')
