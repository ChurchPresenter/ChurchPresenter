package org.churchpresenter.songs

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.core.models.songs.SongTextOverrides
import org.churchpresenter.icons.generated.resources.ic_add
import org.churchpresenter.icons.generated.resources.ic_close
import org.churchpresenter.icons.generated.resources.ic_remove
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.songSettingsFor
import org.churchpresenter.sharedui.composables.ColorPickerField
import org.churchpresenter.sharedui.composables.FontSettingsDropdown
import org.churchpresenter.sharedui.utils.rememberSystemFonts
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.song_look_color
import org.churchpresenter.strings.generated.resources.song_look_font
import org.churchpresenter.strings.generated.resources.song_look_size
import org.churchpresenter.strings.generated.resources.song_look_size_down
import org.churchpresenter.strings.generated.resources.song_look_size_up
import org.churchpresenter.strings.generated.resources.song_look_sub
import org.churchpresenter.strings.generated.resources.song_look_title
import org.churchpresenter.strings.generated.resources.song_look_use_profile
import org.churchpresenter.strings.generated.resources.song_look_voice2
import org.churchpresenter.theme.AppShape
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.icons.generated.resources.Res as IconRes

/** Test handles for the song look card's controls. */
internal const val SONG_LOOK_SIZE_TAG = "song_look_size"
internal const val SONG_LOOK_RESET_TAG = "song_look_reset_"

private const val SIZE_STEP = 10
private val CardShape = AppShape(9.dp)
private val LABEL_WIDTH = 96.dp
private val FIELD_WIDTH = 190.dp
private val FIELD_HEIGHT = 30.dp

/**
 * This song's own font, size and colours over the profile's — written into the song file, so the
 * song looks this way on every output and on every machine it is copied to. A row that shows the
 * profile's value is following it; changing it makes it the song's, and its × hands it back.
 */
@Composable
internal fun SongLookCard(state: EditSongState, appSettings: AppSettings, profile: OutputProfile?) {
    val song = profile?.let { appSettings.songSettingsFor(it) } ?: appSettings.songSettings
    val lowerThird = profile?.isLowerThird == true
    val own = state.textOverrides
    fun edit(change: (SongTextOverrides) -> SongTextOverrides) {
        state.textOverrides = change(state.textOverrides)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CardShape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(Res.string.song_look_title),
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(Res.string.song_look_sub),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LookRow(Res.string.song_look_font, custom = own.font.isNotBlank(), onReset = { edit { it.copy(font = "") } }, key = "font") {
            FontSettingsDropdown(
                value = own.font.ifBlank { if (lowerThird) song.lyricsLowerThirdFontType else song.lyricsFontType },
                fonts = rememberSystemFonts(),
                onValueChange = { v -> edit { it.copy(font = v) } },
                modifier = Modifier.width(FIELD_WIDTH).height(FIELD_HEIGHT),
                fillWidth = true,
            )
        }
        LookRow(
            Res.string.song_look_size,
            custom = own.sizePercent != SongTextOverrides.FULL_SIZE_PERCENT,
            onReset = { edit { it.copy(sizePercent = SongTextOverrides.FULL_SIZE_PERCENT) } },
            key = "size",
        ) {
            SizeStepper(own.sizePercent) { v -> edit { it.copy(sizePercent = v) } }
        }
        LookRow(Res.string.song_look_color, custom = own.color.isNotBlank(), onReset = { edit { it.copy(color = "") } }, key = "color") {
            ColorPickerField(
                color = own.color.ifBlank { if (lowerThird) song.lyricsLowerThirdColor else song.lyricsColor },
                onColorChange = { v -> edit { it.copy(color = v) } },
                modifier = Modifier.width(FIELD_WIDTH).height(FIELD_HEIGHT),
            )
        }
        LookRow(
            Res.string.song_look_voice2,
            custom = own.secondVoiceColor.isNotBlank(),
            onReset = { edit { it.copy(secondVoiceColor = "") } },
            key = "voice2",
        ) {
            ColorPickerField(
                color = own.secondVoiceColor.ifBlank { song.layoutExtras.secondVoiceColor },
                onColorChange = { v -> edit { it.copy(secondVoiceColor = v) } },
                modifier = Modifier.width(FIELD_WIDTH).height(FIELD_HEIGHT),
            )
        }
    }
}

/** One override: its name, its control, and — while the song has its own value — the × that drops it. */
@Composable
private fun LookRow(
    label: StringResource,
    custom: Boolean,
    onReset: () -> Unit,
    key: String,
    control: @Composable () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (custom) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.width(LABEL_WIDTH),
        )
        control()
        Box(Modifier.size(28.dp)) {
            if (custom) {
                IconButton(onClick = onReset, modifier = Modifier.size(28.dp).testTag(SONG_LOOK_RESET_TAG + key)) {
                    Icon(
                        painter = painterResource(IconRes.drawable.ic_close),
                        contentDescription = stringResource(Res.string.song_look_use_profile),
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

/** The size as a percentage of the profile's, a step at a time within the range a song may take. */
@Composable
private fun SizeStepper(percent: Int, onChange: (Int) -> Unit) {
    val range = SongTextOverrides.MIN_SIZE_PERCENT..SongTextOverrides.MAX_SIZE_PERCENT
    Row(
        modifier = Modifier.width(FIELD_WIDTH).testTag(SONG_LOOK_SIZE_TAG),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = { onChange((percent - SIZE_STEP).coerceIn(range)) },
            enabled = percent > range.first,
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                painterResource(IconRes.drawable.ic_remove),
                contentDescription = stringResource(Res.string.song_look_size_down),
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = "$percent%",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        IconButton(
            onClick = { onChange((percent + SIZE_STEP).coerceIn(range)) },
            enabled = percent < range.last,
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                painterResource(IconRes.drawable.ic_add),
                contentDescription = stringResource(Res.string.song_look_size_up),
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
