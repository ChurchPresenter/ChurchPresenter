package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.lower_third_height
import churchpresenter.composeapp.generated.resources.lower_third_band
import org.churchpresenter.app.churchpresenter.composables.NumberSettingsTextField
import org.churchpresenter.app.churchpresenter.composables.SettingsSection
import org.jetbrains.compose.resources.stringResource

/** What the band height is allowed to be, unchanged from the Projection field this replaced. */
internal val LOWER_THIRD_HEIGHT_RANGE = 10..60

/** Test tag on the field, so a UI test can find it among a tab full of numeric boxes. */
internal const val LOWER_THIRD_HEIGHT_TAG = "lowerThirdHeightField"

/**
 * How much of the output's height the band takes, for whichever content type is asking.
 *
 * One composable used by both tabs, sitting in each one's left rail directly above its text margins.
 * The rail is where a tab keeps what belongs to the slide as a whole rather than to any one element,
 * which is what this is — and putting it in the same place on both is the point: it briefly lived in
 * a different spot on each, which made two tabs that are meant to read alike read differently.
 *
 * Not gated on the lower-third target, unlike its first home in the style pane. The rail has no
 * target switch, and a control that appeared and vanished as the pane's switch moved beside it would
 * be the odder thing. It sits next to the margins for the same reason they are not gated either.
 *
 * The field's label is deliberately the string the Projection tab used: it carries a translation in
 * every locale the app ships, and a new English-only key would trade all of them for a tidier
 * phrase. The section heading above it is a new key, and could not reuse "Lower Third": that is
 * already the label of the target switch in the pane beside this rail, and two nodes reading the
 * same thing made every test that selects that target match both of them.
 */
@Composable
internal fun LowerThirdHeightSection(percent: Int, onPercentChange: (Int) -> Unit) {
    SettingsSection(title = stringResource(Res.string.lower_third_band)) {
        ControlColumn(stringResource(Res.string.lower_third_height), Modifier.fillMaxWidth()) {
            NumberSettingsTextField(
                initialText = percent,
                onValueChange = onPercentChange,
                range = LOWER_THIRD_HEIGHT_RANGE,
                modifier = Modifier.fillMaxWidth().testTag(LOWER_THIRD_HEIGHT_TAG),
            )
        }
    }
}
