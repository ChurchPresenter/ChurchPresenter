package org.churchpresenter.dictionary

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import org.churchpresenter.theme.AppShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import org.churchpresenter.theme.components.KeyIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.focusRequester
import org.churchpresenter.sharedui.composables.SearchFieldFocus
import org.churchpresenter.sharedui.composables.rememberSearchFieldValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.book
import org.churchpresenter.strings.generated.resources.chapter
import org.churchpresenter.icons.generated.resources.ic_close
import org.churchpresenter.icons.generated.resources.ic_search
import org.churchpresenter.strings.generated.resources.search_clear
import org.churchpresenter.strings.generated.resources.verse
import org.churchpresenter.theme.components.DropdownSelector
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.theme.elevationPalette
import org.churchpresenter.theme.hoverTint
import org.churchpresenter.theme.sunken

@Composable
internal fun InScriptureBookDropdown(
    allBooksLabel: String,
    selectedBookId: Int?,
    availableBooks: List<Int>,
    getBookName: ((bookId: Int) -> String?)?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf("" to allBooksLabel) +
        availableBooks.map { it.toString() to (getBookName?.invoke(it) ?: "Book $it") }
    DropdownSelector(
        label = stringResource(Res.string.book),
        value = selectedBookId?.toString() ?: "",
        options = options,
        onValueChange = { onSelect(it.toIntOrNull()) },
        modifier = modifier,
    )
}

@Composable
internal fun InScriptureChapterDropdown(
    allChaptersLabel: String,
    selectedChapter: Int?,
    availableChapters: List<Int>,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf("" to allChaptersLabel) +
        availableChapters.map { it.toString() to it.toString() }
    DropdownSelector(
        label = stringResource(Res.string.chapter),
        value = selectedChapter?.toString() ?: "",
        options = options,
        onValueChange = { onSelect(it.toIntOrNull()) },
        modifier = modifier,
    )
}

@Composable
internal fun InScriptureVerseDropdown(
    allVersesLabel: String,
    selectedVerse: Int?,
    availableVerses: List<Int>,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf("" to allVersesLabel) +
        availableVerses.map { it.toString() to it.toString() }
    DropdownSelector(
        label = stringResource(Res.string.verse),
        value = selectedVerse?.toString() ?: "",
        options = options,
        onValueChange = { onSelect(it.toIntOrNull()) },
        modifier = modifier,
    )
}

@Composable
internal fun DictionarySearchField(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier,
    /** How the tab moves the keyboard in here with the query selected. */
    focus: SearchFieldFocus = remember { SearchFieldFocus() },
) {
    Row(
        modifier = modifier
            .height(42.dp)
            .sunken(AppShape(8.dp), elevationPalette())
            .hoverTint(AppShape(8.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(IconRes.drawable.ic_search),
            contentDescription = null,
            modifier = Modifier.padding(start = 11.dp).size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        )
        Box(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
            var fieldValue by rememberSearchFieldValue(value, focus)
            BasicTextField(
                value = fieldValue,
                onValueChange = {
                    fieldValue = it
                    if (it.text != value) onValueChange(it.text)
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().focusRequester(focus.requester),
                decorationBox = { innerTextField ->
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
            )
        }
        if (value.isNotEmpty()) {
            KeyIconButton(onClick = onClear, modifier = Modifier.size(30.dp)) {
                Icon(
                    painter = painterResource(IconRes.drawable.ic_close),
                    contentDescription = stringResource(Res.string.search_clear),
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
