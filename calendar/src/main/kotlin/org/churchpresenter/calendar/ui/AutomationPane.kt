package org.churchpresenter.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.calendar.generated.resources.Res
import org.churchpresenter.calendar.generated.resources.calendar_add_cue
import org.churchpresenter.calendar.generated.resources.calendar_arm_tip
import org.churchpresenter.calendar.generated.resources.calendar_armed
import org.churchpresenter.calendar.generated.resources.calendar_armed_off
import org.churchpresenter.calendar.generated.resources.calendar_automation_empty
import org.churchpresenter.calendar.generated.resources.calendar_automation_empty_sub
import org.churchpresenter.calendar.generated.resources.calendar_automation_note
import org.churchpresenter.calendar.generated.resources.calendar_automation_title
import org.churchpresenter.calendar.generated.resources.calendar_cue_skip_tip
import org.churchpresenter.calendar.generated.resources.calendar_edit_cues
import org.churchpresenter.calendar.model.PlannedService
import org.churchpresenter.calendar.model.ServiceCue
import org.churchpresenter.calendar.model.cuesInOrder
import org.jetbrains.compose.resources.stringResource

private val TIME_COLUMN = 38.dp
private val TICK_BOX = 15.dp
private val NOTE_ICON = 14.dp
private const val SWITCH_SCALE = 0.7f
private const val DIM = 0.45f
private const val BADGE_TINT = 0.16f
private const val NOTE_ALPHA = 0.6f
private const val ROW_ALPHA = 0.45f

/**
 * The right-hand column: the selected service's cues, in firing order, with the arm switch above
 * and **Edit cues** / **Add cue** below.
 *
 * Laid out to the design: `[time] [tick] [label / what it does] [badge]` per cue, the tick being
 * "skip this one" rather than delete. The whole row opens the cue for editing; the tick keeps its
 * own gesture on top.
 */
@Composable
fun AutomationPane(
    service: PlannedService,
    onArmed: (Boolean) -> Unit,
    onCueEnabled: (cueId: String, enabled: Boolean) -> Unit,
    onEditCue: (ServiceCue) -> Unit,
    onAddCue: () -> Unit,
    onEditCues: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val cues = service.cuesInOrder()
    Column(modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier.fillMaxWidth().height(CalendarMetrics.sectionHeaderHeight).padding(horizontal = 12.dp),
        ) {
            Text(
                text = stringResource(Res.string.calendar_automation_title).uppercase(),
                style = overlineStyle(),
                color = scheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(if (service.armed) Res.string.calendar_armed else Res.string.calendar_armed_off),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = if (service.armed) scheme.tertiary else scheme.onSurfaceVariant,
                maxLines = 1,
            )
            // M3's switch is 52×32; the design's is 27×16. Scaled rather than rebuilt so it keeps
            // the theme's colors and its accessibility role.
            Box(Modifier.height(20.dp).width(36.dp), contentAlignment = Alignment.Center) {
                Switch(
                    checked = service.armed,
                    onCheckedChange = onArmed,
                    modifier = Modifier.scale(SWITCH_SCALE),
                )
            }
        }
        HorizontalDivider()

        ScrollableColumn(
            modifier = Modifier.weight(1f, fill = false).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        ) {
            if (cues.isEmpty()) {
                EmptyCues()
            }
            cues.forEach { cue ->
                CueRow(
                    cue = cue,
                    service = service,
                    onToggle = { onCueEnabled(cue.id, !cue.enabled) },
                    onEdit = { onEditCue(cue) },
                )
            }
            AddCueButton(onClick = onAddCue)
        }

        HorizontalDivider()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Box(
                Modifier
                    .size(NOTE_ICON)
                    .clip(RoundedCornerShape(4.dp))
                    .background(scheme.tertiary.copy(alpha = BADGE_TINT)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Bolt, contentDescription = null, tint = scheme.tertiary, modifier = Modifier.size(9.dp))
            }
            Text(
                text = stringResource(Res.string.calendar_automation_note),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.5.sp, lineHeight = 13.sp),
                color = scheme.onSurfaceVariant.copy(alpha = NOTE_ALPHA),
                modifier = Modifier.weight(1f),
            )
            QuietButton(
                label = stringResource(Res.string.calendar_edit_cues),
                onClick = onEditCues,
                height = SheetMetrics.smallButton,
                accent = true,
            )
        }
    }
}

@Composable
private fun CueRow(cue: ServiceCue, service: PlannedService, onToggle: () -> Unit, onEdit: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val on = cue.enabled && service.armed
    val ink = if (on) 1f else DIM
    val badge = cueBadge(cue.action)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(CalendarMetrics.smallRadius)
            .clickable(onClick = onEdit)
            .padding(vertical = 6.dp, horizontal = 2.dp),
    ) {
        Text(
            text = cueTimeText(cue, service.startTime),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
            fontWeight = FontWeight.Bold,
            color = if (on) scheme.primary else scheme.onSurfaceVariant.copy(alpha = DIM),
            maxLines = 1,
            modifier = Modifier.width(TIME_COLUMN),
        )
        Box(
            Modifier
                .size(TICK_BOX)
                .clip(RoundedCornerShape(5.dp))
                .background(if (cue.enabled) scheme.primary else scheme.surfaceVariant.copy(alpha = ROW_ALPHA))
                .border(1.dp, if (cue.enabled) Color.Transparent else scheme.outlineVariant, RoundedCornerShape(5.dp))
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            if (cue.enabled) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = stringResource(Res.string.calendar_cue_skip_tip),
                    tint = scheme.onPrimary,
                    modifier = Modifier.size(10.dp),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = cue.label.ifBlank { cueActionLabel(cue.action) },
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.5.sp),
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface.copy(alpha = ink),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = cueSubtitle(cue),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.5.sp),
                color = scheme.onSurfaceVariant.copy(alpha = ink),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (badge != null) {
            Text(
                text = badge.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp, letterSpacing = 0.4.sp),
                fontWeight = FontWeight.ExtraBold,
                color = scheme.tertiary.copy(alpha = ink),
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(scheme.tertiary.copy(alpha = BADGE_TINT))
                    .padding(horizontal = 5.dp, vertical = 1.5.dp),
            )
        }
    }
}

@Composable
private fun EmptyCues() {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 2.dp)) {
        Text(
            text = stringResource(Res.string.calendar_automation_empty),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
            fontWeight = FontWeight.SemiBold,
            color = scheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(Res.string.calendar_automation_empty_sub),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp, lineHeight = 15.sp),
            color = scheme.onSurfaceVariant.copy(alpha = NOTE_ALPHA),
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

@Composable
private fun AddCueButton(onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .height(28.dp)
            .clip(CalendarMetrics.buttonRadius)
            .background(scheme.surfaceVariant.copy(alpha = ROW_ALPHA * 0.6f))
            .border(1.dp, scheme.outlineVariant, CalendarMetrics.buttonRadius)
            .clickable(onClick = onClick),
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(11.dp))
        Text(
            text = stringResource(Res.string.calendar_add_cue),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
            fontWeight = FontWeight.Bold,
            color = scheme.primary,
        )
    }
}
