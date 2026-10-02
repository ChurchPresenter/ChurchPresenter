package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.content_announcements
import org.churchpresenter.strings.generated.resources.content_bible_background
import org.churchpresenter.strings.generated.resources.media
import org.churchpresenter.strings.generated.resources.content_pictures
import org.churchpresenter.strings.generated.resources.content_songs_background
import org.churchpresenter.strings.generated.resources.display_lower_third
import org.churchpresenter.strings.generated.resources.bible
import org.churchpresenter.strings.generated.resources.songs
import org.churchpresenter.strings.generated.resources.media_subtitles
import org.churchpresenter.strings.generated.resources.output_profile_content_all_shown
import org.churchpresenter.strings.generated.resources.output_profile_content_some_shown
import org.churchpresenter.strings.generated.resources.output_profile_group_backgrounds
import org.churchpresenter.strings.generated.resources.output_profile_group_media
import org.churchpresenter.strings.generated.resources.output_profile_group_overlays
import org.churchpresenter.strings.generated.resources.output_profile_hide_all
import org.churchpresenter.strings.generated.resources.output_profile_scale
import org.churchpresenter.strings.generated.resources.output_profile_show_all
import org.churchpresenter.strings.generated.resources.output_profile_sources
import org.churchpresenter.strings.generated.resources.profile_content_scripture
import org.churchpresenter.strings.generated.resources.profile_group_placement
import org.churchpresenter.strings.generated.resources.profile_nav_live_captions
import org.churchpresenter.strings.generated.resources.profile_source_bible
import org.churchpresenter.strings.generated.resources.profile_source_songs
import org.churchpresenter.strings.generated.resources.background
import org.churchpresenter.strings.generated.resources.projection_content_lt_background
import org.churchpresenter.strings.generated.resources.profile_song_look_ahead
import org.churchpresenter.strings.generated.resources.projection_content_web
import org.churchpresenter.strings.generated.resources.stage_monitor_show_chords
import org.churchpresenter.strings.generated.resources.profile_show_transpose_controls
import org.churchpresenter.strings.generated.resources.tab_canvas
import org.churchpresenter.strings.generated.resources.tab_dictionary
import org.churchpresenter.strings.generated.resources.tab_qa
import org.churchpresenter.bible.defaultTranslationAbbreviation
import org.churchpresenter.core.models.songs.MAX_SONG_TRANSLATIONS
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.changedPaths
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.compose.resources.stringResource

/** One content switch: its caption, whether it is on, and how to write it onto a profile. */
private class ContentSwitch(
    val label: String,
    val checked: Boolean,
    val edit: (OutputProfile, Boolean) -> OutputProfile,
)

/** The page's four groups of switches, each with its caption. */
private class ContentGroups(
    val scripture: Pair<String, List<ContentSwitch>>,
    val media: Pair<String, List<ContentSwitch>>,
    val overlays: Pair<String, List<ContentSwitch>>,
    val backgrounds: Pair<String, List<ContentSwitch>>,
) {
    val all: List<ContentSwitch> get() = scripture.second + media.second + overlays.second + backgrounds.second
}

/** Scripture or songs on or off, stored as the language mode rather than as a flag of their own. */
private fun languageMode(on: Boolean): String = if (on) Constants.SONG_LANG_BOTH else Constants.SONG_LANG_OFF

/**
 * Every content switch, in the page's four groups.
 *
 * The chords switch is the stage monitor's alone -- no other display mode draws one -- and the
 * look-ahead is offered only while songs are on, since there is nothing to look ahead in otherwise.
 * Everything else is offered whatever the display mode, because `showsContentFor` is
 * display-mode-agnostic: a stage monitor obeys `showPictures`, `showQA` and the rest exactly as a
 * full screen does.
 */
