package org.churchpresenter.songlibrary.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.songlibrary.generated.resources.Res
import org.churchpresenter.songlibrary.generated.resources.new_song_book_menu
import org.churchpresenter.songlibrary.generated.resources.no_song_book

/**
 * A cell that is text until it is clicked, and a field while it is being typed in.
 *
 * Committing on Enter and on losing focus, and abandoning on Escape, is what a grid is expected to
 * do — and it is what keeps a mistyped number from being written just because the row scrolled out
 * of sight.
 */
@Composable
fun EditableCell(value: String, strong: Boolean = false, onCommit: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var editing by remember(value) { mutableStateOf(false) }
    var draft by remember(value) { mutableStateOf(value) }
    var everFocused by remember(value) { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    if (!editing) {
        Box(
            Modifier.fillMaxWidth()
                .height(LibraryMetrics.rowHeight)
                .clickable {
                    draft = value
                    editing = true
                }
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                value,
                style = if (strong) LibraryType.bodyStrong else LibraryType.body,
                color =
                    if (value.isBlank()) scheme.onSurfaceVariant.copy(alpha = FAINT_TEXT_ALPHA) else scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        return
    }

    fun commit() {
        editing = false
        if (draft != value) onCommit(draft)
    }

    Box(
        Modifier.fillMaxWidth().height(LibraryMetrics.rowHeight).padding(horizontal = 4.dp, vertical = 4.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        PlainTextField(
            value = draft,
            onValueChange = { draft = it },
            style = if (strong) LibraryType.bodyStrong else LibraryType.body,
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(scheme.background)
                .border(1.5.dp, scheme.primary, RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 5.dp),
            textModifier = Modifier.focusRequester(focus)
                // Only once it HAS been focused. `onFocusChanged` also fires as the modifier
                // attaches, unfocused, before the LaunchedEffect below has asked for focus --
                // committing on that one closed the field on the frame it opened, so clicking a
                // cell flashed a box and put the text straight back.
                .onFocusChanged { state ->
                    if (state.isFocused) everFocused = true else if (everFocused && editing) commit()
                }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.Enter, Key.NumPadEnter -> {
                            commit()
                            true
                        }
                        // Escape abandons what was typed, which is the way back out of a mistake.
                        Key.Escape -> {
                            draft = value
                            editing = false
                            true
                        }
                        else -> false
                    }
                },
        )
    }

    LaunchedEffect(Unit) { focus.requestFocus() }
}

/**
 * The song book cell: a menu of the books that exist, rather than a field to mistype one into.
 *
 * Typing a name here would make a new song book by accident on every typo, since a book is a
 * folder — so a new one is asked for explicitly, through the same dialog the header offers.
 */
@Composable
fun SongbookCell(
    value: String,
    songbooks: List<String>,
    onPick: (String) -> Unit,
    onNewBook: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    MenuAnchorBox { menuMaxHeight ->
        Row(
            Modifier.fillMaxWidth()
                .height(LibraryMetrics.rowHeight)
                .clickable { open = true }
                .padding(start = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                value.ifBlank { stringResource(Res.string.no_song_book) },
                style = LibraryType.body,
                color =
                    if (value.isBlank()) scheme.onSurfaceVariant.copy(alpha = FAINT_TEXT_ALPHA) else scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Box(Modifier.weight(1f))
            Icon(
                Icons.Default.ArrowDropDown,
                null,
                tint = scheme.onSurfaceVariant.copy(alpha = FAINT_TEXT_ALPHA),
                modifier = Modifier.size(13.dp),
            )
        }
        if (open) {
            LibraryPopup(width = 250.dp, maxHeight = menuMaxHeight, onDismiss = { open = false }) {
                MenuRow(stringResource(Res.string.no_song_book), selected = value.isBlank()) {
                    onPick("")
                    open = false
                }
                songbooks.forEach { book ->
                    MenuRow(book, selected = book == value) {
                        onPick(book)
                        open = false
                    }
                }
                MenuDivider()
                MenuRow(
                    label = stringResource(Res.string.new_song_book_menu),
                    accent = true,
                    leading = { Icon(Icons.Default.Add, null, tint = scheme.primary, modifier = Modifier.size(11.dp)) },
                ) {
                    open = false
                    onNewBook()
                }
            }
        }
    }
}
