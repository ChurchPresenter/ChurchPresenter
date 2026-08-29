package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.runtime.Composable
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.bible_scope_full_screen
import churchpresenter.composeapp.generated.resources.bible_scope_lower_third
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
internal fun songScopeNote(settings: AppSettings, target: SongStyleTarget): String {
    val size = previewOutputSize(settings)
    return if (target.isLowerThird) {
        stringResource(
            Res.string.bible_scope_lower_third,
            size.width,
            size.height * settings.songSettings.lowerThirdHeightPercent / PERCENT,
        )
    } else {
        stringResource(Res.string.bible_scope_full_screen, size.width, size.height)
    }
}