@Composable
private fun contentSwitches(profile: OutputProfile): ContentGroups {
    val stageMonitor = profile.displayMode == Constants.DISPLAY_MODE_STAGE_MONITOR
    val scripture = buildList {
        add(ContentSwitch(stringResource(Res.string.bible), profile.showBible) { p, v ->
            // Keeping the language mode it had when switched back on is not possible -- off is one
            // of its values -- so on is "both", which every presenter reads as "whatever is picked".
            p.copy(bibleMode = if (v == p.showBible) p.bibleMode else languageMode(v))
        })
        add(ContentSwitch(stringResource(Res.string.songs), profile.showSongs) { p, v ->
            if (v == p.showSongs) p else p.copy(songMode = languageMode(v), songLookAhead = v && p.songLookAhead)
        })
        if (profile.showSongs) {
            add(ContentSwitch(stringResource(Res.string.profile_song_look_ahead), profile.songLookAhead) { p, v ->
                p.copy(songLookAhead = v && p.showSongs)
            })
        }
        if (stageMonitor) {
            add(ContentSwitch(stringResource(Res.string.stage_monitor_show_chords), profile.showChords) { p, v ->
                p.copy(showChords = v)
            })
            // The musicians' transpose buttons, on a Browser Source page — offered only while
            // there are chords to move.
            if (profile.showChords) {
                val label = stringResource(Res.string.profile_show_transpose_controls)
                add(ContentSwitch(label, profile.showTransposeControls) { p, v -> p.copy(showTransposeControls = v) })
            }
        }
        add(ContentSwitch(stringResource(Res.string.tab_dictionary), profile.showDictionary) { p, v ->
            p.copy(showDictionary = v)
        })
    }
    val media = listOf(
        ContentSwitch(stringResource(Res.string.content_pictures), profile.showPictures) { p, v ->
            p.copy(showPictures = v)
        },
        ContentSwitch(stringResource(Res.string.media), profile.showMedia) { p, v -> p.copy(showMedia = v) },
        // Whether video carries its subtitle overlay on this output; read by MediaPresenter.
        ContentSwitch(stringResource(Res.string.media_subtitles), profile.showSubtitles) { p, v ->
            p.copy(showSubtitles = v)
        },
        ContentSwitch(stringResource(Res.string.projection_content_web), profile.showWebsite) { p, v ->
            p.copy(showWebsite = v)
        },
        ContentSwitch(stringResource(Res.string.tab_canvas), profile.showCanvas) { p, v -> p.copy(showCanvas = v) },
        ContentSwitch(stringResource(Res.string.display_lower_third), profile.showStreaming) { p, v ->
            p.copy(showStreaming = v)
        },
    )
    val overlays = listOf(
        ContentSwitch(stringResource(Res.string.content_announcements), profile.showAnnouncements) { p, v ->
            p.copy(showAnnouncements = v)
        },
        ContentSwitch(stringResource(Res.string.tab_qa), profile.showQA) { p, v -> p.copy(showQA = v) },
        ContentSwitch(
            stringResource(Res.string.profile_nav_live_captions),
            profile.showSTT,
        ) { p, v -> p.copy(showSTT = v) },
    )
    val backgrounds = listOf(
        ContentSwitch(
            stringResource(Res.string.background),
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
    return ContentGroups(
        scripture = stringResource(Res.string.profile_content_scripture) to scripture,
        media = stringResource(Res.string.output_profile_group_media) to media,
        overlays = stringResource(Res.string.output_profile_group_overlays) to overlays,
        backgrounds = stringResource(Res.string.output_profile_group_backgrounds) to backgrounds,
    )
}

/**
 * Content & sources: what this profile's outputs draw, which Bibles and song languages, how
 * pictures and video meet the screen, and -- on a lower third -- where band-less content sits.
 *
 * Every switch is on the page at once. It used to be folded away behind a summary under the
 * profile's name, where it took the room the styling needed; it has a page of its own now, and the
 * summary line above the groups still names what is hidden.
 */
@Composable
internal fun ProfileContentPage(
    settings: AppSettings,
    profile: OutputProfile,
    onProfileChange: (OutputProfile) -> Unit,
) {
    val groups = contentSwitches(profile)
    ContentSummary(profile, groups.all, onProfileChange)
    ContentGroup("scripture", groups.scripture, profile, onProfileChange)
    SettingsGroup(
        stringResource(Res.string.output_profile_sources),
        key = "sources",
        paths = BIBLE_SOURCE_PATHS + SONG_SOURCE_PATHS,
    ) {
        SettingsRow(stringResource(Res.string.profile_source_bible), paths = BIBLE_SOURCE_PATHS) {
            BibleSourcePicker(
                profile = profile,
                stack = bibleTranslationChoices(settings),
                onProfileChange = onProfileChange,
                modifier = Modifier.width(SOURCE_PICKER_WIDTH),
            )
        }
        SettingsRow(stringResource(Res.string.profile_source_songs), paths = SONG_SOURCE_PATHS) {
            SongSourcePicker(
                profile = profile,
                languages = songLanguageChoices(settings),
                onProfileChange = onProfileChange,
                modifier = Modifier.width(SOURCE_PICKER_WIDTH),
            )
        }
    }
    ContentGroup("media", groups.media, profile, onProfileChange)
    ContentGroup("overlays", groups.overlays, profile, onProfileChange)
    // Only for what this profile shows: a screen that never draws a picture has nothing to fit.
    // Nor on a stage monitor, which always fits its slide and video into their zone.
    if (profile.displayMode != Constants.DISPLAY_MODE_STAGE_MONITOR) {
        SettingsGroup(stringResource(Res.string.output_profile_scale), key = "scale", paths = SCALE_PATHS) {
            ScaleRows(profile, onProfileChange)
        }
    }
    // A lower third only: on a full screen there is nowhere else for the content to go.
    if (profile.isLowerThird) {
        SettingsGroup(
            stringResource(Res.string.profile_group_placement),
            key = "placement",
            paths = listOf(PLACEMENTS_PATH),
        ) {
            PlacementRows(profile, onProfileChange)
        }
    }
    ContentGroup("backgrounds", groups.backgrounds, profile, onProfileChange, advanced = true)
}

private val SOURCE_PICKER_WIDTH = 260.dp

private val BIBLE_SOURCE_PATHS = listOf("bibleMode", "bibleTranslations")
private val SONG_SOURCE_PATHS = listOf("songMode", "songTranslations")
private val SCALE_PATHS = listOf("pictureScaleMode", "mediaScaleMode")

/** Where a lower third's band-less content is placed, by kind. */
internal const val PLACEMENTS_PATH = "lowerThirdPlacements"

@Composable
private fun ContentGroup(
    key: String,
    group: Pair<String, List<ContentSwitch>>,
    profile: OutputProfile,
    onProfileChange: (OutputProfile) -> Unit,
    advanced: Boolean = false,
) {
    // Where each switch is stored, found by flipping it: a switch can write more than one field
    // (Songs off takes the look-ahead with it), and only a linked profile needs to know.
    val linked = LocalProfileLink.current?.isLinked == true
    val paths = if (linked) {
        remember(profile, group.second.map { it.label }) {
            group.second.associate { it.label to changedPaths(profile, it.edit(profile, !it.checked)).toList() }
        }
    } else {
        emptyMap()
    }
    SettingsGroup(group.first, key = key, advanced = advanced, paths = paths.values.flatten().distinct()) {
        group.second.forEach { switch ->
            SettingsSwitchRow(
                label = switch.label,
                checked = switch.checked,
                onCheckedChange = { onProfileChange(switch.edit(profile, it)) },
                modifier = Modifier.testTag(contentSwitchTag(switch.label)),
                paths = paths[switch.label].orEmpty(),
            )
        }
    }
}

/** "13 of 17 shown · hidden: …", with Show all and Hide all beside it. */
@Composable
private fun ContentSummary(
    profile: OutputProfile,
    switches: List<ContentSwitch>,
    onProfileChange: (OutputProfile) -> Unit,
) {
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
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            summary,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).testTag(PROFILE_CONTENT_SUMMARY_TAG),
        )
        GroupCaptionAction(stringResource(Res.string.output_profile_show_all), onClick = {
            onProfileChange(switches.fold(profile) { p, s -> s.edit(p, true) })
        })
        GroupCaptionAction(stringResource(Res.string.output_profile_hide_all), onClick = {
            onProfileChange(switches.fold(profile) { p, s -> s.edit(p, false) })
        })
    }
}

