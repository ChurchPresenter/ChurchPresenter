package org.churchpresenter.sharedui.composables

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.churchpresenter.sharedui.models.ShortcutAction
import org.churchpresenter.sharedui.utils.LocalShortcuts
import org.churchpresenter.sharedui.utils.labelOrUnbound
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.keyboard_home_search
import org.churchpresenter.strings.generated.resources.keyboard_home_search_live
import org.jetbrains.compose.resources.stringResource

/**
 * Says the keyboard is in a tab's search box, and which keys take it on from there.
 *
 * Searching and driving what is live are separate: while the caret is in the search box, typing and
 * the arrow keys only move what is highlighted, nothing reaches the output until Go Live, and the
 * live item's own keys wait. This banner is how the operator can tell which of the two they are in.
 * With [somethingLive] it also names the key back to the live item; clicking it does the same as
 * that key ([onClick]).
 */
@Composable
fun SearchKeyboardBanner(
    somethingLive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shortcuts = LocalShortcuts.current
    val goLive = shortcuts.labelOrUnbound(ShortcutAction.GO_LIVE)
    val text = if (somethingLive) {
        stringResource(
            Res.string.keyboard_home_search_live,
            goLive,
            shortcuts.labelOrUnbound(ShortcutAction.SWITCH_SEARCH_LIVE),
        )
    } else {
        stringResource(Res.string.keyboard_home_search, goLive)
    }
    FocusHintBanner(text = text, onClick = onClick, modifier = modifier)
}
