package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.clear
import churchpresenter.composeapp.generated.resources.content_bible_translations_all
import churchpresenter.composeapp.generated.resources.content_bible_translations_count_enabled
import churchpresenter.composeapp.generated.resources.content_bible_translations_more
import churchpresenter.composeapp.generated.resources.song_language_primary
import org.churchpresenter.theme.elevationPalette
import org.churchpresenter.theme.raised
import org.churchpresenter.theme.sunken
import org.jetbrains.compose.resources.stringResource

/**
 * Which of a song's languages a profile actually puts on screen -- the Songs half of the Profiles
 * tab's Sources row. (It picked Bible translations too, until the Bible source became an ordered
 * list: see [BibleSourcePicker].)
 *
 * Recovered from the per-output Content Outputs dialog the Output Profiles refactor deleted. That
 * dialog was the only thing that ever wrote `bibleTranslations`/`songTranslations`, so when it went
 * those two fields kept being read by every presenter and became unsettable: a profile could be
 * told "show the Bible" but not "show only the KJV of the three in the stack". The widget is
 * unchanged bar its home -- it was always self-contained -- and now hangs off the profile's own
 * content row rather than an output's table cell.
 */

/** One translation as the picker lists it: its code, its name, and how much of the Bible it holds. */
internal data class TranslationChoiceDisplay(
    val code: String,
    val title: String,
    val portion: String,
)

/** The small uppercase accent caption at the top of the open picker. */
@Composable
private fun ContentOutputsSectionHeader(text: String, modifier: Modifier = Modifier) {
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
 * The test tags of one translation picker.
 *
 * Per-picker rather than constant so a second picker can be drawn beside the first without its tags
 * colliding. The song languages are the only one now: the Bible source became an ordered list of
 * its own ([BibleSourcePicker]).
 */
internal class TranslationPickerTags(private val prefix: String) {
    /** The collapsed trigger segment that opens the picker. */
    val trigger = "contentOutputs_${prefix}Trigger"

    /** The master on/off row at the top of the open picker. */
    val master = "contentOutputs_${prefix}Master"

    /** The row for the translation at stack position [index]. */
    fun row(index: Int) = "contentOutputs_${prefix}Row_$index"

    companion object {
        val SONG = TranslationPickerTags("songLanguage")
    }
}

@Composable
internal fun ContentTranslationCell(
    modifier: Modifier,
    label: String,
    /** Which picker this is, so it is addressable in a test by tag rather than by its changing caption. */
    tags: TranslationPickerTags,
    /** What the open panel calls the thing being picked -- translations, or song languages. */
    headerText: String,
    /**
     * The "%1$d of %2$d enabled" template for the master row -- the two pickers count different
     * nouns. A template rather than a finished string because the counts are worked out in here.
     */
    enabledFormat: String,
    /** The grey note under the list, explaining what picking more than one does. */
    footerText: String,
    /** What the trigger reads when every choice is ticked -- "All Bibles", "All Songs". */
    allSelectedText: String,
    /**
     * Whether a multiple selection names its choices outright ("1, 2, 4") rather than naming the
     * first and counting the rest ("1", then "+2 more" underneath).
     *
     * Song languages are single digits and there are at most four, so the whole selection fits in
     * the space the count occupied and says more than the count did -- "1, 2, 4" is the answer the
     * operator came for, where "+2 more" makes them open the menu to find out which two. A Bible
     * stack is file stems like `kjv1769`, which do not fit and are what the count is there for.
     */
    listSelectedCodes: Boolean = false,
    /** The configured stack, in order; selection indices below refer to this order. */
    translations: List<TranslationChoiceDisplay>,
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
    /** What the closed field reads while the category is off -- "Songs off". */
    offText: String,
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
        allTranslationsSelected -> allSelectedText
        listSelectedCodes -> tickedPositions.mapNotNull { translations.getOrNull(it)?.code }.joinToString(", ")
        else -> primaryInfo.code
    }
    val secondaryLabel = when {
        primaryInfo == null -> ""
        allTranslationsSelected -> stringResource(Res.string.content_bible_translations_count_enabled, selectionCount)
        // The codes above already name every one of them; a count under them would restate it.
        listSelectedCodes -> ""
        selectionCount > 1 -> stringResource(Res.string.content_bible_translations_more, selectionCount - 1)
        else -> primaryInfo.portion
    }

    Box(modifier = modifier) {
        SourceField(
            label = label,
            value = if (showing && primaryLabel.isNotEmpty()) primaryLabel else offText,
            sub = if (showing) secondaryLabel.ifEmpty { headerText } else "",
            open = dropdownOpen,
            dimmed = !showing,
            onClick = { dropdownOpen = true },
            modifier = Modifier.testTag(tags.trigger),
        )

        DropdownMenu(
            expanded = dropdownOpen,
            onDismissRequest = { dropdownOpen = false },
            modifier = Modifier.width(MENU_WIDTH),
            // Style the menu's own surface directly rather than nesting a second background
            // inside it -- DropdownMenu's default container has its own vertical inset around
            // whatever content() renders, which showed through as a visible band above and below
            // an inner Column that tried to draw its own separately-shaped/colored background.
            shape = RoundedCornerShape(12.dp),
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 0.dp,
        ) {
            TranslationPickerHeader(
                headerText = headerText,
                onSelectAll = selectAll,
                onClear = { onShowingChange(false) },
            )
            TranslationPickerMasterRow(
                label = label,
                showing = showing,
                allSelected = allSelected,
                enabledCount = enabledCount,
                translationCount = translations.size,
                enabledFormat = enabledFormat,
                tag = tags.master,
                onShowingChange = onShowingChange,
                onSelectAll = selectAll,
            )
            // Nothing to choose between with a single translation -- the row above already says
            // whether this profile shows it. Shown regardless of `showing`: picking translations
            // must work whether or not the category is currently switched on.
            if (translations.size > 1) {
                HorizontalDivider(color = dividerColor)
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    translations.forEachIndexed { index, info ->
                        TranslationPickerRow(
                            index = index,
                            info = info,
                            tickedPositions = tickedPositions,
                            translationCount = translations.size,
                            showing = showing,
                            tag = tags.row(index),
                            onShowingChange = onShowingChange,
                            onSelectedChange = onSelectedChange,
                            onShowAndSelect = onShowAndSelect,
                        )
                    }
                }
            }
            TranslationPickerFooter(footerText = footerText, dividerColor = dividerColor)
        }
    }
}

