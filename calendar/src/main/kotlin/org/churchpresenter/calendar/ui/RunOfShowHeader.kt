package org.churchpresenter.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.calendar.generated.resources.Res
import org.churchpresenter.calendar.generated.resources.calendar_copy_service
import org.churchpresenter.calendar.generated.resources.calendar_copy_service_tip
import org.churchpresenter.calendar.generated.resources.calendar_planned_total
import org.churchpresenter.calendar.generated.resources.calendar_run_of_show
import org.churchpresenter.calendar.generated.resources.calendar_save_template
import org.churchpresenter.calendar.generated.resources.calendar_save_template_tip
import org.churchpresenter.calendar.model.PlannedService
import org.churchpresenter.calendar.model.formatDuration
import org.jetbrains.compose.resources.stringResource

/**
 * The pane's header: the overline, the count and planned length, then — as the design has them —
 * **Copy** and **Template**, the two things done to a run of show as a whole rather than to a row.
 */
@Composable
internal fun RunOfShowHeader(service: PlannedService, onCopy: () -> Unit, onSaveTemplate: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(CalendarMetrics.sectionHeaderHeight)
            .padding(horizontal = 13.dp),
    ) {
        Text(
            text = stringResource(Res.string.calendar_run_of_show).uppercase(),
            style = overlineStyle(),
            color = scheme.onSurfaceVariant.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = runMeta(service),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
            color = scheme.onSurfaceVariant,
            maxLines = 1,
        )
        Box(Modifier.width(1.dp).height(HEADER_RULE_HEIGHT).background(scheme.outlineVariant))
        HeaderAction(
            icon = Icons.Filled.ContentCopy,
            label = stringResource(Res.string.calendar_copy_service),
            tooltip = stringResource(Res.string.calendar_copy_service_tip),
            onClick = onCopy,
        )
        HeaderAction(
            icon = Icons.Filled.Dashboard,
            label = stringResource(Res.string.calendar_save_template),
            tooltip = stringResource(Res.string.calendar_save_template_tip),
            onClick = onSaveTemplate,
        )
    }
}

/** One of the header's two small bordered actions, at the design's 21dp. */
@Composable
private fun HeaderAction(
    icon: ImageVector,
    label: String,
    tooltip: String,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .height(HEADER_ACTION_HEIGHT)
            .clip(RoundedCornerShape(6.dp))
            .background(scheme.surfaceVariant.copy(alpha = ROW_ALPHA))
            .border(1.dp, scheme.outlineVariant, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
    ) {
        Icon(icon, contentDescription = tooltip, tint = scheme.onSurfaceVariant, modifier = Modifier.size(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            fontWeight = FontWeight.Bold,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun runMeta(service: PlannedService): String {
    val count = itemCountLabel(service.contentItems().size)
    val total = service.plannedTotalSeconds()
    // "0 planned" would be noise on a service nobody has estimated yet.
    return if (total > 0) {
        "$count · " + stringResource(Res.string.calendar_planned_total, formatDuration(total))
    } else {
        count
    }
}

private const val ROW_ALPHA = 0.45f
private val HEADER_ACTION_HEIGHT = 21.dp
private val HEADER_RULE_HEIGHT = 14.dp
