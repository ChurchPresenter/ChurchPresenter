package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.content_bible_translations_enabled
import churchpresenter.composeapp.generated.resources.content_bible_translations_footer
import churchpresenter.composeapp.generated.resources.content_bible_translations_header
import churchpresenter.composeapp.generated.resources.content_outputs_clear_all
import churchpresenter.composeapp.generated.resources.content_outputs_done
import churchpresenter.composeapp.generated.resources.content_outputs_enabled_subtitle
import churchpresenter.composeapp.generated.resources.content_outputs_preview
import churchpresenter.composeapp.generated.resources.content_outputs_preview_empty
import churchpresenter.composeapp.generated.resources.content_outputs_preview_translations
import churchpresenter.composeapp.generated.resources.content_outputs_quick_select
import churchpresenter.composeapp.generated.resources.content_outputs_section_backgrounds
import churchpresenter.composeapp.generated.resources.content_outputs_section_content
import churchpresenter.composeapp.generated.resources.content_outputs_select_all
import churchpresenter.composeapp.generated.resources.content_song_languages_enabled
import churchpresenter.composeapp.generated.resources.content_song_languages_footer
import churchpresenter.composeapp.generated.resources.content_song_languages_header
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.compose.resources.stringResource

/**
 * The per-output content controls of the Projection settings tab: the toggle grid, the Bible and
 * song language cells, the monitor preview and the "content outputs" dialog.
 *
 * Split out of ProjectionSettingsTab.kt, which was 2,638 lines. These are `internal` rather than
 * file-private only because Kotlin scopes `private` to the file -- they remain implementation
 * detail of the tab and are covered through it, not photographed on their own.
 */

/**
 * Count of enabled content types for the "N of M enabled" summary: Bible and Songs count when their
 * language mode isn't Off, plus every boolean content/background toggle that's on.
 */
internal fun contentOutputsEnabledCount(
    a: ScreenAssignment,
    contentGroup: List<ContentCol>,
    backgroundGroup: List<ContentCol>
): Int {
    var n = 0
    if (a.bibleMode != Constants.SONG_LANG_OFF) n++
    if (a.songMode != Constants.SONG_LANG_OFF) n++
    (contentGroup + backgroundGroup).offeredTo(a).forEach { if (it.getter(a)) n++ }
    return n
}

/** Denominator for the same summary: Bible and Songs plus every toggle [a] is offered. */
internal fun contentOutputsTotalCount(
    a: ScreenAssignment,
    contentGroup: List<ContentCol>,
    backgroundGroup: List<ContentCol>
): Int = 2 + contentGroup.offeredTo(a).size + backgroundGroup.offeredTo(a).size

/**
 * The columns [a] can actually obey -- see [ContentCol.visible].
 *
 * Every reading of the group goes through this, so the dialog, its Select All / Clear All, its
 * preview and the row's "N of M enabled" button all describe the same set. A column filtered out
 * here keeps whatever it was last set to; nothing writes to it while it is out of scope.
 */
internal fun List<ContentCol>.offeredTo(a: ScreenAssignment): List<ContentCol> = filter { it.visible(a) }

@Composable
internal fun ContentOutputsSectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        modifier = modifier,
    )
}

