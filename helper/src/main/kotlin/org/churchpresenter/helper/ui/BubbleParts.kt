package org.churchpresenter.helper.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import org.churchpresenter.strings.generated.resources.helper_do_it
import org.churchpresenter.strings.generated.resources.helper_live_warning
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** A button: what it says, and what it does. */
internal typealias BubbleButton = Pair<StringResource, () -> Unit>

/** One line the helper says. */
@Composable
internal fun Said(text: HelperText, modifier: Modifier = Modifier) {
    Text(text.resolve(), style = MaterialTheme.typography.bodyMedium, modifier = modifier)
}

/** The small coloured heading over a part of the bubble: "Tip of the day", "Display setup". */
@Composable
internal fun BubbleHeading(res: StringResource) {
    Text(stringResource(res), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

/** Choices laid out as chips, wrapping onto more rows as they need. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChipRow(options: List<Pair<HelperText, () -> Unit>>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { (label, onClick) ->
            SuggestionChip(onClick = onClick, label = { Text(label.resolve()) })
        }
    }
}

/** A row of buttons, the main one last — where the eye ends up. */
@Composable
internal fun Actions(vararg buttons: BubbleButton, primary: BubbleButton? = null) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        buttons.forEach { (label, onClick) -> TextButton(onClick = onClick) { Text(stringResource(label)) } }
        primary?.let { (label, onClick) ->
            Spacer(Modifier.width(4.dp))
            Button(onClick = onClick, modifier = Modifier.testTag("helper.primary")) { Text(stringResource(label)) }
        }
    }
}

/** What the helper is about to do, and the operator's yes or no. */
@Composable
internal fun ConfirmCard(text: HelperText, action: HelperAction, onConfirm: () -> Unit, onCancel: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (action is HelperAction.SetBackgroundColor) {
            ColorSwatch(action.hex)
            Spacer(Modifier.width(10.dp))
        }
        Said(text, Modifier.testTag("helper.confirm"))
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

/** The colour a background change would use, so it is seen as well as named. */
@Composable
private fun ColorSwatch(hex: String) {
    val color = hex.removePrefix("#").toLongOrNull(HEX_RADIX)?.let { Color(it or OPAQUE) } ?: Color.Black
    val shape = RoundedCornerShape(6.dp)
    Box(Modifier.size(28.dp).background(color, shape).border(1.dp, MaterialTheme.colorScheme.outline, shape))
}

private const val HEX_RADIX = 16
private const val OPAQUE = 0xFF000000
