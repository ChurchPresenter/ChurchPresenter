package org.churchpresenter.lottiegen.lottie

/**
 * Constants of the Lottie document format itself, as opposed to any one style's geometry.
 *
 * These are the numbers the format fixes: they are not tunable, and a style that "adjusts" one of
 * them is writing a file players will read differently. Named here so a reader can tell those
 * apart from the ordinary geometry each style chooses for itself.
 */

/**
 * Lottie writes opacity and scale as percentages, so fully opaque and unscaled are both `100` --
 * which is why the same literal turns up in `o`, `s`, `e` and `a` properties across every helper.
 */
internal const val FULL_PERCENT = 100
internal const val FULL_PERCENT_D = 100.0

/** Percentages arriving from a spec or the UI are divided by this to reach Lottie's 0..1 range. */
internal const val PERCENT_SCALE = 100.0

/** Lottie's own default line height: 1.2x the font size. */
internal const val LINE_HEIGHT_FACTOR = 1.2

/** Stroke miter limit. Lottie's default, restated because the format has no implicit one. */
internal const val MITER_LIMIT = 4

/**
 * Ascent for the synthetic font descriptor, as a percentage of the em box.
 *
 * Real per-font ascent is not available where this document is built, and every bundled family
 * sits close enough to this that the difference is under a pixel at the sizes used.
 */
internal const val SYNTHETIC_FONT_ASCENT = 72.6
