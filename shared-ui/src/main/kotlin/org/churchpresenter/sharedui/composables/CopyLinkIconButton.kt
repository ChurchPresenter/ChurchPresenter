package org.churchpresenter.sharedui.composables

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.icons.generated.resources.ic_copy
import org.churchpresenter.strings.generated.resources.tooltip_copy_link
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.hosanna.composables.button.IconButton
import org.churchpresenter.hosanna.composables.feedback.Tooltip
import org.churchpresenter.hosanna.composables.model.ButtonHierarchy
import org.churchpresenter.hosanna.composables.model.ButtonIntent
import org.churchpresenter.hosanna.composables.model.IconButtonSize

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
    size: IconButtonSize = IconButtonSize.Small,
) {
    val text = stringResource(Res.string.tooltip_copy_link)
    Tooltip(text = text, modifier = modifier) {
        IconButton(
            icon = painterResource(IconRes.drawable.ic_copy),
            contentDescription = text,
            onClick = { onCopy(url) },
            size = size,
            hierarchy = ButtonHierarchy.Tertiary,
            intent = ButtonIntent.Neutral,
        )
    }
}
