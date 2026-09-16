package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.close
import churchpresenter.composeapp.generated.resources.color
import churchpresenter.composeapp.generated.resources.ic_close
import churchpresenter.composeapp.generated.resources.outline_show
import churchpresenter.composeapp.generated.resources.text_outline
import churchpresenter.composeapp.generated.resources.text_outline_width
import org.churchpresenter.core.models.text.TextOutline
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val DIALOG_WIDTH = 300.dp
private val SECTION_PADDING = 12.dp
private val CLOSE_BUTTON_SIZE = 28.dp
private val CLOSE_ICON_SIZE = 14.dp
private val WIDTH_FIELD_WIDTH = 104.dp

/**
 * The outline's colour and width, behind the caret of the toolbar's outline button.
 *
 * A dialog for the reason [TextBackdropDialog] is one: the button sits in a row of square toggles
 * that has no room for a colour picker, and the row is shared by every panel that styles text.
 * The switch is repeated here as well as on the button, so an operator who opened this to set a
 * colour is not left with a look that draws nothing until they find the toggle again.
 */
@Composable
fun TextOutlineDialog(
    outline: TextOutline,
    onChange: (TextOutline) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier.width(DIALOG_WIDTH),
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(SECTION_PADDING),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.text_outline).uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.8.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(CLOSE_BUTTON_SIZE)) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_close),
                            contentDescription = stringResource(Res.string.close),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(CLOSE_ICON_SIZE),
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(
                    modifier = Modifier.padding(SECTION_PADDING),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    LabeledCheckbox(
                        checked = outline.enabled,
                        onCheckedChange = { onChange(outline.copy(enabled = it)) },
                        label = stringResource(Res.string.outline_show),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ColorPickerField(
                            label = stringResource(Res.string.color).removeSuffix(":"),
                            color = outline.color,
                            onColorChange = { onChange(outline.copy(color = it)) },
                            modifier = Modifier.weight(1f),
                        )
                        NumberSettingsTextField(
                            label = stringResource(Res.string.text_outline_width),
                            initialText = outline.width,
                            onValueChange = { onChange(outline.copy(width = it)) },
                            range = TextOutline.WIDTH_RANGE,
                            modifier = Modifier.width(WIDTH_FIELD_WIDTH),
                        )
                    }
                }
            }
        }
    }
}
