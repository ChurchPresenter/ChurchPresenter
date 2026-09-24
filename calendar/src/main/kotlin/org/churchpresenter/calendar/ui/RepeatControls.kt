package org.churchpresenter.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.calendar.generated.resources.Res
import org.churchpresenter.calendar.generated.resources.calendar_applies_to
import org.churchpresenter.calendar.generated.resources.calendar_repeat_biweekly
import org.churchpresenter.calendar.generated.resources.calendar_repeat_monthly
import org.churchpresenter.calendar.generated.resources.calendar_repeat_once
import org.churchpresenter.calendar.generated.resources.calendar_repeat_weekly
import org.churchpresenter.calendar.generated.resources.calendar_scope_series
import org.churchpresenter.calendar.generated.resources.calendar_scope_this
import org.churchpresenter.calendar.generated.resources.calendar_series_note
import org.churchpresenter.calendar.model.ServiceRepeat
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.theme.sunken
import org.churchpresenter.theme.raised
import org.churchpresenter.theme.elevationPalette

private val CHECK_BOX = 16.dp
private const val ON_TINT = 0.16f

/**
 * For a service that is one of a series: whether Save and Delete touch this one or all of them.
 *
 * A series is made by **Copy** with a repeat, so this is the one place the sheet knows about it —
 * changing the rhythm itself is another Copy, not an edit.
 */
@Composable
fun SeriesScopeSection(
    repeat: ServiceRepeat,
    seriesSize: Int,
    wholeSeries: Boolean,
    onScope: (wholeSeries: Boolean) -> Unit,
) {
    Column {
        FieldLabel(stringResource(Res.string.calendar_applies_to))
        Spacer(Modifier.height(5.dp))
        SegmentedSelector(
            options = listOf(false, true),
            selected = wholeSeries,
            label = { stringResource(if (it) Res.string.calendar_scope_series else Res.string.calendar_scope_this) },
            onSelect = onScope,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = stringResource(Res.string.calendar_series_note, repeatLabel(repeat), seriesSize),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun repeatLabel(repeat: ServiceRepeat): String = stringResource(
    when (repeat) {
        ServiceRepeat.NONE -> Res.string.calendar_repeat_once
        ServiceRepeat.WEEKLY -> Res.string.calendar_repeat_weekly
        ServiceRepeat.BIWEEKLY -> Res.string.calendar_repeat_biweekly
        ServiceRepeat.MONTHLY -> Res.string.calendar_repeat_monthly
    }
)

/**
 * One of the design's **Include** rows: a card that is a checkbox, tinted while on.
 *
 * The whole card toggles, not just the box — a 16dp target beside two lines of text is the kind
 * of thing that reads as clickable and is not.
 */
@Composable
fun IncludeRow(label: String, sub: String, on: Boolean, onToggle: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    SettingCard(
        horizontalPadding = 10.dp,
        verticalPadding = 8.dp,
        modifier = Modifier
            .clip(SheetMetrics.cardRadius)
            .background(if (on) scheme.primary.copy(alpha = ON_TINT) else Color.Transparent)
            .clickable(onClick = onToggle),
    ) {
        val palette = elevationPalette()
        Box(
            Modifier
                .size(CHECK_BOX)
                .then(
                    // Ticked is a raised accent key, clear a sunken well -- the app's checkbox.
                    if (on) {
                        Modifier.raised(RoundedCornerShape(4.dp), palette.accent, palette, lift = 2.dp)
                    } else {
                        Modifier.sunken(RoundedCornerShape(4.dp), palette)
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (on) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = palette.accent.ink,
                    modifier = Modifier.size(11.dp),
                )
            }
        }
        CardText(title = label, subtitle = sub)
    }
}
