package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.ic_check
import churchpresenter.composeapp.generated.resources.media_subtitles_all_outputs
import churchpresenter.composeapp.generated.resources.media_subtitles_embedded
import churchpresenter.composeapp.generated.resources.media_subtitles_off
import churchpresenter.composeapp.generated.resources.media_subtitles_show_on
import org.churchpresenter.app.churchpresenter.viewmodel.MediaViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.SidecarSubtitle
import org.churchpresenter.settings.OutputProfile
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val CHECK_SIZE = 14.dp
private val ROUTE_INDENT = 18.dp

/**
 * The Media tab's Subtitles menu: what is loaded, what is on, and which outputs each one goes to.
 *
 * Routing lives here rather than in a profile's settings because the tracks are a property of the
 * video that is loaded, not of the install -- a profile has nothing stable to remember between one
 * clip and the next. The profile decides only whether it draws subtitles at all, and how they look.
 *
 * Only the app-drawn files can be routed. An embedded track is burned into the one frame every
 * output shares, so it is necessarily the same on all of them, and stays a single choice.
 */
@Composable
internal fun SubtitleMenuItems(
    viewModel: MediaViewModel,
    profiles: List<OutputProfile>,
    onLoadFile: () -> Unit,
    loadFileLabel: String,
) {
    // Which track's routing list is open, or -1. One at a time: the menu is already a popup, and a
    // nested popup per row cannot be driven by a test or reliably dismissed.
    var routingOpen by remember { mutableIntStateOf(-1) }

    DropdownMenuItem(
        text = { Text(stringResource(Res.string.media_subtitles_off)) },
        onClick = { viewModel.turnSubtitlesOff() },
        trailingIcon = { if (!viewModel.subtitlesVisible) CheckMark() },
        modifier = Modifier.testTag(SUBTITLE_OFF_TAG),
    )

    viewModel.sidecarSubtitles.forEachIndexed { index, track ->
        SidecarRow(
            index = index,
            track = track,
            routingOpen = routingOpen == index,
            onToggle = { viewModel.setSidecarEnabled(index, !track.enabled) },
            onToggleRouting = { routingOpen = if (routingOpen == index) -1 else index },
        )
        if (routingOpen == index) {
            RoutingRows(
                track = track,
                profiles = profiles,
                onOutputs = { viewModel.setSidecarOutputs(index, it) },
            )
        }
    }

    val embedded = viewModel.subtitleTracks
    if (embedded.isNotEmpty()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text(
                text = stringResource(Res.string.media_subtitles_embedded),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        embedded.forEach { embeddedTrack ->
            DropdownMenuItem(
                text = { Text(embeddedTrack.name) },
                onClick = { viewModel.selectSubtitleTrack(embeddedTrack.id) },
                trailingIcon = { if (viewModel.selectedSubtitleTrack == embeddedTrack.id) CheckMark() },
            )
        }
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    DropdownMenuItem(
        text = { Text(loadFileLabel) },
        onClick = onLoadFile,
        modifier = Modifier.testTag(SUBTITLE_LOAD_TAG),
    )
}

/** One loaded file: a tick that turns it on or off, and the handle that opens its routing. */
@Composable
private fun SidecarRow(
    index: Int,
    track: SidecarSubtitle,
    routingOpen: Boolean,
    onToggle: () -> Unit,
    onToggleRouting: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = track.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(Res.string.media_subtitles_show_on),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (routingOpen) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .testTag(subtitleShowOnTag(index))
                        .clickable(onClick = onToggleRouting)
                        .padding(horizontal = 4.dp),
                )
            }
        },
        onClick = onToggle,
        trailingIcon = { if (track.enabled) CheckMark() },
        modifier = Modifier.testTag(subtitleTrackTag(index)),
    )
}

/** The outputs one track is drawn on: all of them, or a named set. */
@Composable
private fun RoutingRows(
    track: SidecarSubtitle,
    profiles: List<OutputProfile>,
    onOutputs: (Set<String>) -> Unit,
) {
    DropdownMenuItem(
        text = { Text(stringResource(Res.string.media_subtitles_all_outputs)) },
        onClick = { onOutputs(emptySet()) },
        trailingIcon = { if (track.outputs.isEmpty()) CheckMark() },
        modifier = Modifier.padding(start = ROUTE_INDENT).testTag(SUBTITLE_ROUTE_ALL_TAG),
    )
    profiles.forEach { profile ->
        val on = profile.id in track.outputs
        DropdownMenuItem(
            text = { Text(profile.name) },
            onClick = {
                // Empty means every output, so unticking the last named one falls back to that
                // rather than leaving a track routed nowhere and silently invisible.
                onOutputs(if (on) track.outputs - profile.id else track.outputs + profile.id)
            },
            trailingIcon = { if (on) CheckMark() },
            modifier = Modifier.padding(start = ROUTE_INDENT).testTag(subtitleRouteTag(profile.id)),
        )
    }
}

@Composable
private fun CheckMark() {
    Icon(painterResource(Res.drawable.ic_check), null, Modifier.size(CHECK_SIZE))
}

/** Test handle for the Off row. */
internal const val SUBTITLE_OFF_TAG = "media_subtitle_off"

/** Test handle for the "load a file" row. */
internal const val SUBTITLE_LOAD_TAG = "media_subtitle_load"

/** Test handle for the [index]th loaded subtitle file. */
internal fun subtitleTrackTag(index: Int): String = "media_subtitle_track_$index"

/** Test handle for the [index]th file's "show on" handle. */
internal fun subtitleShowOnTag(index: Int): String = "media_subtitle_show_on_$index"

/** Test handle for the "every output" routing row. */
internal const val SUBTITLE_ROUTE_ALL_TAG = "media_subtitle_route_all"

/** Test handle for the routing row of the profile with id [profileId]. */
internal fun subtitleRouteTag(profileId: String): String = "media_subtitle_route_$profileId"
