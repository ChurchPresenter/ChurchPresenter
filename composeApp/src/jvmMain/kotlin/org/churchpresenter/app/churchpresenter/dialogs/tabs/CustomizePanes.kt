package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.unit.dp

/**
 * The shared parts of the Customize dialog's per-output panes, built from [CustomizeForm]'s
 * controls. Each pane lives in its own file beside this one.
 *
 * The panes carry the same appearance settings their tabs do — typography, colour, shadow,
 * alignment, look-ahead, transitions and backgrounds — so an output can be given any look the
 * global tab can give, without going to the global tab to do it. What stays out is what has no
 * per-output meaning: the stock-photo API keys, the quick-backgrounds tray, and the library and
 * browse options. Those remain on their own tabs, where one value serves every output.
 *
 * Each pane reads the profile the output actually draws with — full-screen or lower-third — from
 * [LocalOutputStyleScope], so one set of controls edits whichever half applies.
 */

internal val BAND_RANGE = 5..100
internal val BLUR_RANGE = 0..100
internal val SOURCE_FIELD_WIDTH = 260.dp
internal val PERCENT_RANGE = 0..100

// ── Shared bits ─────────────────────────────────────────────────────────────────────────────────