/**
 * Monitor mock summarising what an output actually shows: every enabled content type (and the
 * Bible/Songs language mode) is drawn as a chip inside a 16:9 screen, so the operator can read the
 * result at a glance instead of scanning a long checkbox list.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ContentOutputsMonitorPreview(
    modifier: Modifier,
    screenLabel: String,
    assignment: ScreenAssignment,
    contentGroup: List<ContentCol>,
    backgroundGroup: List<ContentCol>,
    bibleLabel: String,
    songsLabel: String,
    translationNames: List<String>,
    songLanguageChoices: List<TranslationChoiceDisplay>,
) {
    val bibleListFormat = stringResource(Res.string.content_outputs_preview_translations)
    val chips = buildList {
        if (assignment.showBible) {
            // An empty selection means every translation, so the whole stack is shown.
            val shownNames = if (assignment.bibleTranslations.isEmpty()) translationNames
                              else assignment.bibleTranslations.filter { it in translationNames.indices }.map { translationNames[it] }
            add(
                if (translationNames.size > 1 && shownNames.isNotEmpty())
                    bibleListFormat.format(bibleLabel, shownNames.joinToString(", "))
                else bibleLabel
            )
        }
        if (assignment.showSongs) {
            // The same rule the Bible chip above uses: an empty selection is every language.
            val names = songLanguageChoices.map { it.title }
            val shownNames = assignment.songTranslations
                .filter { it in names.indices }
                .map { names[it] }
                .ifEmpty { if (assignment.songTranslations.isEmpty()) names else emptyList() }
            add(
                if (names.size > 1 && shownNames.isNotEmpty())
                    bibleListFormat.format(songsLabel, shownNames.joinToString(", "))
                else songsLabel
            )
        }
        (contentGroup + backgroundGroup).offeredTo(assignment)
            .forEach { if (it.getter(assignment)) add(it.label) }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ContentOutputsSectionHeader(stringResource(Res.string.content_outputs_preview))
        Spacer(modifier = Modifier.height(8.dp))
        // Bezel
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .padding(6.dp)
        ) {
            // 16:9 is the MINIMUM height — with many content types enabled the chips need more
            // room, and a hard aspectRatio would clip them out of sight.
            val screenMinHeight = maxWidth * 9f / 16f
            // Screen
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = screenMinHeight)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black)
                    .padding(8.dp)
            ) {
                Text(
                    text = screenLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(6.dp))
                if (chips.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.content_outputs_preview_empty),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.4f)
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        chips.forEach { chip ->
                            Text(
                                text = chip,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
        // Stand
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(8.dp)
                .background(MaterialTheme.colorScheme.outline)
        )
        Box(
            modifier = Modifier
                .width(88.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.outline)
        )
    }
}

/** Renders one content/background toggle, applying the Web-on-DeckLink / Web-snapshot tooltip rules. */
@Composable
internal fun ContentOutputsToggle(
    modifier: Modifier,
    col: ContentCol,
    assignment: ScreenAssignment,
    isBrowserSource: Boolean,
    webDeckLinkTooltip: String,
    webSnapshotTooltip: String,
    onApply: (ScreenAssignment) -> Unit,
) {
    val isWeb = col.isWeb
    val webDisabledOnDeckLink = !isBrowserSource && isWeb && assignment.targetType == "decklink"
    val enabled = col.enabled(assignment) && !webDisabledOnDeckLink
    val checked = col.getter(assignment) && !webDisabledOnDeckLink
    val tooltip = when {
        webDisabledOnDeckLink -> webDeckLinkTooltip
        isWeb && isBrowserSource -> webSnapshotTooltip
        else -> col.tooltip
    }
    ContentToggleCell(
        modifier = modifier,
        label = col.label,
        checked = checked,
        enabled = enabled,
        tooltip = tooltip,
        onCheckedChange = { v -> onApply(col.setter(assignment, v)) }
    )
}

