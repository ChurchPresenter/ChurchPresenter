package org.churchpresenter.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.message_duration
import org.churchpresenter.strings.generated.resources.message_duration_custom
import org.churchpresenter.strings.generated.resources.message_duration_hint
import org.churchpresenter.strings.generated.resources.message_insert_blank
import org.churchpresenter.strings.generated.resources.message_one_minute
import org.churchpresenter.strings.generated.resources.message_preview_empty
import org.churchpresenter.strings.generated.resources.message_seconds_short
import org.churchpresenter.theme.components.SegmentTrack
import org.churchpresenter.theme.components.SegmentTrackItem
import org.churchpresenter.theme.components.SettingsTextField
import org.jetbrains.compose.resources.stringResource

/** The blanks a message is most often written with, one click each to add to the text. */
@Composable
internal fun InsertBlank(onInsert: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(Res.string.message_insert_blank),
            fontSize = 11.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 2.dp),
        )
        COMMON_BLANKS.forEach { blank ->
            Text(
                blank,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = BLANK_FILL_ALPHA))
                    .clickable(role = Role.Button) { onInsert(blank) }
                    .padding(horizontal = 8.dp, vertical = 3.dp)
                    .testTag(messageBlankTag(blank)),
            )
        }
    }
}

internal fun messageBlankTag(blank: String) = "message_blank_$blank"

/**
 * How long the message stays up: until cleared, one of the usual lengths, or Custom -- any number
 * of seconds, typed. A saved message whose length is none of the usual ones opens on Custom.
 */
@Composable
internal fun StaysUpFor(duration: String, onChange: (String) -> Unit) {
    var customPicked by remember { mutableStateOf(false) }
    val seconds = duration.toIntOrNull()
    val custom = customPicked || (seconds != null && seconds !in DURATION_PRESETS)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SegmentTrack(modifier = Modifier.fillMaxWidth()) {
            DurationSegment(stringResource(Res.string.message_duration_hint), !custom && seconds == null) {
                customPicked = false
                onChange("")
            }
            DURATION_PRESETS.forEach { preset ->
                val label = if (preset == SECONDS_PER_MINUTE) {
                    stringResource(Res.string.message_one_minute)
                } else {
                    stringResource(Res.string.message_seconds_short, preset)
                }
                DurationSegment(label, !custom && seconds == preset) {
                    customPicked = false
                    onChange(preset.toString())
                }
            }
            DurationSegment(stringResource(Res.string.message_duration_custom), custom) { customPicked = true }
        }
        if (custom) {
            SettingsTextField(
                value = duration,
                onValueChange = { typed -> onChange(typed.filter(Char::isDigit).take(DURATION_DIGITS)) },
                label = stringResource(Res.string.message_duration),
                placeholder = { Text(stringResource(Res.string.message_duration_hint)) },
                modifier = Modifier.fillMaxWidth().testTag(MESSAGE_DURATION_TAG),
            )
        }
    }
}

@Composable
private fun RowScope.DurationSegment(label: String, selected: Boolean, onClick: () -> Unit) {
    SegmentTrackItem(selected = selected, onClick = onClick, modifier = Modifier.weight(1f).height(28.dp)) {
        Text(
            label,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = LocalContentColor.current,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

/** How the message will look over the output: [shown] on a dark band, and how long it stays up. */
@Composable
internal fun MessagePreview(shown: String, seconds: Int?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(PREVIEW_ASPECT)
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.linearGradient(PREVIEW_BACKDROP)),
    ) {
        Text(
            seconds?.let { "$it s" } ?: stringResource(Res.string.message_duration_hint),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = PREVIEW_NOTE_ALPHA),
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 10.dp),
        )
        Text(
            shown.ifEmpty { stringResource(Res.string.message_preview_empty) },
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(PREVIEW_BAND)
                .padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}

internal val COMMON_BLANKS = listOf("{number}", "{name}", "{room}")
private const val BLANK_FILL_ALPHA = 0.14f
private const val SECONDS_PER_MINUTE = 60
private val DURATION_PRESETS = listOf(5, 10, 30, SECONDS_PER_MINUTE)
private const val PREVIEW_ASPECT = 16f / 6f
private const val PREVIEW_NOTE_ALPHA = 0.6f
private val PREVIEW_BACKDROP = listOf(Color(0xFF2A1E3A), Color(0xFF1A2236), Color(0xFF33261A))
private val PREVIEW_BAND = Color(0xB80A0C10)
