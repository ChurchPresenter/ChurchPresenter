package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.content_announcements
import churchpresenter.composeapp.generated.resources.content_bible_background
import churchpresenter.composeapp.generated.resources.content_media
import churchpresenter.composeapp.generated.resources.content_pictures
import churchpresenter.composeapp.generated.resources.content_songs_background
import churchpresenter.composeapp.generated.resources.content_streaming
import churchpresenter.composeapp.generated.resources.customize_bible
import churchpresenter.composeapp.generated.resources.customize_songs
import churchpresenter.composeapp.generated.resources.media_subtitles
import churchpresenter.composeapp.generated.resources.output_profile_content_all_shown
import churchpresenter.composeapp.generated.resources.output_profile_content_customize
import churchpresenter.composeapp.generated.resources.output_profile_content_done
import churchpresenter.composeapp.generated.resources.output_profile_content_on_output
import churchpresenter.composeapp.generated.resources.output_profile_content_some_shown
import churchpresenter.composeapp.generated.resources.output_profile_group_backgrounds
import churchpresenter.composeapp.generated.resources.output_profile_group_media
import churchpresenter.composeapp.generated.resources.output_profile_group_overlays
import churchpresenter.composeapp.generated.resources.output_profile_group_scripture
import churchpresenter.composeapp.generated.resources.output_profile_hide_all
import churchpresenter.composeapp.generated.resources.output_profile_show_all
import churchpresenter.composeapp.generated.resources.projection_content_background
import churchpresenter.composeapp.generated.resources.projection_content_lt_background
import churchpresenter.composeapp.generated.resources.projection_content_song_la
import churchpresenter.composeapp.generated.resources.projection_content_web
import churchpresenter.composeapp.generated.resources.stage_monitor_show_chords
import churchpresenter.composeapp.generated.resources.tab_canvas
import churchpresenter.composeapp.generated.resources.tab_dictionary
import churchpresenter.composeapp.generated.resources.tab_qa
import churchpresenter.composeapp.generated.resources.tab_stt
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.theme.components.GhostButton
import org.churchpresenter.theme.components.KeyButton
import org.churchpresenter.theme.components.RaisedCheckbox
import org.churchpresenter.theme.components.toggleRow
import org.churchpresenter.theme.elevationPalette
import org.churchpresenter.theme.sunken
import org.jetbrains.compose.resources.stringResource

/** One content switch: its caption, whether it is on, and how to write it onto a profile. */
private class ContentSwitch(
    val label: String,
    val checked: Boolean,
    val edit: (OutputProfile, Boolean) -> OutputProfile,
)

/** A titled column of [ContentSwitch]es. */
private class ContentGroup(val name: String, val switches: List<ContentSwitch>)

/** Scripture or songs on or off, stored as the language mode rather than as a flag of their own. */
private fun languageMode(on: Boolean): String = if (on) Constants.SONG_LANG_BOTH else Constants.SONG_LANG_OFF

/**
 * Every content type this profile can show, grouped the way an operator thinks about them.
 *
 * The chords switch is the stage monitor's alone -- no other display mode draws one -- and the
 * look-ahead is offered only while songs are on, since there is nothing to look ahead in otherwise.
 * Everything else is offered whatever the display mode, because `showsContentFor` is
 * display-mode-agnostic: a stage monitor obeys `showPictures`, `showQA` and the rest exactly as a
 * full screen does.
 */
@Composable
private fun contentGroups(profile: OutputProfile): List<ContentGroup> {
    val stageMonitor = profile.displayMode == Constants.DISPLAY_MODE_STAGE_MONITOR
    val scripture = buildList {
        add(ContentSwitch(stringResource(Res.string.customize_bible), profile.showBible) { p, v ->
            // Keeping the language mode it had when switched back on is not possible -- off is one
            // of its values -- so on is "both", which every presenter reads as "whatever is picked".
            p.copy(bibleMode = if (v == p.showBible) p.bibleMode else languageMode(v))
        })
        add(ContentSwitch(stringResource(Res.string.customize_songs), profile.showSongs) { p, v ->
            if (v == p.showSongs) p else p.copy(songMode = languageMode(v), songLookAhead = v && p.songLookAhead)
        })
        if (profile.showSongs) {
            add(ContentSwitch(stringResource(Res.string.projection_content_song_la), profile.songLookAhead) { p, v ->
                p.copy(songLookAhead = v && p.showSongs)
            })
        }
        if (stageMonitor) {
            add(ContentSwitch(stringResource(Res.string.stage_monitor_show_chords), profile.showChords) { p, v ->
                p.copy(showChords = v)
            })
        }
        add(ContentSwitch(stringResource(Res.string.tab_dictionary), profile.showDictionary) { p, v ->
            p.copy(showDictionary = v)
        })
    }
    val media = listOf(
        ContentSwitch(stringResource(Res.string.content_pictures), profile.showPictures) { p, v ->
            p.copy(showPictures = v)
        },
        ContentSwitch(stringResource(Res.string.content_media), profile.showMedia) { p, v -> p.copy(showMedia = v) },
        // Whether video carries its subtitle overlay on this output; read by MediaPresenter.
        ContentSwitch(stringResource(Res.string.media_subtitles), profile.showSubtitles) { p, v ->
            p.copy(showSubtitles = v)
        },
        ContentSwitch(stringResource(Res.string.projection_content_web), profile.showWebsite) { p, v ->
            p.copy(showWebsite = v)
        },
        ContentSwitch(stringResource(Res.string.tab_canvas), profile.showCanvas) { p, v -> p.copy(showCanvas = v) },
    )
    val overlays = listOf(
        ContentSwitch(stringResource(Res.string.content_streaming), profile.showStreaming) { p, v ->
            p.copy(showStreaming = v)
        },
        ContentSwitch(stringResource(Res.string.content_announcements), profile.showAnnouncements) { p, v ->
            p.copy(showAnnouncements = v)
        },
        ContentSwitch(stringResource(Res.string.tab_qa), profile.showQA) { p, v -> p.copy(showQA = v) },
        ContentSwitch(stringResource(Res.string.tab_stt), profile.showSTT) { p, v -> p.copy(showSTT = v) },
    )
    val backgrounds = listOf(
        ContentSwitch(
            stringResource(Res.string.projection_content_background),
            profile.showFullscreenBackground,
        ) { p, v -> p.copy(showFullscreenBackground = v) },
        ContentSwitch(
            stringResource(Res.string.projection_content_lt_background),
            profile.showLowerThirdBackground,
        ) { p, v -> p.copy(showLowerThirdBackground = v) },
        ContentSwitch(stringResource(Res.string.content_bible_background), profile.showBibleBackground) { p, v ->
            p.copy(showBibleBackground = v)
        },
        ContentSwitch(stringResource(Res.string.content_songs_background), profile.showSongsBackground) { p, v ->
            p.copy(showSongsBackground = v)
        },
    )
    return listOf(
        ContentGroup(stringResource(Res.string.output_profile_group_scripture), scripture),
        ContentGroup(stringResource(Res.string.output_profile_group_media), media),
        ContentGroup(stringResource(Res.string.output_profile_group_overlays), overlays),
        ContentGroup(stringResource(Res.string.output_profile_group_backgrounds), backgrounds),
    )
}

