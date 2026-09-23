package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.verticalScroll
import org.churchpresenter.app.churchpresenter.composables.SettingsScrollbar
import org.churchpresenter.app.churchpresenter.composables.SettingsScrollbarGutter
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.bottom
import churchpresenter.composeapp.generated.resources.top

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
internal val DURATION_RANGE = 0..5000
internal const val SPACING_RANGE_MIN = -20
internal const val SPACING_RANGE_MAX = 100
internal val PANE_PADDING = 16.dp

/** The control column: scrolling, with the scrollbar every settings tab draws down its edge. */
@Composable
internal fun PaneScaffold(content: @Composable ColumnScope.() -> Unit) {
    val scrollState = rememberScrollState()
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(start = PANE_PADDING, end = SettingsScrollbarGutter, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
        SettingsScrollbar(scrollState)
    }
}

// ── Shared bits ─────────────────────────────────────────────────────────────────────────────────

internal val FONT_SIZE_RANGE = 8..150
internal val MARGIN_RANGE = 0..500