/**
 * Modal listing every content type + background for one output (a physical screen or browser
 * source). Replaces the old horizontally-scrolling per-row checkbox grid. Bible/Songs stay as
 * language dropdowns; everything else is a boolean toggle. Changes apply live.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ContentOutputsDialog(
    title: String,
    screenLabel: String,
    assignment: ScreenAssignment,
    contentGroup: List<ContentCol>,
    backgroundGroup: List<ContentCol>,
    bibleLabel: String,
    songsLabel: String,
    translationNames: List<String>,
    translationDisplays: List<TranslationChoiceDisplay>,
    songLanguageChoices: List<TranslationChoiceDisplay>,
    webDeckLinkTooltip: String,
    webSnapshotTooltip: String,
    isBrowserSource: Boolean,
    onApply: (ScreenAssignment) -> Unit,
    onDismiss: () -> Unit,
) {
    val shownContent = contentGroup.offeredTo(assignment)
    val shownBackgrounds = backgroundGroup.offeredTo(assignment)
    val total = contentOutputsTotalCount(assignment, contentGroup, backgroundGroup)
    val enabled = contentOutputsEnabledCount(assignment, contentGroup, backgroundGroup)

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.width(840.dp),
        shape = RoundedCornerShape(12.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Tv,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = stringResource(Res.string.content_outputs_enabled_subtitle, enabled, total),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(Res.string.content_outputs_done),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
          Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Quick select
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ContentOutputsSectionHeader(stringResource(Res.string.content_outputs_quick_select))
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        shape = RoundedCornerShape(6.dp),
                        onClick = {
                            var a = assignment
                            (shownContent + shownBackgrounds).forEach { a = it.setter(a, true) }
                            a = a.copy(
                                bibleMode = Constants.SONG_LANG_BOTH,
                                // An empty list means "every translation" -- Select All must reset
                                // this too, or a translation deselected earlier stays deselected
                                // even though the button says "all".
                                bibleTranslations = emptyList(),
                                songMode = if (a.songMode == Constants.SONG_LANG_OFF) Constants.SONG_LANG_BOTH else a.songMode
                            )
                            onApply(a)
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) { Text(stringResource(Res.string.content_outputs_select_all), style = MaterialTheme.typography.labelSmall) }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        shape = RoundedCornerShape(6.dp),
                        onClick = {
                            var a = assignment
                            (shownContent + shownBackgrounds).forEach { a = it.setter(a, false) }
                            a = a.copy(
                                bibleMode = Constants.SONG_LANG_OFF,
                                songMode = Constants.SONG_LANG_OFF,
                                songLookAhead = false
                            )
                            onApply(a)
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) { Text(stringResource(Res.string.content_outputs_clear_all), style = MaterialTheme.typography.labelSmall) }
                }

                // Content
                ContentOutputsSectionHeader(stringResource(Res.string.content_outputs_section_content))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ContentTranslationCell(
                        modifier = Modifier.weight(1f),
                        label = bibleLabel,
                        tags = TranslationPickerTags.BIBLE,
                        headerText = stringResource(Res.string.content_bible_translations_header),
                        enabledFormat = stringResource(Res.string.content_bible_translations_enabled),
                        footerText = stringResource(Res.string.content_bible_translations_footer),
                        translations = translationDisplays,
                        showing = assignment.showBible,
                        selected = assignment.bibleTranslations,
                        onShowingChange = { on ->
                            onApply(
                                assignment.copy(
                                    bibleMode = if (on) Constants.SONG_LANG_BOTH else Constants.SONG_LANG_OFF,
                                ),
                            )
                        },
                        onSelectedChange = { next -> onApply(assignment.copy(bibleTranslations = next)) },
                        onShowAndSelect = { next ->
                            onApply(
                                assignment.copy(
                                    bibleMode = Constants.SONG_LANG_BOTH,
                                    bibleTranslations = next,
                                ),
                            )
                        },
                    )
                    // The same checklist the Bible gets, for the same reason: a song may now carry
                    // up to four languages and an output may want any subset of them. The old
                    // single-choice picker could say "language 2" but never "languages 1 and 3".
                    ContentTranslationCell(
                        modifier = Modifier.weight(1f),
                        label = songsLabel,
                        tags = TranslationPickerTags.SONG,
                        headerText = stringResource(Res.string.content_song_languages_header),
                        enabledFormat = stringResource(Res.string.content_song_languages_enabled),
                        footerText = stringResource(Res.string.content_song_languages_footer),
                        translations = songLanguageChoices,
                        showing = assignment.showSongs,
                        selected = assignment.songTranslations,
                        onShowingChange = { on ->
                            onApply(
                                if (on) assignment.copy(songMode = Constants.SONG_LANG_BOTH)
                                else assignment.copy(songMode = Constants.SONG_LANG_OFF, songLookAhead = false),
                            )
                        },
                        onSelectedChange = { next -> onApply(assignment.copy(songTranslations = next)) },
                        onShowAndSelect = { next ->
                            onApply(
                                assignment.copy(
                                    songMode = Constants.SONG_LANG_BOTH,
                                    songTranslations = next,
                                ),
                            )
                        },
                    )
                }
                shownContent.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { col ->
                            ContentOutputsToggle(Modifier.weight(1f), col, assignment, isBrowserSource, webDeckLinkTooltip, webSnapshotTooltip, onApply)
                        }
                        if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }

                // Backgrounds
                ContentOutputsSectionHeader(stringResource(Res.string.content_outputs_section_backgrounds))
                shownBackgrounds.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { col ->
                            ContentOutputsToggle(Modifier.weight(1f), col, assignment, isBrowserSource, webDeckLinkTooltip, webSnapshotTooltip, onApply)
                        }
                        if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            // Live summary of what this output actually shows, drawn inside a monitor mock.
            ContentOutputsMonitorPreview(
                modifier = Modifier.width(280.dp),
                screenLabel = screenLabel,
                assignment = assignment,
                contentGroup = contentGroup,
                backgroundGroup = backgroundGroup,
                bibleLabel = bibleLabel,
                songsLabel = songsLabel,
                translationNames = translationNames,
                songLanguageChoices = songLanguageChoices,
            )
          }
        },
        confirmButton = {
            Button(shape = RoundedCornerShape(6.dp), onClick = onDismiss) {
                Text(stringResource(Res.string.content_outputs_done), style = MaterialTheme.typography.labelSmall)
            }
        }
    )
}
