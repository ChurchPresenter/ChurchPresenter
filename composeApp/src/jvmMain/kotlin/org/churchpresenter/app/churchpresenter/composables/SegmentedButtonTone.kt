package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * How a [SegmentedButton] paints the segment that is selected.
 *
 * [NEUTRAL] is the long-standing look and the default, so nothing changes where this is not asked
 * for. [ACCENT] fills the selected segment with the theme's accent, the way the song editor's own
 * pane tabs do -- readable at a glance rather than inferred from a small difference in surface tint.
 *
 * Ambient rather than a parameter because the Bible and Song settings tabs reach a segmented button
 * from nineteen call sites spread over seven files, several of them behind `LongMethod` entries in
 * `config/detekt/baseline.xml` that are keyed by signature. One provider at the top of each tab
 * carries the choice to all of them, including the shared preview rows they compose.
 */
enum class SegmentedButtonTone { NEUTRAL, ACCENT }

/** The tone segmented buttons in this subtree paint with. See [SegmentedButtonTone]. */
val LocalSegmentedButtonTone = staticCompositionLocalOf { SegmentedButtonTone.NEUTRAL }
