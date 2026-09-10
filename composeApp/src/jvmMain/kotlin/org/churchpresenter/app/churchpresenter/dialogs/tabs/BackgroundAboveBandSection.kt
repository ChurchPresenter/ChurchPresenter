/*
 * The wash above a lower-third band: the Background tab's editor column, one rectangle up.
 *
 * Its own file because `BackgroundSettingsControls.kt` sits at detekt's function ceiling, and
 * because this is a subject of its own — the band and what is above it are two decisions an
 * operator makes separately, even though they are made with the same controls.
 */
package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.background_above_band_caption
import churchpresenter.composeapp.generated.resources.background_above_band_fill
import churchpresenter.composeapp.generated.resources.background_above_band_opacity
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.compose.resources.stringResource

/**
 * The wash over the two thirds a band does not cover.
 *
 * Built out of the band's own parts on purpose: the same segmented type row, drawn by
 * [BackgroundTypeRow] and labelled from [backgroundTypeLabel], and the same
 * [BackgroundColorPicker] — field, swatches and all — under it. Applying a color above the band is
 * the same act as applying one to the band, so it is the same control, and `Default` there means
 * what `Default` means one row up.
 *
 * The list it is handed is the short one: no picture, no clip, no camera, no gradient. See
 * [aboveBandTypeOptions] for which surface offers what, and why the Default Lower Third has no
 * `Default` of its own.
 *
 * Captioned "Fill Color" and "Fill Opacity" rather than the plain words the band's controls use:
 * both sets are in one scrolling column, and a second "COLOR" or "OPACITY" there names nothing an
 * operator could tell from the first.
 */
@Composable
internal fun AboveBandSection(
    scope: BackgroundScope,
    config: BackgroundConfig,
    onConfigChange: (BackgroundConfig) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
        BackgroundTypeRow(
            caption = stringResource(Res.string.background_above_band_caption),
            options = scope.aboveBandTypeOptions(),
            selected = config.aboveBandType,
            onSelect = { onConfigChange(config.copy(aboveBandType = it)) },
        )
        if (config.aboveBandType == Constants.BACKGROUND_COLOR) {
            BackgroundColorPicker(
                caption = stringResource(Res.string.background_above_band_fill),
                color = config.aboveBandColor,
                onColorChange = { onConfigChange(config.copy(aboveBandColor = it)) },
            )
            CaptionedSlider(
                caption = stringResource(Res.string.background_above_band_opacity),
                readout = percentReadout(config.aboveBandOpacity),
                value = config.aboveBandOpacity,
                onValueChange = { onConfigChange(config.copy(aboveBandOpacity = it)) }
            )
        }
    }
}
