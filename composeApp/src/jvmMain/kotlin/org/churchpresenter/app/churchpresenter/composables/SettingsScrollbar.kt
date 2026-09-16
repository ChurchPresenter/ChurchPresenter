package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Width kept clear down the right edge of a settings tab so [SettingsScrollbar] sits beside the
 * content instead of over it — the 5.dp bar of the app's scrollbar style plus a gap.
 */
val SettingsScrollbarGutter: Dp = 12.dp

/**
 * The scrollbar for a settings tab, drawn down the right edge of the tab's root [BoxScope].
 *
 * Every settings tab scrolls its content, and each one wraps that content in a full-size Box, so
 * this is the one line each needs to gain the affordance the rest of the app already has. Styling
 * comes from `LocalScrollbarStyle`, set once in the app theme.
 *
 * @param scrollState the same state passed to the tab's `Modifier.verticalScroll`
 */
@Composable
fun BoxScope.SettingsScrollbar(scrollState: ScrollState) {
    VerticalScrollbar(
        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        adapter = rememberScrollbarAdapter(scrollState)
    )
}

/**
 * A settings column that scrolls, with [SettingsScrollbar] down its edge and the gutter kept clear
 * for it.
 *
 * The shape every scrolling pane in the settings dialog was writing out by hand — a Box, a Column
 * carrying `verticalScroll` and the end padding, and the bar beside it. Written once so a pane that
 * grows a row gains the affordance without anyone remembering to add it.
 */
@Composable
fun SettingsScrollColumn(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollState = rememberScrollState()
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(scrollState)
                .padding(end = SettingsScrollbarGutter),
            verticalArrangement = verticalArrangement,
            content = content,
        )
        SettingsScrollbar(scrollState)
    }
}
