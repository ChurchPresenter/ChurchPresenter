package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.ic_copy
import churchpresenter.composeapp.generated.resources.tooltip_copy_link
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Copies [url] to the clipboard, shown beside a button that opens the same address.
 *
 * The browser launch beside it is not always usable. The operating system decides which display a
 * new browser window opens on, and on a two-screen setup that is regularly the projection output —
 * so the operator sees nothing and the congregation sees a browser. Nothing in AWT can pin that
 * window to a screen, so this is the second way to reach the address: copy it, and open it on a
 * phone or on a window the operator places themselves.
 *
 * [onCopy] rather than a direct [org.churchpresenter.app.churchpresenter.utils.SystemClipboard]
 * call so a test observes the copy instead of writing to the developer's own clipboard.
 */
@Composable
fun CopyLinkIconButton(
    url: String,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 28.dp,
    iconSize: Dp = 15.dp
) {
    TooltipIconButton(
        painter = painterResource(Res.drawable.ic_copy),
        text = stringResource(Res.string.tooltip_copy_link),
        onClick = { onCopy(url) },
        modifier = modifier,
        iconSize = iconSize,
        buttonSize = buttonSize,
        iconTint = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
