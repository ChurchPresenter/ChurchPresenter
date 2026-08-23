package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Shape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.churchpresenter.resources.generated.resources.Res
import org.churchpresenter.resources.generated.resources.color
import org.churchpresenter.resources.generated.resources.enabled
import org.churchpresenter.resources.generated.resources.song_title_slide
import org.churchpresenter.resources.generated.resources.show_song_number_before_title
import org.churchpresenter.resources.generated.resources.title
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.ExperimentalFoundationApi
import org.churchpresenter.ui.SettingsScrollbar
import org.churchpresenter.ui.SettingsScrollbarGutter
import org.churchpresenter.ui.SettingsSection
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.ui.rememberSystemFonts
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.ui.LabeledCheckbox

private const val COLUMN_WEIGHT = 0.48f

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongSettingsTab(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    presenterManager: PresenterManager? = null
) {
    val availableFonts = rememberSystemFonts()

    val scrollState = rememberScrollState()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(end = SettingsScrollbarGutter),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            Column(
                modifier = Modifier
                    .weight(COLUMN_WEIGHT)
                    .widthIn(min = 400.dp, max = 450.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TitleSlideColumn(
                    settings = settings,
                    onSettingsChange = onSettingsChange,
                )
                LeftColumn(
                    settings = settings,
                    onSettingsChange = onSettingsChange,
                    availableFonts = availableFonts
                )
            }

            Column(
                modifier = Modifier
                    .weight(COLUMN_WEIGHT)
                    .widthIn(min = 400.dp, max = 450.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RightColumn(settings, onSettingsChange, availableFonts, presenterManager)
                LookAheadColumn(settings, onSettingsChange, availableFonts)
            }
        }
        SettingsScrollbar(scrollState)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TitleSlideColumn(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    SettingsSection(title = stringResource(Res.string.song_title_slide)) {
        Column {
            LabeledCheckbox(
                checked = settings.songSettings.titleSlideEnabled,
                onCheckedChange = { onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(titleSlideEnabled = it)) } },
                controlModifier = Modifier.size(24.dp),
                label = stringResource(Res.string.enabled),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("song_titleSlideEnabled"),
                style = MaterialTheme.typography.bodyMedium,
            )
            LabeledCheckbox(
                checked = settings.songSettings.titleSlideShowSongNumber,

                onCheckedChange = { checked ->
                    onSettingsChange { s ->
                        s.copy(songSettings = s.songSettings.copy(titleSlideShowSongNumber = checked))
                    }
                },
                enabled = settings.songSettings.titleSlideEnabled,
                controlModifier = Modifier.size(24.dp),
                label = stringResource(Res.string.show_song_number_before_title),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("song_titleSlideShowSongNumber"),
                style = MaterialTheme.typography.bodyMedium,
                color = if (settings.songSettings.titleSlideEnabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    },
            )
        }
    }
}

internal fun segmentedItemShape(index: Int, count: Int): Shape {
    val r = 4.dp
    return when {
        count == 1 -> RoundedCornerShape(r)
        index == 0 -> RoundedCornerShape(topStart = r, bottomStart = r, topEnd = 0.dp, bottomEnd = 0.dp)
        index == count - 1 -> RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = r, bottomEnd = r)
        else -> RoundedCornerShape(0.dp)
    }
}
