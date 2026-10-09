package org.churchpresenter.helper.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.churchpresenter.helper.HelperText
import org.churchpresenter.helper.action.HelperAction
import org.churchpresenter.helper.resolve
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.helper_cancel
import org.churchpresenter.strings.generated.resources.helper_confirm_hide
import org.churchpresenter.strings.generated.resources.helper_do_it
import org.churchpresenter.strings.generated.resources.helper_hide_confirm_button
import org.churchpresenter.strings.generated.resources.helper_hide_menu_path
import org.churchpresenter.strings.generated.resources.helper_hide_where
import org.churchpresenter.strings.generated.resources.helper_live_warning
import org.jetbrains.compose.resources.stringResource

/** What the helper is about to do, and the operator's yes or no. */
@Composable
internal fun ConfirmCard(text: HelperText, action: HelperAction, onConfirm: () -> Unit, onCancel: () -> Unit) {
    WickBubble(Modifier.testTag("helper.confirm")) {
        if (action is HelperAction.SetBackgroundColor) {
            ColorSwatch(action.hex)
            Spacer(Modifier.width(10.dp))
        }
        BubbleText(text.resolve(), MaterialTheme.colorScheme.onSurface)
    }
    if (action.affectsLive) {
        Text(
            stringResource(Res.string.helper_live_warning),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    Actions(Res.string.helper_cancel to onCancel, primary = Res.string.helper_do_it to onConfirm)
}

/** Asking before Wick is hidden, as a card in the conversation, saying where to get it back. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HideCard(onCancel: () -> Unit, onHide: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(lifted(CARD_LIFT), shape)
            .border(1.dp, colors.outlineVariant, shape)
            .padding(start = 13.dp, end = 13.dp, top = 13.dp, bottom = 12.dp)
            .testTag("helper.confirmHide"),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Text(stringResource(Res.string.helper_confirm_hide), style = MaterialTheme.typography.titleSmall)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Res.string.helper_hide_where),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            Text(
                stringResource(Res.string.helper_hide_menu_path),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurface,
                modifier = Modifier
                    .background(colors.onSurface.copy(alpha = KEY_ALPHA), RoundedCornerShape(5.dp))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
        Actions(
            Res.string.helper_cancel to onCancel,
            primary = Res.string.helper_hide_confirm_button to onHide,
            quiet = true,
        )
    }
}

/** The colour a background change would use, so it is seen as well as named. */
@Composable
private fun ColorSwatch(hex: String) {
    val color = hex.removePrefix("#").toLongOrNull(HEX_RADIX)?.let { Color(it or OPAQUE) } ?: Color.Black
    val shape = RoundedCornerShape(6.dp)
    Box(Modifier.size(28.dp).background(color, shape).border(1.dp, MaterialTheme.colorScheme.outline, shape))
}

private const val KEY_ALPHA = 0.08f
private const val HEX_RADIX = 16
private const val OPAQUE = 0xFF000000
