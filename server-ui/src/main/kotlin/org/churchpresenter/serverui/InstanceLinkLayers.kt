package org.churchpresenter.serverui

import org.churchpresenter.strings.generated.resources.instance_link_layer_props
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.churchpresenter.settings.LinkLayers
import org.churchpresenter.sharedui.composables.LabeledCheckbox
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.instance_link_follow_layers
import org.churchpresenter.strings.generated.resources.instance_link_layer_announcements
import org.churchpresenter.strings.generated.resources.instance_link_layer_captions
import org.churchpresenter.strings.generated.resources.instance_link_layer_lower_thirds
import org.churchpresenter.strings.generated.resources.instance_link_layer_media
import org.churchpresenter.strings.generated.resources.instance_link_layer_messages
import org.churchpresenter.strings.generated.resources.instance_link_layer_slides
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Which layers of the primary's output this follower mirrors; the rest stay its own. */
@Composable
internal fun FollowedLayers(followed: List<String>, onFollow: (String, Boolean) -> Unit) {
    Text(
        stringResource(Res.string.instance_link_follow_layers),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    LinkLayers.ALL.forEach { layer ->
        LabeledCheckbox(
            checked = layer in followed,
            onCheckedChange = { onFollow(layer, it) },
            label = stringResource(linkLayerLabel(layer)),
            modifier = Modifier.fillMaxWidth().testTag(followLayerTag(layer)),
            style = MaterialTheme.typography.bodyMedium,
            spacing = 12.dp,
        )
    }
}

/** What a follower's layer choice is called in the dialog. */
private fun linkLayerLabel(layer: String): StringResource = when (layer) {
    LinkLayers.MEDIA -> Res.string.instance_link_layer_media
    LinkLayers.LOWER_THIRD -> Res.string.instance_link_layer_lower_thirds
    LinkLayers.CAPTIONS -> Res.string.instance_link_layer_captions
    LinkLayers.ANNOUNCEMENTS -> Res.string.instance_link_layer_announcements
    LinkLayers.MESSAGES -> Res.string.instance_link_layer_messages
    LinkLayers.PROPS -> Res.string.instance_link_layer_props
    else -> Res.string.instance_link_layer_slides
}

/** Test handle for the checkbox that follows [layer]. */
internal fun followLayerTag(layer: String) = "instance_link_follow_$layer"
