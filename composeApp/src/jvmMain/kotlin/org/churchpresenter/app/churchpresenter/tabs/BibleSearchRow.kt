package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.bible_search_mode_auto
import churchpresenter.composeapp.generated.resources.bible_search_mode_reference
import churchpresenter.composeapp.generated.resources.bible_search_mode_text
import churchpresenter.composeapp.generated.resources.bible_search_mode_tooltip
import org.churchpresenter.icons.generated.resources.ic_search
import churchpresenter.composeapp.generated.resources.mode
import churchpresenter.composeapp.generated.resources.scope
import churchpresenter.composeapp.generated.resources.search
import org.churchpresenter.app.churchpresenter.composables.DropdownSelector
import org.churchpresenter.app.churchpresenter.viewmodel.BibleSearchMode
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.ExperimentalFoundationApi
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
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 8.dp)) {
        val searchIsNarrow = maxWidth < 440.dp

        if (searchIsNarrow) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BibleSearchField(
                    value = searchQuery,
                    placeholder = searchPlaceholder,
                    onValueChange = onQueryChange,
                    onClear = onClear,
                    onSubmit = onSubmit,
                    onFocusChanged = onFocusChanged,
                    modeChip = { SearchModeChip(searchMode, onCycleSearchMode) },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DropdownSelector(
                        label = stringResource(Res.string.scope),
                        items = scopeOptions,
                        selected = selectedScope,
                        onSelectedChange = { newValue ->
                            onScopeSelected(scopeOptions.indexOf(newValue).coerceAtLeast(0))
                        }
                    )
                    DropdownSelector(
                        label = stringResource(Res.string.mode),
                        items = modeOptions,
                        selected = selectedMode,
                        onSelectedChange = { newValue ->
                            onModeSelected(modeOptions.indexOf(newValue).coerceAtLeast(0))
                        }
                    )
                    Box(
                        modifier = Modifier.size(42.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                                onSubmit()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(painter = painterResource(IconRes.drawable.ic_search), contentDescription = stringResource(Res.string.search), modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                BibleSearchField(
                    value = searchQuery,
                    placeholder = searchPlaceholder,
                    onValueChange = onQueryChange,
                    onClear = onClear,
                    onSubmit = onSubmit,
                    onFocusChanged = onFocusChanged,
                    modeChip = { SearchModeChip(searchMode, onCycleSearchMode) },
                    modifier = Modifier.weight(1f)
                )
                DropdownSelector(
                    label = stringResource(Res.string.scope),
                    items = scopeOptions,
                    selected = selectedScope,
                    onSelectedChange = { newValue ->
                        onScopeSelected(scopeOptions.indexOf(newValue).coerceAtLeast(0))
                    }
                )
                DropdownSelector(
                    label = stringResource(Res.string.mode),
                    items = modeOptions,
                    selected = selectedMode,
                    onSelectedChange = { newValue ->
                        onModeSelected(modeOptions.indexOf(newValue).coerceAtLeast(0))
                    }
                )
                Box(
                    modifier = Modifier.size(42.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                            onSubmit()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(painter = painterResource(IconRes.drawable.ic_search), contentDescription = stringResource(Res.string.search), modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchModeChip(searchMode: BibleSearchMode, onCycle: () -> Unit, modifier: Modifier = Modifier) {
    val (label, container, content) = when (searchMode) {
        BibleSearchMode.AUTO -> Triple(
            Res.string.bible_search_mode_auto,
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.onPrimary
        )
        BibleSearchMode.REFERENCE -> Triple(
            Res.string.bible_search_mode_reference,
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.onSecondary
        )
        BibleSearchMode.TEXT -> Triple(
            Res.string.bible_search_mode_text,
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.onTertiary
        )
    }
    TooltipArea(
        tooltip = {
            Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) {
                Text(
                    text = stringResource(Res.string.bible_search_mode_tooltip),
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
    ) {
        Surface(
            onClick = onCycle,
            modifier = modifier,
            shape = MaterialTheme.shapes.small,
            color = container,
            contentColor = content
        ) {
            Text(
                text = stringResource(label),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.05.sp
                ),
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
            )
        }
    }
}
