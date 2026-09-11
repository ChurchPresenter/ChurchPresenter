package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.runtime.Composable
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.bible_scope_full_screen
import churchpresenter.composeapp.generated.resources.bible_scope_lower_third
import churchpresenter.composeapp.generated.resources.song_scope_title_slide
import org.churchpresenter.settings.AppSettings
import org.jetbrains.compose.resources.stringResource

/** The lower third's height is stored as a whole percentage of the output. */
private const val PERCENT = 100

/**
 * What the output being styled actually is, in its own pixels.
 *
 * Shares the Bible tab's wording and its [previewOutputSize]: both tabs are describing the same
 * physical screen, so a difference between them would only ever be a discrepancy.
 */
@Composable
internal fun songScopeNote(settings: AppSettings, target: SongStyleTarget, titleSlide: Boolean = false): String {
    val size = previewOutputSize(settings)
    val height = if (target.isLowerThird) {
        size.height * settings.songSettings.lowerThirdHeightPercent / PERCENT
    } else {
        size.height
    }
    return when {
        // The slide is drawn on whichever output is selected, so the note carries that output's
        // size: it is the only place the row still says which of the two is being styled.
        titleSlide -> stringResource(Res.string.song_scope_title_slide, size.width, height)
        target.isLowerThird -> stringResource(Res.string.bible_scope_lower_third, size.width, height)
        else -> stringResource(Res.string.bible_scope_full_screen, size.width, height)
    }
}
