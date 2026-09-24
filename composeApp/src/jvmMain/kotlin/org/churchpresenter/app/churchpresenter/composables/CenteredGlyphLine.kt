package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.ui.text.style.LineHeightStyle

/**
 * A line box trimmed to its glyph and centred on it — for a single character standing in a small
 * key or badge.
 *
 * A `Text` is centred by its line box, which carries the font's ascent and descent above and below
 * the glyph. For a word that is right; for one symbol or digit in a 12–32dp circle or key it leaves
 * the glyph visibly high or low. Pair it with a `lineHeight` equal to the font size.
 */
val CenteredGlyphLine = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.Both,
)
