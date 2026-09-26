package org.churchpresenter.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.calendar.generated.resources.Res
import org.churchpresenter.calendar.generated.resources.calendar_cue_toast_close
import org.churchpresenter.calendar.generated.resources.calendar_export_failed_title
import org.churchpresenter.calendar.generated.resources.calendar_export_saved_title
import org.jetbrains.compose.resources.stringResource

/**
 * `PDF SAVED` and the file's name, or `COULDN'T SAVE THE PDF` and why -- in the corner, the way a
 * fired cue is reported.
 *
 * Without it an export ended in silence either way: the file chooser closed and nothing on screen
 * changed, which reads as a Save button that does nothing (#651), and a failure went only to the
 * crash reporter, never to the person who pressed Save. It stays until dismissed.
 */
@Composable
fun ExportToast(outcome: ExportOutcome, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val failed = outcome is ExportOutcome.Failed
    val tone = if (failed) scheme.error else scheme.tertiary
    Row(
        modifier = modifier
            .width(TOAST_WIDTH)
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(11.dp))
            .background(scheme.surfaceContainerHigh)
            .border(1.dp, tone.copy(alpha = TOAST_BORDER), RoundedCornerShape(11.dp)),
    ) {
        Box(Modifier.width(TOAST_ACCENT).fillMaxHeight().background(tone))
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            modifier = Modifier.weight(1f).padding(start = 10.dp, end = 8.dp, top = 9.dp, bottom = 9.dp),
        ) {
            Box(
                Modifier.size(TOAST_ICON).clip(RoundedCornerShape(7.dp)).background(tone.copy(alpha = TOAST_TINT)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (failed) Icons.Filled.ErrorOutline else Icons.Filled.PictureAsPdf,
                    contentDescription = null,
                    tint = tone,
                    modifier = Modifier.size(13.dp),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(
                        if (failed) Res.string.calendar_export_failed_title else Res.string.calendar_export_saved_title,
                    ).uppercase(),
                    style = overlineStyle().copy(fontSize = 9.sp),
                    color = tone,
                    maxLines = 1,
                )
                Text(
                    text = when (outcome) {
                        is ExportOutcome.Saved -> outcome.file.name
                        is ExportOutcome.Failed -> outcome.reason
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.5.sp),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (outcome is ExportOutcome.Saved) {
                    Text(
                        text = outcome.file.parent.orEmpty(),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        color = scheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box(
                Modifier
                    .size(SheetMetrics.smallButton)
                    .clip(CalendarMetrics.smallRadius)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(Res.string.calendar_cue_toast_close),
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(11.dp),
                )
            }
        }
    }
}

private val TOAST_WIDTH = 310.dp
private val TOAST_ICON = 24.dp
private val TOAST_ACCENT = 3.dp
private const val TOAST_TINT = 0.14f
private const val TOAST_BORDER = 0.55f
