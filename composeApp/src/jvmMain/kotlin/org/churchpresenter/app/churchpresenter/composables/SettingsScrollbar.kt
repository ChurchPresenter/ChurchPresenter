package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * The scrollbar for a settings tab, drawn over the right edge of the tab's root [BoxScope].
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