/** How wide the open checklist is: room for a code, a name and a tick without crowding. */
private val MENU_WIDTH = 320.dp

/** The checklist's own title, with Select-all and Clear beside it. */
@Composable
private fun TranslationPickerHeader(headerText: String, onSelectAll: () -> Unit, onClear: () -> Unit) {
    // Header
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ContentOutputsSectionHeader(headerText)
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
                .clickable(onClick = onSelectAll)
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
                .clickable(onClick = onClear)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** The category's own on/off row, tri-state because a subset is neither on nor off. */
@Composable
private fun TranslationPickerMasterRow(
    label: String,
    showing: Boolean,
    allSelected: Boolean,
    enabledCount: Int,
    translationCount: Int,
    enabledFormat: String,
    tag: String,
    onShowingChange: (Boolean) -> Unit,
    onSelectAll: () -> Unit,
) {
    val masterCheckShape = RoundedCornerShape(5.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (enabledCount > 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)
                else Color.Transparent,
            )
            // triStateToggleable (not plain clickable) so this still publishes the
            // ToggleableState semantics TriStateCheckbox used to -- tests locate this
            // control via isToggleable().
            .triStateToggleable(
                state = when {
                    !showing -> ToggleableState.Off
                    allSelected -> ToggleableState.On
                    else -> ToggleableState.Indeterminate
                },
                onClick = { if (showing) onShowingChange(false) else onSelectAll() },
            )
            .testTag(tag)
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
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = enabledFormat.format(enabledCount, translationCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Box(
            modifier = Modifier
                .size(18.dp)
                // Ticked is a raised accent key, clear a sunken well -- the app's checkbox.
                .then(
                    if (enabledCount > 0) {
                        Modifier.raised(masterCheckShape, elevationPalette().accent, elevationPalette(), lift = 2.dp)
                    } else {
                        Modifier.sunken(masterCheckShape, elevationPalette())
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (allSelected && showing) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(11.dp),
                )
            } else if (enabledCount > 0) {
                Box(
                    modifier = Modifier
                        .size(width = 8.dp, height = 2.dp)
                        .background(MaterialTheme.colorScheme.onPrimary, RoundedCornerShape(1.dp)),
                )
            }
        }
    }
}

/**
 * What clicking one row means, given what is ticked now.
 *
 * Plain rather than composable: it draws nothing, and the three branches are the whole of this
 * picker's behaviour -- every one of them was a bug once, so they are worth reading on their own
 * rather than buried in the middle of a row's layout.
 */
@Suppress("LongParameterList")
private fun toggleTranslationAt(
    index: Int,
    selectedIn: Boolean,
    showing: Boolean,
    tickedPositions: List<Int>,
    translationCount: Int,
    onShowingChange: (Boolean) -> Unit,
    onSelectedChange: (List<Int>) -> Unit,
    onShowAndSelect: (List<Int>) -> Unit,
) {
    if (!showing) {
        // Starting fresh from off, a click means "show just this one" -- it must NOT fold in
        // whatever `selected` happened to hold before the category was switched off. `selected` is
        // frequently the empty-list "all" sentinel at that point (e.g. right after "Clear", which
        // only flips `showing`), and building the new selection from "all indices" + this one
        // collapses straight back to that same sentinel -- every row re-selecting itself the
        // moment any one of them was clicked.
        onShowAndSelect(listOf(index))
        return
    }
    if (!selectedIn) {
        val next = (tickedPositions + index).distinct().sorted()
        // Everything ticked is stored as "all", so a translation added later shows up here too
        // rather than needing to be ticked on every profile.
        onSelectedChange(if (next.size == translationCount) emptyList() else next)
        return
    }
    val next = tickedPositions.filterNot { it == index }
    if (next.isEmpty()) {
        // Unchecking the last remaining translation would store the same empty list that means
        // "all" everywhere else in this cell -- next render, every row would read back as ticked
        // again. Turning the category off instead is what "nothing selected" actually means, and
        // leaves `selected` untouched (every "turn on" path already resets it, so nothing is lost).
        onShowingChange(false)
    } else {
        onSelectedChange(next)
    }
}

/**
 * A row's leading code badge -- `kjv1769`, or a song language's position.
 *
 * Fixed width rather than a minimum: a longer code must not push its row's title column further
 * right than every other row's, which is what a min-only width let happen.
 */
@Composable
private fun TranslationCodeChip(code: String, ticked: Boolean, shape: Shape) {
    Box(
        modifier = Modifier
            .width(58.dp)
            .height(26.dp)
            .then(
                if (ticked) {
                    Modifier.raised(shape, elevationPalette().selected, elevationPalette(), lift = 2.dp)
                } else {
                    Modifier.sunken(shape, elevationPalette())
                }
            )
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = code,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = if (ticked) elevationPalette().selected.ink
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** One translation, or one of a song's languages: its code, its name and its tick. */
@Composable
private fun TranslationPickerRow(
    index: Int,
    info: TranslationChoiceDisplay,
    tickedPositions: List<Int>,
    translationCount: Int,
    showing: Boolean,
    tag: String,
    onShowingChange: (Boolean) -> Unit,
    onSelectedChange: (List<Int>) -> Unit,
    onShowAndSelect: (List<Int>) -> Unit,
) {
    val selectedIn = index in tickedPositions
    // Off means every row reads as unticked, matching the master row's own
    // checkbox -- the underlying selection is still remembered in `selected`,
    // just not shown as active while Bible is off for this output.
    val ticked = showing && selectedIn
    val toggle: () -> Unit = {
        toggleTranslationAt(
            index = index,
            selectedIn = selectedIn,
            showing = showing,
            tickedPositions = tickedPositions,
            translationCount = translationCount,
            onShowingChange = onShowingChange,
            onSelectedChange = onSelectedChange,
            onShowAndSelect = onShowAndSelect,
        )
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
            .testTag(tag)
            .padding(start = 30.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TranslationCodeChip(code = info.code, ticked = ticked, shape = chipShape)
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
                .then(
                    if (ticked) {
                        Modifier.raised(rowCheckShape, elevationPalette().accent, elevationPalette(), lift = 2.dp)
                    } else {
                        Modifier.sunken(rowCheckShape, elevationPalette())
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (ticked) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(11.dp),
                )
            }
        }
    }
}

/** The grey note under the list, explaining what picking more than one does. */
@Composable
private fun TranslationPickerFooter(footerText: String, dividerColor: Color) {
    HorizontalDivider(color = dividerColor)
    Text(
        text = footerText,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}
