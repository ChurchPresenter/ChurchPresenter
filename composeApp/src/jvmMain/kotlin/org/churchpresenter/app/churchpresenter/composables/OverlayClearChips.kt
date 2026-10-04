package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.icons.generated.resources.ic_close
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.content_announcements
import org.churchpresenter.strings.generated.resources.display_lower_third
import org.churchpresenter.strings.generated.resources.overlay_take_down
import org.churchpresenter.strings.generated.resources.profile_nav_live_captions
import org.churchpresenter.theme.components.RaisedFilterChip
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** The overlays in the order their chips appear: as they stack, bottom to top. */
private val CHIP_ORDER = listOf(Presenting.STT, Presenting.LOWER_THIRD, Presenting.ANNOUNCEMENTS)

/**
 * One chip per overlay up -- captions, lower third, announcement -- each taking only that overlay
 * down when clicked, and leaving the slide under it on screen. Nothing at all while none is up.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OverlayClearChips(
    overlays: Set<Presenting>,
    onClear: (Presenting) -> Unit,
    modifier: Modifier = Modifier,
) {
    val up = CHIP_ORDER.filter { it in overlays }
    if (up.isEmpty()) return
    FlowRow(
        modifier = modifier.padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        up.forEach { overlay ->
            val name = overlayName(overlay)
            val takeDown = stringResource(Res.string.overlay_take_down, name)
            RaisedFilterChip(
                selected = true,
                onClick = { onClear(overlay) },
                label = { Text(name, style = MaterialTheme.typography.labelSmall) },
                trailingIcon = {
                    Icon(
                        painter = painterResource(IconRes.drawable.ic_close),
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                },
                modifier = Modifier
                    .testTag(overlayClearChipTag(overlay))
                    .semantics { contentDescription = takeDown },
            )
        }
    }
}

@Composable
private fun overlayName(overlay: Presenting): String = stringResource(
    when (overlay) {
        Presenting.LOWER_THIRD -> Res.string.display_lower_third
        Presenting.ANNOUNCEMENTS -> Res.string.content_announcements
        else -> Res.string.profile_nav_live_captions
    },
)

/** The chip that takes [overlay] down, for a test to find. */
internal fun overlayClearChipTag(overlay: Presenting): String = "overlay_clear_${overlay.name.lowercase()}"