/**
 * The configured Bible stack as pickable choices, in the order the selections index.
 *
 * Named from the stack itself rather than by opening each `.spb`; the widget omits the portion line
 * when it is blank.
 */
@Composable
internal fun bibleTranslationChoices(settings: AppSettings): List<TranslationChoiceDisplay> =
    settings.bibleSettings.translationList().map { translation ->
        val code = translation.customAbbreviation.ifBlank {
            defaultTranslationAbbreviation(title = "", fileName = translation.fileName)
        }
        TranslationChoiceDisplay(
            code = code,
            title = translation.customName.ifBlank { translation.fileName.substringBeforeLast('.') },
            portion = "",
        )
    }

/** A song's four language slots as pickable choices, named as the Song settings name them. */
@Composable
internal fun songLanguageChoices(settings: AppSettings): List<TranslationChoiceDisplay> =
    List(MAX_SONG_TRANSLATIONS) { slot ->
        TranslationChoiceDisplay(
            code = (slot + 1).toString(),
            title = songLanguageName(settings.songSettings, slot),
            portion = "",
        )
    }

/** Test handle for one content switch, by its label. */
internal fun contentSwitchTag(label: String): String = "profile_content_$label"

/** Test handle for the summary line. */
internal const val PROFILE_CONTENT_SUMMARY_TAG = "profile_content_summary"