/**
 * What this profile's outputs draw: one summary line, folded open into four groups of switches.
 *
 * Folded by default because it is set once and rarely revisited, and seventeen switches open all
 * the time took the room the styling below needs every day. The summary names what is hidden, so a
 * profile that has quietly stopped showing something says so without being opened.
 */
@Composable
internal fun ProfileContentSection(
    profile: OutputProfile,
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    onProfileChange: (OutputProfile) -> Unit,
) {
    val groups = contentGroups(profile)
    val switches = groups.flatMap { it.switches }
    val hidden = switches.filterNot { it.checked }.map { it.label }
    val summary = if (hidden.isEmpty()) {
        stringResource(Res.string.output_profile_content_all_shown, switches.size)
    } else {
        stringResource(
            Res.string.output_profile_content_some_shown,
            switches.size - hidden.size,
            switches.size,
            hidden.joinToString(", "),
        )
    }
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .sunken(shape, elevationPalette())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.output_profile_content_on_output),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (open) {
                SmallGhost(stringResource(Res.string.output_profile_show_all)) {
                    setAll(profile, switches, true, onProfileChange)
                }
                SmallGhost(stringResource(Res.string.output_profile_hide_all)) {
                    setAll(profile, switches, false, onProfileChange)
                }
            }
            KeyButton(
                onClick = { onOpenChange(!open) },
                shape = RoundedCornerShape(7.dp),
                contentPadding = PaddingValues(start = 12.dp, end = 8.dp),
                modifier = Modifier.testTag(PROFILE_CONTENT_TOGGLE_TAG),
            ) {
                Text(
                    text = stringResource(
                        if (open) Res.string.output_profile_content_done
                        else Res.string.output_profile_content_customize,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        if (open) {
            ContentSwitchGroups(profile, groups, onProfileChange)
        }
    }
}

@Composable
private fun ContentSwitchGroups(
    profile: OutputProfile,
    groups: List<ContentGroup>,
    onProfileChange: (OutputProfile) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().testTag(PROFILE_CONTENT_LIST_TAG),
    ) {
        groups.forEach { group ->
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${group.switches.count { it.checked }}/${group.switches.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                group.switches.forEach { switch ->
                    ContentCheckRow(switch.label, switch.checked) { onProfileChange(switch.edit(profile, it)) }
                }
            }
        }
    }
}

/** Every switch in [switches] turned [on] -- or off -- in one write. */
private fun setAll(
    profile: OutputProfile,
    switches: List<ContentSwitch>,
    on: Boolean,
    onProfileChange: (OutputProfile) -> Unit,
) {
    onProfileChange(switches.fold(profile) { p, switch -> switch.edit(p, on) })
}

@Composable
private fun ContentCheckRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    // One toggle for the label and its box, sharing the hover, as every settings row does.
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .toggleRow(checked, onCheckedChange, interaction, role = Role.Checkbox)
            .padding(horizontal = 2.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        RaisedCheckbox(
            checked = checked,
            onCheckedChange = null,
            interactionSource = interaction,
            modifier = Modifier.size(16.dp),
            // The card these stand in is a well already, so the box's own well edge vanished into it.
            uncheckedRim = MaterialTheme.colorScheme.outline,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (checked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SmallGhost(label: String, onClick: () -> Unit) {
    GhostButton(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}

/** Test handle for the button that folds the content switches open and shut. */
internal const val PROFILE_CONTENT_TOGGLE_TAG = "profile_content_toggle"

/** Test handle for the open list of content switches. */
internal const val PROFILE_CONTENT_LIST_TAG = "profile_content_list"
