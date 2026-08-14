package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.bottom
import churchpresenter.composeapp.generated.resources.clear
import churchpresenter.composeapp.generated.resources.content_bible_translations_all
import churchpresenter.composeapp.generated.resources.content_bible_translations_all_selected
import churchpresenter.composeapp.generated.resources.content_bible_translations_count_enabled
import churchpresenter.composeapp.generated.resources.content_bible_translations_enabled
import churchpresenter.composeapp.generated.resources.content_bible_translations_footer
import churchpresenter.composeapp.generated.resources.content_bible_translations_header
import churchpresenter.composeapp.generated.resources.content_bible_translations_more
import churchpresenter.composeapp.generated.resources.song_language_primary
import churchpresenter.composeapp.generated.resources.top
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.jetbrains.compose.resources.stringResource

/**
 * The individual cells of the Projection tab's per-output content grid: the on/off toggle, the
 * Bible translation picker and the song language picker.
 *
 * Separated from the surrounding layout (ProjectionContentOutputs.kt) because the translation cell
 * alone is ~420 lines -- together they made a file no one could scan.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ContentToggleCell(
    modifier: Modifier,
    label: String,
    checked: Boolean,
    enabled: Boolean,
    tooltip: String?,
    onCheckedChange: (Boolean) -> Unit,
) {
    // The weight modifier MUST sit on a plain layout node (the Box) that is a direct child of the
    // parent Row. Putting weight on a TooltipBox instead does not participate in the Row's weight
    // distribution and starves the sibling cell of width — that was the bug that hid every item
    // paired after a tooltipped one (Pictures/Presentation after Song LA, Songs Background after
    // Bible Background).
    Box(modifier = modifier) {
        val cell: @Composable () -> Unit = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 0.7f else 0.35f))
                    .clickable(enabled = enabled) { onCheckedChange(!checked) }
                    .padding(start = 4.dp, end = 10.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (tooltip != null) {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                tooltip = { PlainTooltip { Text(tooltip) } },
                state = rememberTooltipState(),
                modifier = Modifier.fillMaxWidth()
            ) { cell() }
        } else {
            cell()
        }
    }
}

/**
 * Test tags for the per-output Bible translation picker.
 *
 * Every part of this cell names itself with derived text — the trigger shows a summary of the
 * current selection, and each row shows a code and title read out of the .spb file — so a caption
 * is not a stable way to address any of them. These tags name what a control *is* instead.
 */
internal object TranslationPickerTags {
    /** The collapsed trigger segment that opens the picker. */
    const val TRIGGER = "contentOutputs_bibleTranslationTrigger"

    /** The master on/off row at the top of the open picker. */
    const val MASTER = "contentOutputs_bibleTranslationMaster"

    /** The row for the translation at stack position [index]. */
    fun row(index: Int) = "contentOutputs_bibleTranslationRow_$index"
}

/**
 * Bible content cell: a compact two-segment trigger button (on/off, then the current translation
 * pick) that opens a floating panel with the full translation list -- collapsed by default rather
 * than always showing the full picker inline, so it reads the same as any other content-outputs
 * row until the operator actually needs to change translations.
 */
