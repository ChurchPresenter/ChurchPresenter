package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.auto_fit_scope
import churchpresenter.composeapp.generated.resources.auto_fit_scope_slide
import churchpresenter.composeapp.generated.resources.auto_fit_scope_song
import org.churchpresenter.app.churchpresenter.composables.SegmentedButton
import org.churchpresenter.app.churchpresenter.composables.SegmentedButtonItem
import org.churchpresenter.settings.SongSettings
import org.jetbrains.compose.resources.stringResource

/** Whether Auto fits each slide on its own on [lowerThird]'s output, rather than the whole song. */
internal fun SongSettings.autoFitEachSlide(lowerThird: Boolean): Boolean =
    if (lowerThird) layoutExtras.autoFitEachSlideLowerThird else layoutExtras.autoFitEachSlide

/** The inverse of [autoFitEachSlide]. */
internal fun SongSettings.withAutoFitEachSlide(lowerThird: Boolean, value: Boolean): SongSettings =
    if (lowerThird) {
        copy(layoutExtras = layoutExtras.copy(autoFitEachSlideLowerThird = value))
    } else {
        copy(layoutExtras = layoutExtras.copy(autoFitEachSlide = value))
    }

/** Test handle for the Whole song / Each slide switch. */
internal const val AUTO_FIT_SCOPE_TAG = "song_auto_fit_scope"

/**
 * Whole song or each slide -- what Auto measures when it picks a size.
 *
 * Whole song is one size for every slide, so the text never changes size between them; each slide
 * lets a short verse grow to fill the frame and a long one shrink, up to the configured size.
 */
@Composable
internal fun AutoFitScopeControl(eachSlide: Boolean, onEachSlideChange: (Boolean) -> Unit) =
    ControlColumn(stringResource(Res.string.auto_fit_scope), labelInsideControl = true) {
        SegmentedButton(
            items = listOf(
                SegmentedButtonItem(false, stringResource(Res.string.auto_fit_scope_song)),
                SegmentedButtonItem(true, stringResource(Res.string.auto_fit_scope_slide)),
            ),
            selectedValue = eachSlide,
            onValueChange = onEachSlideChange,
            buttonWidth = SCOPE_BUTTON_WIDTH,
            buttonHeight = 30.dp,
            fontSize = MaterialTheme.typography.labelSmall.fontSize,
            modifier = Modifier.testTag(AUTO_FIT_SCOPE_TAG),
        )
    }

/** Wide enough for "Whole song" without an ellipsis. */
private val SCOPE_BUTTON_WIDTH = 86.dp
