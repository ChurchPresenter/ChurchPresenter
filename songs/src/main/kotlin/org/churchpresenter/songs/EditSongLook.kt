package org.churchpresenter.songs

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.churchpresenter.core.models.songs.SongTextOverrides
import org.churchpresenter.icons.generated.resources.ic_close
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.songSettingsFor
import org.churchpresenter.sharedui.utils.rememberSystemFonts
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.song_look_color
import org.churchpresenter.strings.generated.resources.song_look_font
import org.churchpresenter.strings.generated.resources.song_look_size
import org.churchpresenter.strings.generated.resources.song_look_sub
import org.churchpresenter.strings.generated.resources.song_look_title
import org.churchpresenter.strings.generated.resources.song_look_use_profile
import org.churchpresenter.strings.generated.resources.song_look_voice2
import org.churchpresenter.strings.generated.resources.song_look_voice3
import org.churchpresenter.theme.AppShape
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.hosanna.composables.button.IconButton
import org.churchpresenter.hosanna.composables.feedback.Tooltip
import org.churchpresenter.hosanna.composables.field.UnitField
import org.churchpresenter.hosanna.composables.model.ButtonHierarchy
import org.churchpresenter.hosanna.composables.model.ButtonIntent
import org.churchpresenter.hosanna.composables.model.IconButtonSize
import org.churchpresenter.hosanna.composables.model.NumberRange
import org.churchpresenter.hosanna.composables.field.ColorField
import org.churchpresenter.hosanna.composables.model.ColorFieldLabels
import org.churchpresenter.sharedui.utils.Utils
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.remember
import kotlin.math.roundToInt
import org.churchpresenter.strings.generated.resources.color_field_pick
import org.churchpresenter.strings.generated.resources.color_field_theme
import org.churchpresenter.strings.generated.resources.color_field_recent
import org.churchpresenter.strings.generated.resources.color_field_invalid
import org.churchpresenter.strings.generated.resources.none
import org.churchpresenter.strings.generated.resources.customize_background_opacity
import org.churchpresenter.hosanna.composables.field.Dropdown
import org.churchpresenter.hosanna.composables.model.MenuEntry
import org.churchpresenter.hosanna.composables.model.MenuSearch
import org.churchpresenter.strings.generated.resources.search
import org.churchpresenter.strings.generated.resources.search_clear
import org.churchpresenter.strings.generated.resources.font_search_none

/** Test handles for the song look card's controls. */
internal const val SONG_LOOK_SIZE_TAG = "song_look_size"
internal const val SONG_LOOK_RESET_TAG = "song_look_reset_"

private const val SIZE_STEP = 10
private val CardShape = AppShape(9.dp)
private val LABEL_WIDTH = 96.dp

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
        LookRow(
            Res.string.song_look_font,
            custom = own.font.isNotBlank(),
            onReset = { edit { it.copy(font = "") } },
            key = "font",
        ) {
            val fonts = rememberSystemFonts()
            val profileFont = if (lowerThird) song.lyricsLowerThirdFontType else song.lyricsFontType
            Dropdown(
                entries = fonts.map { MenuEntry.Item(it, it) },
                selectedKey = own.font.ifBlank { profileFont },
                onSelect = { v -> edit { it.copy(font = v) } },
                modifier = Modifier.fillMaxWidth(),
                search = MenuSearch(
                    stringResource(Res.string.search),
                    stringResource(Res.string.search_clear),
                    stringResource(Res.string.font_search_none),
                ),
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
        LookRow(
            Res.string.song_look_color,
            custom = own.color.isNotBlank(),
            onReset = { edit { it.copy(color = "") } },
            key = "color",
        ) {
            SongColorField(
                color = own.color.ifBlank { if (lowerThird) song.lyricsLowerThirdColor else song.lyricsColor },
                onColorChange = { v -> edit { it.copy(color = v) } },
                label = stringResource(Res.string.song_look_color),
            )
        }
        LookRow(
            Res.string.song_look_voice2,
            custom = own.secondVoiceColor.isNotBlank(),
            onReset = { edit { it.copy(secondVoiceColor = "") } },
            key = "voice2",
        ) {
            SongColorField(
                color = own.secondVoiceColor.ifBlank { song.layoutExtras.secondVoiceColor },
                onColorChange = { v -> edit { it.copy(secondVoiceColor = v) } },
                label = stringResource(Res.string.song_look_voice2),
            )
        }
        LookRow(
            Res.string.song_look_voice3,
            custom = own.thirdVoiceColor.isNotBlank(),
            onReset = { edit { it.copy(thirdVoiceColor = "") } },
            key = "voice3",
        ) {
            SongColorField(
                color = own.thirdVoiceColor.ifBlank { song.layoutExtras.thirdVoiceColor },
                onColorChange = { v -> edit { it.copy(thirdVoiceColor = v) } },
                label = stringResource(Res.string.song_look_voice3),
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (custom) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.width(LABEL_WIDTH),
        )
        Box(Modifier.weight(1f)) { control() }
        Box(Modifier.size(28.dp)) {
            if (custom) {
                val reset = stringResource(Res.string.song_look_use_profile)
                Tooltip(reset) {
                    IconButton(
                        painterResource(IconRes.drawable.ic_close),
                        reset,
                        onReset,
                        Modifier.testTag(SONG_LOOK_RESET_TAG + key),
                        size = IconButtonSize.Small,
                        hierarchy = ButtonHierarchy.Tertiary,
                        intent = ButtonIntent.Neutral,
                    )
                }
            }
        }
    }
}

/** The size as a percentage of the profile's, a step at a time within the range a song may take. */
@Composable
private fun SizeStepper(percent: Int, onChange: (Int) -> Unit) {
    UnitField(
        value = percent.toDouble(),
        onValueChange = { onChange(it.toInt()) },
        unit = "%",
        range = NumberRange(
            SongTextOverrides.MIN_SIZE_PERCENT.toDouble(),
            SongTextOverrides.MAX_SIZE_PERCENT.toDouble(),
            SIZE_STEP.toDouble(),
        ),
        modifier = Modifier.fillMaxWidth().testTag(SONG_LOOK_SIZE_TAG),
    )
}

/** A color override, kept as the "#RRGGBB" text the song stores. */
@Composable
private fun SongColorField(color: String, onColorChange: (String) -> Unit, label: String) {
    ColorField(
        color = remember(color) { Utils.parseHexColor(color) },
        onColorChange = { picked -> picked?.let { onColorChange(it.toHexString()) } },
        label = label,
        modifier = Modifier.fillMaxWidth(),
        labels = ColorFieldLabels(
            pick = stringResource(Res.string.color_field_pick),
            none = stringResource(Res.string.none),
            opacity = stringResource(Res.string.customize_background_opacity),
            theme = stringResource(Res.string.color_field_theme),
            recent = stringResource(Res.string.color_field_recent),
            invalid = stringResource(Res.string.color_field_invalid),
        ),
    )
}

private fun Color.toHexString(): String = "#%02X%02X%02X".format(
    (red * COLOR_CHANNEL_MAX).roundToInt(),
    (green * COLOR_CHANNEL_MAX).roundToInt(),
    (blue * COLOR_CHANNEL_MAX).roundToInt(),
)

private const val COLOR_CHANNEL_MAX = 255f
