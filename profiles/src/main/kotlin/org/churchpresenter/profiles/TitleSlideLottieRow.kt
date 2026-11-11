package org.churchpresenter.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.churchpresenter.settings.SettingsManager
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.titleSlideLottie
import org.churchpresenter.settings.withTitleSlideLottie
import org.churchpresenter.sharedui.filechooser.FileChooser
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.title_slide_animation
import org.churchpresenter.strings.generated.resources.title_slide_animation_browse
import org.churchpresenter.strings.generated.resources.title_slide_animation_files
import org.churchpresenter.strings.generated.resources.title_slide_animation_sub
import org.churchpresenter.theme.components.GhostButton
import org.jetbrains.compose.resources.stringResource
import java.io.File
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.io.path.Path

/**
 * The Lottie template this output's title slide plays (#971): one of the band templates, or any
 * Lottie file browsed to. None draws the title slide as it always was.
 */
@Composable
internal fun TitleSlideLottieRow(
    song: SongSettings,
    lowerThird: Boolean,
    updateSong: ((SongSettings) -> SongSettings) -> Unit,
    /** The folder the band templates are offered from. */
    templatesDir: File? = remember { SettingsManager.bibleLowerThirdsDir() },
    /** Asks for a file; the native chooser unless a test answers. */
    browse: suspend (filterLabel: String) -> String? = ::browseForLottie,
) {
    val path = song.titleSlideLottie(lowerThird)
    val scope = rememberCoroutineScope()
    val filterLabel = stringResource(Res.string.title_slide_animation_files)
    val setPath: (String) -> Unit = { picked -> updateSong { it.withTitleSlideLottie(lowerThird, picked) } }
    SettingsRow(
        Res.string.title_slide_animation,
        sub = stringResource(Res.string.title_slide_animation_sub),
        paths = listOf(titleSlideLottiePath(lowerThird)),
    ) {
        Row(
            modifier = Modifier.width(SOURCE_FIELD_WIDTH + BROWSE_BUTTON_ROOM),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            LottieBandPickerRow(
                path = path,
                onPathChange = setPath,
                templatesDir = templatesDir,
                onGenerate = null,
                modifier = Modifier.weight(1f).testTag(TITLE_SLIDE_LOTTIE_PICKER_TAG),
            )
            GhostButton(
                onClick = { scope.launch { browse(filterLabel)?.let(setPath) } },
                modifier = Modifier.testTag(TITLE_SLIDE_LOTTIE_BROWSE_TAG),
            ) {
                Text(
                    stringResource(Res.string.title_slide_animation_browse),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

/** Where the title slide's template is stored for this output, as the profile's change list names it. */
internal fun titleSlideLottiePath(lowerThird: Boolean): String =
    "songSettings.layoutExtras." + if (lowerThird) "titleSlideLottieLowerThird" else "titleSlideLottie"

private suspend fun browseForLottie(filterLabel: String): String? =
    FileChooser.platformInstance.chooseSingle(
        path = SettingsManager.bibleLowerThirdsDir().takeIf(File::isDirectory)?.toPath()
            ?: Path(System.getProperty("user.home")),
        filters = listOf(FileNameExtensionFilter(filterLabel, "json")),
        title = "",
        selectDirectory = false,
    )?.toString()

/** What the Browse button takes beside the template dropdown. */
private val BROWSE_BUTTON_ROOM = 96.dp

internal const val TITLE_SLIDE_LOTTIE_PICKER_TAG = "profile_title_slide_lottie"
internal const val TITLE_SLIDE_LOTTIE_BROWSE_TAG = "profile_title_slide_lottie_browse"
