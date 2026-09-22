package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.content_region
import churchpresenter.composeapp.generated.resources.content_region_width
import churchpresenter.composeapp.generated.resources.content_region_x_offset
import churchpresenter.composeapp.generated.resources.content_region_y_offset
import org.churchpresenter.app.churchpresenter.composables.SettingsSection
import org.churchpresenter.app.churchpresenter.composables.SliderNumberField
import org.churchpresenter.settings.ContentRegion
import org.jetbrains.compose.resources.stringResource

private val OFFSET_FIELD_WIDTH = 56.dp

/**
 * Width, and X/Y offset from the centerline, for a presenter's whole content block.
 *
 * One composable shared by the Bible and Song tabs, the same way [LowerThirdHeightSection] is —
 * see its own doc comment for why a control that belongs to the slide as a whole rather than to any
 * one element sits in the same place on both.
 *
 * Each row is a [SliderNumberField] rather than a bare text field -- the offsets accept negative
 * values (to push the block toward the opposite edge), which a plain number box gives no visual
 * hint of; the slider's track and handle make the full negative-to-positive range, and where the
 * current value sits in it, obvious without having to already know to type a minus sign.
 */
@Composable
internal fun ContentRegionSection(region: ContentRegion, onRegionChange: (ContentRegion) -> Unit) {
    SettingsSection(title = stringResource(Res.string.content_region)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ControlColumn(stringResource(Res.string.content_region_width), Modifier.fillMaxWidth()) {
                SliderNumberField(
                    value = region.widthPercent,
                    range = ContentRegion.WIDTH_RANGE,
                    onValueChange = { value -> onRegionChange(region.copy(widthPercent = value)) },
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ControlColumn(stringResource(Res.string.content_region_x_offset), Modifier.weight(1f)) {
                    SliderNumberField(
                        value = region.xOffsetPercent,
                        range = ContentRegion.OFFSET_RANGE,
                        onValueChange = { value -> onRegionChange(region.copy(xOffsetPercent = value)) },
                        fieldWidth = OFFSET_FIELD_WIDTH,
                    )
                }
                ControlColumn(stringResource(Res.string.content_region_y_offset), Modifier.weight(1f)) {
                    SliderNumberField(
                        value = region.yOffsetPercent,
                        range = ContentRegion.OFFSET_RANGE,
                        onValueChange = { value -> onRegionChange(region.copy(yOffsetPercent = value)) },
                        fieldWidth = OFFSET_FIELD_WIDTH,
                    )
                }
            }
        }
    }
}
