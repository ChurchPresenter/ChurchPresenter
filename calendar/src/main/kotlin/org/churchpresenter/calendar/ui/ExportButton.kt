package org.churchpresenter.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.calendar.generated.resources.Res
import org.churchpresenter.calendar.generated.resources.calendar_export_choose
import org.churchpresenter.calendar.generated.resources.calendar_export_pdf
import org.churchpresenter.calendar.generated.resources.calendar_export_public
import org.churchpresenter.calendar.generated.resources.calendar_export_public_sub
import org.churchpresenter.calendar.generated.resources.calendar_export_staff
import org.churchpresenter.calendar.generated.resources.calendar_export_staff_sub
import org.churchpresenter.calendar.model.PdfAudience
import org.jetbrains.compose.resources.stringResource

private val MENU_WIDTH = 300.dp
private const val SPLIT_TINT = 0.5f

/**
 * Export as a split button: the left half exports the copy last chosen, the right half names it
 * and opens the choice between the public and the staff copy -- picking one exports it.
 */
@Composable
internal fun ExportSplitButton(
    audience: PdfAudience,
    height: Dp,
    onExport: (PdfAudience) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .height(height)
                .clip(CalendarMetrics.buttonRadius)
                .background(scheme.surfaceVariant.copy(alpha = SPLIT_TINT))
                .border(1.dp, scheme.outlineVariant, CalendarMetrics.buttonRadius),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier
                    .fillMaxHeight()
                    .clickable { onExport(audience) }
                    .padding(horizontal = 12.dp),
            ) {
                Icon(
                    Icons.Filled.PictureAsPdf,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp),
                )
                ButtonText(stringResource(Res.string.calendar_export_pdf), scheme.onSurfaceVariant)
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(scheme.outlineVariant))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxHeight()
                    .clickable { open = true }
                    .padding(start = 10.dp, end = 4.dp),
            ) {
                ButtonText(audienceLabel(audience), scheme.primary)
                Icon(
                    Icons.Filled.ArrowDropDown,
                    contentDescription = stringResource(Res.string.calendar_export_choose),
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.width(MENU_WIDTH),
            shape = RoundedCornerShape(10.dp),
            containerColor = scheme.surfaceVariant,
        ) {
            PdfAudience.entries.forEach { entry ->
                DropdownMenuItem(
                    text = {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            Text(
                                text = audienceLabel(entry),
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = stringResource(
                                    if (entry == PdfAudience.PUBLIC) {
                                        Res.string.calendar_export_public_sub
                                    } else {
                                        Res.string.calendar_export_staff_sub
                                    }
                                ),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    },
                    trailingIcon = if (entry == audience) {
                        { Icon(Icons.Filled.Check, contentDescription = null, tint = scheme.primary) }
                    } else {
                        null
                    },
                    onClick = {
                        open = false
                        onExport(entry)
                    },
                )
            }
        }
    }
}

@Composable
private fun audienceLabel(audience: PdfAudience): String = stringResource(
    if (audience == PdfAudience.PUBLIC) Res.string.calendar_export_public else Res.string.calendar_export_staff
)

@Composable
private fun ButtonText(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp),
        fontWeight = FontWeight.SemiBold,
        color = color,
        maxLines = 1,
        softWrap = false,
    )
}
