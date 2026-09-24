package org.churchpresenter.calendar.ui

import androidx.compose.foundation.layout.width
import org.churchpresenter.theme.components.RaisedSwitch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.churchpresenter.calendar.generated.resources.Res
import org.churchpresenter.calendar.generated.resources.calendar_auto_load
import org.churchpresenter.calendar.generated.resources.calendar_auto_load_lead
import org.churchpresenter.calendar.generated.resources.calendar_auto_load_lead_sub
import org.churchpresenter.calendar.generated.resources.calendar_auto_load_sub
import org.churchpresenter.calendar.generated.resources.calendar_cloud_sync
import org.churchpresenter.calendar.generated.resources.calendar_cloud_sync_sub
import org.churchpresenter.calendar.generated.resources.calendar_default_item
import org.churchpresenter.calendar.generated.resources.calendar_default_item_sub
import org.churchpresenter.calendar.generated.resources.calendar_default_sermon
import org.churchpresenter.calendar.generated.resources.calendar_default_sermon_sub
import org.churchpresenter.calendar.generated.resources.calendar_default_start
import org.churchpresenter.calendar.generated.resources.calendar_default_start_sub
import org.churchpresenter.calendar.generated.resources.calendar_time_12h
import org.churchpresenter.calendar.generated.resources.calendar_time_24h
import org.churchpresenter.calendar.generated.resources.calendar_time_format
import org.churchpresenter.calendar.generated.resources.calendar_time_format_sub
import org.churchpresenter.calendar.CalendarCloudSync
import org.churchpresenter.calendar.model.AUTO_LOAD_LEAD_MAX
import org.churchpresenter.calendar.model.AUTO_LOAD_LEAD_MIN
import org.churchpresenter.calendar.model.CalendarPreferences
import org.churchpresenter.calendar.model.clockText
import org.churchpresenter.calendar.model.formatDuration
import org.churchpresenter.calendar.model.parseClockText
import org.churchpresenter.calendar.model.parseDuration
import org.churchpresenter.calendar.model.parseLeadMinutes
import org.churchpresenter.calendar.model.storedTime
import org.jetbrains.compose.resources.stringResource

private val PREF_FIELD = 88.dp
private val PREF_FIELD_HEIGHT = 29.dp
private val FORMAT_SELECTOR = 150.dp

/** The two times the format row demonstrates with — one each side of noon, as the design's are. */
private const val MORNING_EXAMPLE = "10:00"
private const val EVENING_EXAMPLE = "18:30"

/**
 * The settings dialog's `Defaults` tab: the clock format, the default times and lengths, auto-load,
 * and -- when the app offers it -- the cloud sync switch.
 */
@Composable
internal fun DefaultsTab(
    preferences: CalendarPreferences,
    onChange: (CalendarPreferences) -> Unit,
    cloudSync: CalendarCloudSync? = null,
) {
    val use24Hour = preferences.use24HourClock
    SettingCard {
        CardText(
            title = stringResource(Res.string.calendar_time_format),
            subtitle = stringResource(
                Res.string.calendar_time_format_sub,
                clockText(MORNING_EXAMPLE, use24Hour),
                clockText(EVENING_EXAMPLE, use24Hour),
            ),
        )
        SegmentedSelector(
            options = listOf(false, true),
            selected = use24Hour,
            label = { stringResource(if (it) Res.string.calendar_time_24h else Res.string.calendar_time_12h) },
            onSelect = { onChange(preferences.copy(use24HourClock = it)) },
            height = PREF_FIELD_HEIGHT,
            modifier = Modifier.width(FORMAT_SELECTOR),
        )
    }
    PrefRow(
        title = stringResource(Res.string.calendar_default_start),
        subtitle = stringResource(Res.string.calendar_default_start_sub),
        value = clockText(preferences.defaultStartTime, use24Hour),
        isValid = { parseClockText(it) != null },
        onCommit = { text ->
            parseClockText(text)?.let { onChange(preferences.copy(defaultStartTime = storedTime(it))) }
        },
    )
    PrefRow(
        title = stringResource(Res.string.calendar_default_item),
        subtitle = stringResource(Res.string.calendar_default_item_sub),
        value = formatDuration(preferences.defaultItemSeconds),
        isValid = { parseDuration(it) != null },
        onCommit = { parseDuration(it)?.let { secs -> onChange(preferences.copy(defaultItemSeconds = secs)) } },
    )
    PrefRow(
        title = stringResource(Res.string.calendar_default_sermon),
        subtitle = stringResource(Res.string.calendar_default_sermon_sub),
        value = formatDuration(preferences.defaultSermonSeconds),
        isValid = { parseDuration(it) != null },
        onCommit = { parseDuration(it)?.let { secs -> onChange(preferences.copy(defaultSermonSeconds = secs)) } },
    )
    SettingCard {
        CardText(
            title = stringResource(Res.string.calendar_auto_load),
            subtitle = stringResource(Res.string.calendar_auto_load_sub),
        )
        RaisedSwitch(
            checked = preferences.autoLoadService,
            onCheckedChange = { onChange(preferences.copy(autoLoadService = it)) },
        )
    }
    // Only while it is on: a lead for a thing that does not happen is a control with nothing to
    // do, and the tab is read top to bottom.
    if (preferences.autoLoadService) {
        PrefRow(
            title = stringResource(Res.string.calendar_auto_load_lead),
            subtitle = stringResource(
                Res.string.calendar_auto_load_lead_sub, AUTO_LOAD_LEAD_MIN, AUTO_LOAD_LEAD_MAX,
            ),
            value = preferences.autoLoadLead().toString(),
            isValid = { parseLeadMinutes(it) != null },
            onCommit = { text ->
                parseLeadMinutes(text)?.let { onChange(preferences.copy(autoLoadLeadMinutes = it)) }
            },
        )
    }
    if (cloudSync != null) {
        SettingCard {
            CardText(
                title = stringResource(Res.string.calendar_cloud_sync),
                subtitle = stringResource(Res.string.calendar_cloud_sync_sub),
            )
            RaisedSwitch(checked = cloudSync.enabled(), onCheckedChange = cloudSync.setEnabled)
        }
    }
}

/**
 * A default: its name, what it is for, and a narrow value field.
 *
 * Committed when the field is left rather than per keystroke — half of `10:00` is `10:` , which is
 * not a time, and storing every intermediate state would both fail validation and rewrite the file
 * on every character.
 */
@Composable
private fun PrefRow(
    title: String,
    subtitle: String,
    value: String,
    isValid: (String) -> Boolean,
    onCommit: (String) -> Unit,
) {
    var draft by remember(value) { mutableStateOf(value) }
    SettingCard {
        CardText(title = title, subtitle = subtitle)
        // A fresh field whenever the value is replaced from outside -- the clock format flipping
        // `10:00` to `10:00 AM` and back. The text field keeps its horizontal scroll across a
        // value change, so a shorter text arrived drawn where the longer one had been scrolled to.
        key(value) {
            CompactTextField(
                value = draft,
                onValueChange = { draft = it },
                height = PREF_FIELD_HEIGHT,
                textAlign = TextAlign.Center,
                errorBorder = !isValid(draft),
                modifier = Modifier
                    .width(PREF_FIELD)
                    .commitOnExit(draft != value) { if (isValid(draft)) onCommit(draft) else draft = value },
            )
        }
    }
}