@Composable
internal fun ContentTranslationCell(
    modifier: Modifier,
    label: String,
    /** The configured stack, in order; selection indices below refer to this order. */
    translations: List<BibleTranslationDisplay>,
    showing: Boolean,
    selected: List<Int>,
    onShowingChange: (Boolean) -> Unit,
    onSelectedChange: (List<Int>) -> Unit,
    /**
     * Turns this output's Bible content on AND sets its selection, atomically.
     *
     * [onShowingChange] and [onSelectedChange] each round-trip through the caller's own
     * `assignment.copy(...)` closure. Calling two of them back to back in one handler -- as
     * "turn on and select everything" needs -- has both read the *same* pre-click `assignment`
     * snapshot, since Compose does not recompose between two synchronous calls in one handler; the
     * second call's `.copy(...)` then overwrites the first's change instead of building on it. This
     * callback exists so callers can apply both fields in a single `assignment.copy(...)`.
     */
    onShowAndSelect: (List<Int>) -> Unit,
) {
    // Which translations this output actually shows, as positions that exist in the stack it is being
    // shown against. Everything below counts, labels, ticks and writes from this rather than from
    // `selected`, so a position past the end of the stack -- left in a settings file written before
    // the stack edits started remapping selections, or by hand -- is ignored consistently. Counting
    // one used to make the menu claim "2 of 3 translations enabled" over a single ticked row, while
    // the preview chip beside it named just the one.
    //
    // A stored selection that has been emptied this way shows nothing, not everything: it named
    // translations that have gone, which is not the same statement as the empty "all of them", and
    // reading it as "all" would put every language on a screen deliberately narrowed to one. That is
    // the same call TranslationStackEdits makes when a remap leaves an output with nothing.
    val tickedPositions = if (selected.isEmpty()) translations.indices.toList()
                          else selected.filter { it in translations.indices }
    val allSelected = tickedPositions.size == translations.size
    val enabledCount = if (!showing) 0 else tickedPositions.size
    val selectAll: () -> Unit = { onShowAndSelect(emptyList()) }
    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    var dropdownOpen by remember { mutableStateOf(false) }

    val selectionCount = tickedPositions.size
    val primaryIndex = tickedPositions.minOrNull() ?: 0
    // null while off, not just when there's nothing configured: otherwise this kept previewing
    // the last-selected translation's code/portion after Bible was switched off for this output,
    // instead of reflecting that nothing is actually showing right now.
    val primaryInfo = if (showing) translations.getOrNull(primaryIndex) else null
    // No fallback to `label` here: with zero translations configured there is nothing to name in
    // this segment, and falling back to `label` would repeat the left segment's own text.
    val allTranslationsSelected = selectionCount > 1 && selectionCount == translations.size
    val primaryLabel = when {
        primaryInfo == null -> ""
        allTranslationsSelected -> stringResource(Res.string.content_bible_translations_all_selected)
        else -> primaryInfo.code
    }
    val secondaryLabel = when {
        primaryInfo == null -> ""
        allTranslationsSelected -> stringResource(Res.string.content_bible_translations_count_enabled, selectionCount)
        selectionCount > 1 -> stringResource(Res.string.content_bible_translations_more, selectionCount - 1)
        else -> primaryInfo.portion
    }

    Box(modifier = modifier) {
        // Collapsed trigger: the left segment is a status indicator only (on/off lives on the
        // master row's checkbox inside the dropdown below); the whole rest of the button opens
        // that dropdown, regardless of whether Bible content is currently on or off, so a
        // translation can be picked before switching it on.
        val triggerShape = RoundedCornerShape(10.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .alpha(if (showing) 1f else 0.55f)
                .clip(triggerShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(dividerColor))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    // Always clickable, even with zero translations configured: the master on/off
                    // row now lives inside this dropdown (not on the collapsed trigger), so gating
                    // this on translations.isNotEmpty() would leave no way at all to reach it in
                    // that case.
                    .clickable { dropdownOpen = true }
                    // Tagged rather than found by caption: this segment's text is a derived summary
                    // ("All Bibles", a code, "+N more") that changes with the selection, and a
                    // single-selection caption repeats the code its own menu row shows.
                    .testTag(TranslationPickerTags.TRIGGER)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // fill = true (the default): the label column claims all the leftover width so
                // the chevron lands flush against the button's trailing edge instead of sitting
                // right after however wide the label happens to be.
                Column(modifier = Modifier.weight(1f)) {
                    if (primaryLabel.isNotEmpty()) {
                        Text(
                            text = primaryLabel,
                            style = MaterialTheme.typography.labelMedium,
                            // Monospace suits a file-stem code like "kjv1769" but not a plain
                            // phrase like "All Bibles".
                            fontFamily = if (allTranslationsSelected) FontFamily.Default else FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (secondaryLabel.isNotEmpty()) {
                        Text(
                            text = secondaryLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    // Otherwise unlabelled when there's nothing configured to name (primary/
                    // secondary text both blank) -- this is also what tests target to open the
                    // dropdown in that case.
                    contentDescription = stringResource(Res.string.content_bible_translations_header),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        DropdownMenu(
            expanded = dropdownOpen,
            onDismissRequest = { dropdownOpen = false },
            modifier = Modifier.width(320.dp),
            // Style the menu's own surface directly rather than nesting a second background
            // inside it -- DropdownMenu's default container has its own vertical inset around
            // whatever content() renders, which showed through as a visible band above and below
            // an inner Column that tried to draw its own separately-shaped/colored background.
            shape = RoundedCornerShape(12.dp),
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 0.dp,
        ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ContentOutputsSectionHeader(stringResource(Res.string.content_bible_translations_header))
            Spacer(modifier = Modifier.weight(1f))
            // Plain clickable Text pills rather than Button/OutlinedButton: those enforce a 58dp
            // minWidth floor that, in this narrow card, starved whichever pill measured last down
            // to ~0dp and made its label wrap one letter per line.
            Text(
                text = stringResource(Res.string.content_bible_translations_all),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                    .clickable(onClick = selectAll)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(Res.string.clear),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                    .clickable(onClick = { onShowingChange(false) })
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        HorizontalDivider(color = dividerColor)

        // Master "Bible" row
        val masterCheckShape = RoundedCornerShape(5.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (enabledCount > 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.07f) else Color.Transparent)
                // triStateToggleable (not plain clickable) so this still publishes the
                // ToggleableState semantics TriStateCheckbox used to -- tests locate this
                // control via isToggleable().
                .triStateToggleable(
                    state = when {
                        !showing -> ToggleableState.Off
                        allSelected -> ToggleableState.On
                        else -> ToggleableState.Indeterminate
                    },
                    onClick = { if (showing) onShowingChange(false) else selectAll() },
                )
                .testTag(TranslationPickerTags.MASTER)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Book,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = stringResource(Res.string.content_bible_translations_enabled, enabledCount, translations.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(masterCheckShape)
                    .background(if (enabledCount > 0) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .border(1.dp, if (enabledCount > 0) Color.Transparent else MaterialTheme.colorScheme.outline, masterCheckShape),
                contentAlignment = Alignment.Center,
            ) {
                if (allSelected && showing) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(11.dp))
                } else if (enabledCount > 0) {
                    Box(modifier = Modifier.size(width = 8.dp, height = 2.dp).background(MaterialTheme.colorScheme.onPrimary, RoundedCornerShape(1.dp)))
                }
            }
        }

        // Nothing to choose between with a single translation -- the row above already says
        // whether this output shows it. Shown regardless of `showing`: picking translations must
        // work whether or not Bible content is currently switched on for this output.
        if (translations.size > 1) {
            HorizontalDivider(color = dividerColor)
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                translations.forEachIndexed { index, info ->
                    val selectedIn = index in tickedPositions
                    // Off means every row reads as unticked, matching the master row's own
                    // checkbox -- the underlying selection is still remembered in `selected`,
                    // just not shown as active while Bible is off for this output.
                    val ticked = showing && selectedIn
                    val toggle: () -> Unit = {
                        if (!showing) {
                            // Starting fresh from off, a click means "show just this one" -- it must
                            // NOT fold in whatever `selected` happened to hold before Bible was
                            // switched off. `selected` is frequently the empty-list "all" sentinel
                            // at that point (e.g. right after "Clear", which only flips `showing`),
                            // and building the new selection from "all indices" + this one collapses
                            // straight back to that same sentinel -- every row re-selecting itself
                            // the moment any one of them was clicked.
                            onShowAndSelect(listOf(index))
                        } else if (!selectedIn) {
                            val next = (tickedPositions + index).distinct().sorted()
                            // Everything ticked is stored as "all", so a translation added later
                            // shows up here too rather than needing to be ticked on every output.
                            onSelectedChange(if (next.size == translations.size) emptyList() else next)
                        } else {
                            val next = tickedPositions.filterNot { it == index }
                            if (next.isEmpty()) {
                                // Unchecking the last remaining translation would store the same
                                // empty list that means "all" everywhere else in this cell -- next
                                // render, every row would read back as ticked again. Turning Bible
                                // off instead is what "nothing selected" actually means, and leaves
                                // `selected` untouched (every "turn on" path already resets it, so
                                // nothing is lost).
                                onShowingChange(false)
                            } else {
                                onSelectedChange(next)
                            }
                        }
                    }
                    val chipShape = RoundedCornerShape(6.dp)
                    val rowCheckShape = RoundedCornerShape(5.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (ticked) MaterialTheme.colorScheme.primary.copy(alpha = 0.09f) else Color.Transparent)
                            .clickable(onClick = toggle)
                            // Tagged by stack position, which is what a selection actually stores;
                            // the code and title beside it are file-derived and repeat elsewhere.
                            .testTag(TranslationPickerTags.row(index))
                            .padding(start = 30.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            // Fixed width (not just a minimum): a longer code like "kjv1769"
                            // must not push its row's title column further right than every
                            // other row's, which is what a min-only width let happen.
                            modifier = Modifier
                                .width(58.dp)
                                .height(26.dp)
                                .clip(chipShape)
                                .background(if (ticked) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, if (ticked) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant, chipShape)
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = info.code,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = if (ticked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        // fill = true (the default): the title column claims all the leftover
                        // width so the checkbox lands flush against the row's trailing edge
                        // instead of sitting right after however wide the title happens to be.
                        // Safe here (unlike the header pills earlier) because this wraps plain
                        // Text with maxLines=1 + ellipsis, not a component with a mandatory
                        // min-width that could be squeezed to zero.
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = info.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (ticked) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (ticked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            // PRIMARY sits on its own line with the portion rather than competing
                            // with the title for width -- a long title (e.g. "King James Version")
                            // was getting cut to "King James V..." to make room for the tag.
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (info.portion.isNotEmpty()) {
                                    Text(
                                        text = info.portion,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        softWrap = false,
                                    )
                                }
                                if (index == 0) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                    ) {
                                        Text(
                                            text = stringResource(Res.string.song_language_primary),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                        )
                                    }
                                }
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(rowCheckShape)
                                .background(if (ticked) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .border(1.dp, if (ticked) Color.Transparent else MaterialTheme.colorScheme.outline, rowCheckShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (ticked) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(11.dp))
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = dividerColor)
        Text(
            text = stringResource(Res.string.content_bible_translations_footer),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .padding(horizontal = 14.dp, vertical = 9.dp),
        )
        } // DropdownMenu
    } // outer Box
}

/**
 * Songs content cell: a collapsed trigger (current mode + chevron) that opens a floating panel
 * listing every mode -- styled to match [ContentTranslationCell] so the two cells read as one
 * family rather than two different pickers side by side. Unlike Bible's checklist, mode selection
 * is single-choice (Off counts as a mode, not a separate on/off dimension), so each row is a plain
 * radio-style pick that both selects and closes the panel.
 */
@Composable
internal fun ContentLangCell(
    modifier: Modifier,
    label: String,
    modes: List<Pair<String, String>>,
    currentMode: String,
    onSelect: (String) -> Unit,
) {
    var dropdownOpen by remember { mutableStateOf(false) }
    val currentLabel = modes.find { it.first == currentMode }?.second ?: modes.first().second
    val isOff = currentMode == Constants.SONG_LANG_OFF
    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)

    Box(modifier = modifier) {
        // Left segment is a plain label, not a click target -- matching Bible's trigger, where
        // only the value/chevron side opens anything.
        val triggerShape = RoundedCornerShape(10.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .alpha(if (isOff) 0.55f else 1f)
                .clip(triggerShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
        ) {
            Row(
                modifier = Modifier.fillMaxHeight().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(dividerColor))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { dropdownOpen = true }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = currentLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        DropdownMenu(
            expanded = dropdownOpen,
            onDismissRequest = { dropdownOpen = false },
            modifier = Modifier.width(220.dp),
            shape = RoundedCornerShape(12.dp),
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 0.dp,
        ) {
            ContentOutputsSectionHeader(
                text = label,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            )
            HorizontalDivider(color = dividerColor)
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                modes.forEach { (value, modeLabel) ->
                    val isSelected = value == currentMode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.09f) else Color.Transparent)
                            .clickable {
                                dropdownOpen = false
                                onSelect(value)
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = modeLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        // Radio, not checkbox: modes are mutually exclusive (a single pick, Off
                        // included), unlike Bible's independently-toggleable translations.
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) {
                                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                            }
                        }
                    }
                }
            }
        }
    }
}
