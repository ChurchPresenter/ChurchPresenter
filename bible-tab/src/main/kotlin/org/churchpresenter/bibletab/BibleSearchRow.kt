package org.churchpresenter.bibletab

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.churchpresenter.hosanna.composables.button.IconButton
import org.churchpresenter.hosanna.composables.chip.ActionChip
import org.churchpresenter.hosanna.composables.feedback.Tooltip
import org.churchpresenter.hosanna.composables.field.Dropdown
import org.churchpresenter.hosanna.composables.field.SearchField
import org.churchpresenter.hosanna.composables.model.ButtonHierarchy
import org.churchpresenter.hosanna.composables.model.IconButtonSize
import org.churchpresenter.hosanna.composables.model.ChipColor
import org.churchpresenter.hosanna.composables.model.ChipSize
import org.churchpresenter.hosanna.composables.model.MenuEntry
import org.churchpresenter.icons.generated.resources.ic_search
import org.churchpresenter.sharedui.composables.SearchFieldFocus
import org.churchpresenter.sharedui.composables.rememberSearchFieldValue
import org.churchpresenter.sharedui.composables.searchBarCard
import org.churchpresenter.sharedui.guide.GuideTargets
import org.churchpresenter.sharedui.guide.guideTarget
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.bible_search_mode_auto
import org.churchpresenter.strings.generated.resources.bible_search_mode_text
import org.churchpresenter.strings.generated.resources.bible_search_mode_tooltip
import org.churchpresenter.strings.generated.resources.customize_group_reference
import org.churchpresenter.strings.generated.resources.mode
import org.churchpresenter.strings.generated.resources.scope
import org.churchpresenter.strings.generated.resources.search
import org.churchpresenter.strings.generated.resources.search_clear
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.icons.generated.resources.Res as IconRes

/**
 * The smart search box, its scope and mode selectors, and the search button.
 *
 * Below 440dp the four cannot share a row, so it stacks into a column — which is why this measures
 * itself rather than being told how wide it is.
 */
@Composable
internal fun BibleSearchRow(
    searchQuery: String,
    searchPlaceholder: String,
    searchMode: BibleSearchMode,
    scopeOptions: List<String>,
    selectedScope: String,
    modeOptions: List<String>,
    selectedMode: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onCycleSearchMode: () -> Unit,
    onScopeSelected: (Int) -> Unit,
    onModeSelected: (Int) -> Unit,
    searchFocus: SearchFieldFocus = remember { SearchFieldFocus() },
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().searchBarCard()) {
        val field: @Composable (Modifier) -> Unit = { modifier ->
            BibleSearchField(
                value = searchQuery,
                placeholder = searchPlaceholder,
                onValueChange = onQueryChange,
                onClear = onClear,
                onSubmit = onSubmit,
                onFocusChanged = onFocusChanged,
                modeChip = {
                    SearchModeChip(
                        searchMode,
                        onCycleSearchMode,
                        modifier = Modifier.guideTarget(GuideTargets.BIBLE_SEARCH_MODE),
                    )
                },
                modifier = modifier.height(SEARCH_ROW_HEIGHT).guideTarget(GuideTargets.BIBLE_SEARCH),
                focus = searchFocus,
            )
        }
        val pickers: @Composable () -> Unit = {
            Picker(stringResource(Res.string.scope), scopeOptions, selectedScope, onScopeSelected)
            Picker(stringResource(Res.string.mode), modeOptions, selectedMode, onModeSelected)
            val searchStr = stringResource(Res.string.search)
            Tooltip(searchStr) {
                IconButton(
                    painterResource(IconRes.drawable.ic_search),
                    searchStr,
                    onSubmit,
                    Modifier.size(SEARCH_ROW_HEIGHT),
                    size = IconButtonSize.Large,
                    hierarchy = ButtonHierarchy.Primary,
                )
            }
        }
        if (maxWidth < NARROW_SEARCH) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                field(Modifier.fillMaxWidth())
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    pickers()
                }
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                field(Modifier.weight(1f))
                pickers()
            }
        }
    }
}

private val NARROW_SEARCH = 440.dp

// The height of a labelled dropdown, so the search box, the pickers and the button line up.
private val SEARCH_ROW_HEIGHT = 44.dp
private val PICKER_WIDTH = 140.dp

/** A scope or mode picker: the options keyed by their position, as the view model takes them. */
@Composable
private fun Picker(label: String, options: List<String>, selected: String, onSelected: (Int) -> Unit) {
    Dropdown(
        entries = options.mapIndexed { index, option -> MenuEntry.Item(index.toString(), option) },
        selectedKey = options.indexOf(selected).takeIf { it >= 0 }?.toString(),
        onSelect = { onSelected(it.toInt()) },
        modifier = Modifier.widthIn(min = PICKER_WIDTH),
        label = label,
    )
}

/** The search box, with the keyboard moved in and the query selected when the tab asks. */
@Composable
internal fun BibleSearchField(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modeChip: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    /** How the tab moves the keyboard in here with the query selected. */
    focus: SearchFieldFocus = remember { SearchFieldFocus() },
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val report by rememberUpdatedState(onFocusChanged)
    LaunchedEffect(focused) { report(focused) }
    var fieldValue by rememberSearchFieldValue(value, focus)
    SearchField(
        value = fieldValue,
        onValueChange = {
            fieldValue = it
            if (it.text != value) onValueChange(it.text)
        },
        placeholder = placeholder,
        clearContentDescription = stringResource(Res.string.search_clear),
        modifier = modifier,
        interactionSource = source,
        onSearch = onSubmit,
        onClear = onClear,
        focusRequester = focus.requester,
        trailing = modeChip,
    )
}

@Composable
private fun SearchModeChip(searchMode: BibleSearchMode, onCycle: () -> Unit, modifier: Modifier = Modifier) {
    val label = when (searchMode) {
        BibleSearchMode.AUTO -> Res.string.bible_search_mode_auto
        BibleSearchMode.REFERENCE -> Res.string.customize_group_reference
        BibleSearchMode.TEXT -> Res.string.bible_search_mode_text
    }
    val color = when (searchMode) {
        BibleSearchMode.AUTO -> ChipColor.Neutral
        BibleSearchMode.REFERENCE -> ChipColor.Bible
        BibleSearchMode.TEXT -> ChipColor.Song
    }
    Tooltip(stringResource(Res.string.bible_search_mode_tooltip), modifier) {
        ActionChip(stringResource(label), onCycle, size = ChipSize.Small, color = color)
    }
}
