package org.churchpresenter.bibletab

import androidx.compose.foundation.layout.Box
import org.churchpresenter.sharedui.guide.guideTarget
import org.churchpresenter.sharedui.guide.GuideTargets
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.add_to_schedule
import org.churchpresenter.strings.generated.resources.bible_translation_order
import org.churchpresenter.strings.generated.resources.bible_verse_selection_hint
import org.churchpresenter.strings.generated.resources.go_live
import org.churchpresenter.strings.generated.resources.hold_live
import org.churchpresenter.strings.generated.resources.swap_bibles
import org.churchpresenter.strings.generated.resources.bible_cross_references
import org.churchpresenter.strings.generated.resources.bible_cross_references_title
import org.churchpresenter.strings.generated.resources.hold_live_modifier_hint
import org.churchpresenter.icons.generated.resources.ic_link
import org.churchpresenter.icons.generated.resources.ic_pause
import org.churchpresenter.icons.generated.resources.ic_swap
import org.churchpresenter.strings.generated.resources.stt_connect
import org.churchpresenter.strings.generated.resources.stt_disconnect
import org.churchpresenter.strings.generated.resources.swap_bibles_hint
import org.churchpresenter.strings.generated.resources.verse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.churchpresenter.bible.bibleDisplayNames
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import org.churchpresenter.hosanna.composables.button.IconButton
import org.churchpresenter.hosanna.composables.button.IconToggleButton
import org.churchpresenter.hosanna.composables.chip.FilterChip
import org.churchpresenter.hosanna.composables.feedback.RichTooltip
import org.churchpresenter.hosanna.composables.feedback.Tooltip
import org.churchpresenter.hosanna.composables.model.ButtonHierarchy
import org.churchpresenter.hosanna.composables.model.ChipColor
import org.churchpresenter.hosanna.composables.model.ChipSize
import org.churchpresenter.hosanna.composables.model.ButtonIntent
import org.churchpresenter.icons.generated.resources.ic_playlist_add
import org.churchpresenter.sharedui.utils.LocalShortcuts
import org.churchpresenter.sharedui.utils.label
import org.churchpresenter.sharedui.models.ShortcutAction
import androidx.compose.material.icons.filled.Tv

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun BibleVerseHeader(
    crossRefsVisible: Boolean,
    crossRefsDocked: Boolean,
    holdAvailable: Boolean,
    holdLive: Boolean,
    sttToggleVisible: Boolean,
    sttConnected: Boolean,
    translations: List<BibleTranslationSettings>,
    storageDirectory: String,
    translationSelectionKey: List<String>,
    onCrossReferencesToggle: () -> Unit,
    onHoldLiveToggle: () -> Unit,
    onSttToggle: () -> Unit,
    onSwapTranslations: () -> Unit,
    onMoveTranslation: (index: Int, offset: Int) -> Unit,
    onAddToSchedule: () -> Unit,
    onGoLive: () -> Unit,
    showLabel: Boolean = true,
) {
    val holdLiveStr = stringResource(Res.string.hold_live)
    val verseSelectionHint = stringResource(Res.string.bible_verse_selection_hint)
    val goLiveStr = stringResource(Res.string.go_live)
    val addScheduleStr = stringResource(Res.string.add_to_schedule)
    // Wraps rather than clips: in split mode the verse card is narrow, and a Row squeezed the
    // trailing Go Live button to nothing.
    FlowRow(
        // The same height as the Book and Chapter headings, so the label and the buttons sit on their
        // center line and all three lists start level.
        modifier = Modifier.fillMaxWidth().heightIn(min = BIBLE_HEADER_HEIGHT)
            .guideTarget(GuideTargets.BIBLE_VERSES)
            .padding(start = 16.dp, end = 10.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        // Without the label the spacer keeps the actions right-aligned, as the label's weight did.
        // Its own vertical padding is lost inside the 48dp row and only shows when a narrow card
        // wraps the buttons below it, where it keeps the label off the card's top edge.
        if (showLabel) {
            BibleListHeaderLabel(stringResource(Res.string.verse), Modifier.weight(1f).padding(vertical = 7.dp))
        } else Spacer(Modifier.weight(1f))

        if (crossRefsVisible) {
            Box(Modifier.guideTarget(GuideTargets.BIBLE_CROSS_REFS)) {
                CrossRefsPill(crossRefsDocked, onCrossReferencesToggle)
            }
        }

        HoldLivePill(holdAvailable, holdLive, holdLiveStr, verseSelectionHint, onHoldLiveToggle)

        TranslationControls(
            translations, storageDirectory, translationSelectionKey, onSwapTranslations, onMoveTranslation,
        )

        // Beside Add to Schedule rather than out among the translation controls: this is
        // what opens the Bible Lookup Engine, so it belongs with the actions rather than
        // with the things that choose what is being read.
        if (sttToggleVisible) {
            val sttActionStr = if (sttConnected) {
                stringResource(Res.string.stt_disconnect)
            } else {
                stringResource(Res.string.stt_connect)
            }
            Tooltip(sttActionStr) {
                IconToggleButton(
                    rememberVectorPainter(Icons.Filled.Mic),
                    sttActionStr,
                    sttConnected,
                    { onSttToggle() },
                )
            }
        }

        Tooltip(addScheduleStr, Modifier.guideTarget(GuideTargets.BIBLE_ADD_TO_SCHEDULE)) {
            IconButton(
                painterResource(IconRes.drawable.ic_playlist_add),
                addScheduleStr,
                { onAddToSchedule() },
                hierarchy = ButtonHierarchy.Primary,
                intent = ButtonIntent.Neutral,
            )
        }

        val goLiveKey = LocalShortcuts.current.label(ShortcutAction.GO_LIVE).ifEmpty { null }
        Tooltip(goLiveStr, shortcut = goLiveKey) {
            IconButton(
                rememberVectorPainter(Icons.Default.Tv),
                goLiveStr,
                onGoLive,
                hierarchy = ButtonHierarchy.Primary,
            )
        }
    }
}

@Composable
internal fun BibleListHeaderLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
private fun CrossRefsPill(crossRefsDocked: Boolean, onCrossReferencesToggle: () -> Unit) {
    Tooltip(stringResource(Res.string.bible_cross_references)) {
        FilterChip(
            label = stringResource(Res.string.bible_cross_references_title),
            selected = crossRefsDocked,
            onSelectedChange = { onCrossReferencesToggle() },
            icon = painterResource(IconRes.drawable.ic_link),
            size = ChipSize.Small,
        )
    }
}

@Composable
private fun HoldLivePill(
    holdAvailable: Boolean,
    holdLive: Boolean,
    holdLiveStr: String,
    verseSelectionHint: String,
    onHoldLiveToggle: () -> Unit,
) {
    // Red while the hold is live. Where it cannot apply it is shown but not pressable -- its
    // tooltip then explains Ctrl/Shift selection instead.
    Tooltip(if (holdAvailable) holdLiveStr else verseSelectionHint) {
        FilterChip(
            label = stringResource(Res.string.hold_live_modifier_hint),
            selected = holdLive && holdAvailable,
            onSelectedChange = { onHoldLiveToggle() },
            icon = painterResource(IconRes.drawable.ic_pause),
            size = ChipSize.Small,
            color = ChipColor.Live,
            enabled = holdAvailable,
        )
    }
}

@Composable
private fun TranslationControls(
    translations: List<BibleTranslationSettings>,
    storageDirectory: String,
    translationSelectionKey: List<String>,
    onSwapTranslations: () -> Unit,
    onMoveTranslation: (index: Int, offset: Int) -> Unit,
) {
    val swapBiblesStr = stringResource(Res.string.swap_bibles)
    val translationOrderStr = stringResource(Res.string.bible_translation_order)
    if (translations.size == 2) {
        val order = translations.mapIndexed { position, item ->
            "${position + 1}. ${item.fileName.substringBeforeLast('.').ifEmpty { "-" }}"
        }
        RichTooltip(stringResource(Res.string.swap_bibles_hint), order.joinToString("\n")) {
            IconButton(
                painterResource(IconRes.drawable.ic_swap),
                swapBiblesStr,
                { onSwapTranslations() },
                hierarchy = ButtonHierarchy.Secondary,
            )
        }
    } else if (translations.size > 2) {
        // The renames come in on `translations` -- each entry carries its own --
        // so this reads them without a parameter of its own.
        val customNames = translations
            .associate { it.fileName to it.customName.trim() }
            .filterValues { it.isNotBlank() }
        val translationDisplayNames by produceState(
            initialValue = emptyMap<String, String>(),
            storageDirectory,
            customNames,
            translationSelectionKey,
        ) {
            value = withContext(Dispatchers.IO) {
                bibleDisplayNames(
                    storageDirectory,
                    translations.map { it.fileName },
                    customNames,
                )
            }
        }
        TranslationOrderSelector(
            label = translationOrderStr,
            translations = translations,
            displayNames = translationDisplayNames,
            onMove = { index, offset ->
                onMoveTranslation(index, offset)
            },
            modifier = Modifier
                .widthIn(min = 127.dp, max = 174.dp),
        )
    }
}

/** The height of every Bible list card's heading row: the buttons' touch target. */
internal val BIBLE_HEADER_HEIGHT = 48.dp
