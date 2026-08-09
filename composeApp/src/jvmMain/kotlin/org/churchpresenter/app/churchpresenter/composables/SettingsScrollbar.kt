package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollbarAdapter
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
