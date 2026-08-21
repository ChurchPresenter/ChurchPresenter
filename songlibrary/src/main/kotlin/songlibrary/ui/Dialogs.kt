package songlibrary.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.churchpresenter.app.churchpresenter.models.songs.SongField
import org.churchpresenter.app.churchpresenter.models.songs.SongItem
import org.jetbrains.compose.resources.stringResource
import songlibrary.generated.resources.Res
import songlibrary.generated.resources.new_song_book_invalid
import songlibrary.generated.resources.new_song_book_exists
import songlibrary.generated.resources.batch_edit
import songlibrary.generated.resources.batch_edit_apply
import songlibrary.generated.resources.batch_edit_hint
import songlibrary.generated.resources.batch_edit_nothing
import songlibrary.generated.resources.batch_edit_subhead
import songlibrary.generated.resources.cancel
import songlibrary.generated.resources.column_author
import songlibrary.generated.resources.column_ccli
import songlibrary.generated.resources.column_composer
import songlibrary.generated.resources.column_song_book
import songlibrary.generated.resources.column_title
import songlibrary.generated.resources.column_tune
import songlibrary.generated.resources.create
import songlibrary.generated.resources.delete_confirm_action
import songlibrary.generated.resources.delete_confirm_body
import songlibrary.generated.resources.delete_confirm_one
import songlibrary.generated.resources.delete_confirm_title
import songlibrary.generated.resources.name
import songlibrary.generated.resources.new_song_book
import songlibrary.generated.resources.new_song_book_assign
import songlibrary.generated.resources.new_song_book_placeholder
import songlibrary.generated.resources.new_song_book_subhead
import songlibrary.generated.resources.no_song_book
import songlibrary.generated.resources.song_count

/**
 * The panel every dialog in this window is: an icon, a title and a line under it, a body, and a
 * footer holding the one action it is for.
 */
@Composable
fun LibraryDialog(
    icon: ImageVector,
    title: String,
    subtitle: String,
    width: Dp = 420.dp,
    onDismiss: () -> Unit,
    footer: @Composable RowScopeFooter.() -> Unit,
    body: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.width(width)
                .clip(RoundedCornerShape(LibraryMetrics.panelRadius))
                .background(scheme.surfaceContainer)
                .border(1.dp, scheme.outlineVariant, RoundedCornerShape(LibraryMetrics.panelRadius)),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                Box(
                    Modifier.size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(scheme.primary.copy(alpha = ACCENT_SURFACE_ALPHA)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, null, tint = scheme.primary, modifier = Modifier.size(15.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = LibraryType.bodyStrong, color = scheme.onSurface)
                    Text(subtitle, style = LibraryType.small, color = scheme.onSurfaceVariant)
                }
                Box(
                    Modifier.size(28.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(scheme.surfaceContainerHigh)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Close, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(scheme.outlineVariant))
            Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) { body() }
            Box(Modifier.fillMaxWidth().height(1.dp).background(scheme.outlineVariant))
            Row(
                Modifier.fillMaxWidth().background(scheme.background).padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Spacer(Modifier.weight(1f))
                RowScopeFooter.footer()
            }
        }
    }
}

/** Marker for the footer slot, so a dialog's buttons cannot be written anywhere else. */
object RowScopeFooter

/**
 * Making a song book, which is making a folder.
 *
 * It is offered from three places — the filter, a song book cell and the batch editor — because in
 * each of them the moment a person wants a new one is the moment they went looking for it and it
 * was not there.
 */
@Composable
fun NewSongBookDialog(
    existing: List<String>,
    selectedCount: Int,
    onDismiss: () -> Unit,
    onCreate: (name: String, assignSelected: Boolean) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var name by remember { mutableStateOf("") }
    var assign by remember { mutableStateOf(selectedCount > 0) }
    val trimmed = name.trim().trim('/')
    val clash = trimmed.isNotEmpty() && existing.any { it.equals(trimmed, ignoreCase = true) }
    val invalid = trimmed.contains("..")
    val valid = trimmed.isNotEmpty() && !clash && !invalid

    LibraryDialog(
        icon = Icons.Default.LibraryBooks,
        title = stringResource(Res.string.new_song_book),
        subtitle = stringResource(Res.string.new_song_book_subhead),
        onDismiss = onDismiss,
        footer = {
            QuietButton(stringResource(Res.string.cancel), onClick = onDismiss)
            PrimaryButton(stringResource(Res.string.create), onClick = { onCreate(trimmed, assign) }, enabled = valid)
        },
    ) {
        Text(
            stringResource(Res.string.name).uppercase(),
            style = LibraryType.columnHead,
            color = scheme.onSurfaceVariant.copy(alpha = FAINT_TEXT_ALPHA),
        )
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier.fillMaxWidth()
                .height(36.dp)
                .clip(RoundedCornerShape(LibraryMetrics.radius))
                .background(scheme.background)
                .border(
                    1.dp,
                    if (clash || invalid) scheme.error else scheme.primary.copy(alpha = ACCENT_BORDER_ALPHA),
                    RoundedCornerShape(LibraryMetrics.radius),
                )
                .padding(horizontal = 11.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            PlainTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = stringResource(Res.string.new_song_book_placeholder),
            )
        }
        if (clash || invalid) {
            Spacer(Modifier.height(6.dp))
            Text(
                if (clash) stringResource(Res.string.new_song_book_exists)
                else stringResource(Res.string.new_song_book_invalid),
                style = LibraryType.small,
                color = scheme.error,
            )
        }
        if (selectedCount > 0) {
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(LibraryMetrics.radius))
                    .background(scheme.primary.copy(alpha = ACCENT_SURFACE_ALPHA))
                    .border(
                        1.dp,
                        scheme.primary.copy(alpha = ACCENT_BORDER_ALPHA),
                        RoundedCornerShape(LibraryMetrics.radius),
                    )
                    .clickable { assign = !assign }
                    .padding(horizontal = 11.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                LibraryCheckbox(checked = assign)
                Text(
                    stringResource(Res.string.new_song_book_assign, selectedCount),
                    style = LibraryType.body,
                    color = scheme.primary,
                )
            }
        }
    }
}

/**
 * Overwriting one field on every selected song.
 *
 * Each field is ticked before it applies, and an unticked one is left alone — so a blank in a box
 * nobody ticked cannot quietly clear the composer on two hundred songs, while a blank in one that
 * *was* ticked clears it deliberately, which is a thing people mean to do.
 */
@Composable
fun BatchEditDialog(
    count: Int,
    songbooks: List<String>,
    onDismiss: () -> Unit,
    onApply: (Map<SongField, String>) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val ticked = remember { mutableStateMapOf<SongField, Boolean>() }
    val values = remember { mutableStateMapOf<SongField, String>() }
    val fields = listOf(
        SongField.SONGBOOK, SongField.AUTHOR, SongField.COMPOSER, SongField.TUNE, SongField.CCLI,
    )
    val chosen = fields.filter { ticked[it] == true }.associateWith { values[it].orEmpty() }

    LibraryDialog(
        icon = Icons.Default.Edit,
        title = stringResource(Res.string.batch_edit),
        subtitle = stringResource(Res.string.batch_edit_subhead, count),
        width = 520.dp,
        onDismiss = onDismiss,
        footer = {
            Text(
                if (chosen.isEmpty()) stringResource(Res.string.batch_edit_nothing) else "",
                style = LibraryType.small,
                color = scheme.onSurfaceVariant.copy(alpha = FAINT_TEXT_ALPHA),
            )
            QuietButton(stringResource(Res.string.cancel), onClick = onDismiss)
            PrimaryButton(
                stringResource(Res.string.batch_edit_apply, count),
                onClick = { onApply(chosen) },
                enabled = chosen.isNotEmpty(),
            )
        },
    ) {
        Text(
            stringResource(Res.string.batch_edit_hint),
            style = LibraryType.small,
            color = scheme.onSurfaceVariant.copy(alpha = FAINT_TEXT_ALPHA),
        )
        Spacer(Modifier.height(4.dp))
        fields.forEach { field ->
            val on = ticked[field] == true
            Box(Modifier.fillMaxWidth().height(1.dp).background(scheme.onSurface.copy(alpha = HAIRLINE_ALPHA)))
            Row(
                Modifier.fillMaxWidth().padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                Row(
                    Modifier.width(126.dp).clickable { ticked[field] = !on },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    LibraryCheckbox(checked = on)
                    Text(
                        batchLabel(field),
                        style = LibraryType.bodyStrong,
                        color = if (on) scheme.onSurface else scheme.onSurfaceVariant,
                    )
                }
                if (field == SongField.SONGBOOK) {
                    BatchSongbookField(
                        value = values[field].orEmpty(),
                        songbooks = songbooks,
                        enabled = on,
                        onPick = { values[field] = it },
                    )
                } else {
                    Box(
                        Modifier.fillMaxWidth()
                            .height(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (on) scheme.background else scheme.surfaceContainer)
                            .border(
                                1.dp,
                                if (on) scheme.primary.copy(alpha = ACCENT_BORDER_ALPHA)
                                else scheme.onSurface.copy(alpha = HAIRLINE_ALPHA),
                                RoundedCornerShape(8.dp),
                            )
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (on) {
                            PlainTextField(values[field].orEmpty(), { values[field] = it })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BatchSongbookField(
    value: String,
    songbooks: List<String>,
    enabled: Boolean,
    onPick: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    MenuAnchorBox(Modifier.fillMaxWidth()) { menuMaxHeight ->
        Row(
            Modifier.fillMaxWidth()
                .height(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (enabled) scheme.background else scheme.surfaceContainer)
                .border(
                    1.dp,
                    if (enabled) scheme.primary.copy(alpha = ACCENT_BORDER_ALPHA)
                    else scheme.onSurface.copy(alpha = HAIRLINE_ALPHA),
                    RoundedCornerShape(8.dp),
                )
                .clickable(enabled = enabled) { open = true }
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                value.ifBlank { stringResource(Res.string.no_song_book) },
                style = LibraryType.body,
                color = if (enabled) scheme.onSurface else scheme.onSurfaceVariant.copy(alpha = FAINT_TEXT_ALPHA),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Default.ArrowDropDown,
                null,
                tint = scheme.onSurfaceVariant.copy(alpha = FAINT_TEXT_ALPHA),
                modifier = Modifier.size(13.dp),
            )
        }
        if (open) {
            LibraryPopup(width = 260.dp, maxHeight = menuMaxHeight, onDismiss = { open = false }) {
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
            }
        }
    }
}

/**
 * Asking before a delete, which is the one thing here that a Revert cannot undo.
 *
 * Every other change in this window waits for Save; a delete takes the file straight away, so it is
 * the only one that asks first.
 */
@Composable
fun DeleteConfirmDialog(songs: List<SongItem>, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    LibraryDialog(
        icon = Icons.Default.Delete,
        title = if (songs.size == 1) stringResource(Res.string.delete_confirm_one, songs.single().title)
        else stringResource(Res.string.delete_confirm_title, stringResource(Res.string.song_count, songs.size)),
        subtitle = stringResource(Res.string.delete_confirm_body),
        onDismiss = onDismiss,
        footer = {
            QuietButton(stringResource(Res.string.cancel), onClick = onDismiss)
            DangerButton(stringResource(Res.string.delete_confirm_action), onClick = onConfirm)
        },
    ) {
        songs.take(DELETE_PREVIEW).forEach { song ->
            Text(
                song.title,
                style = LibraryType.body,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (songs.size > DELETE_PREVIEW) {
            Text(
                stringResource(Res.string.song_count, songs.size - DELETE_PREVIEW),
                style = LibraryType.small,
                color = scheme.onSurfaceVariant.copy(alpha = FAINT_TEXT_ALPHA),
            )
        }
    }
}

/** The filled button for the one action that destroys something. */
@Composable
private fun DangerButton(label: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.height(LibraryMetrics.control)
            .clip(RoundedCornerShape(LibraryMetrics.radius))
            .background(scheme.errorContainer)
            .border(1.dp, scheme.error, RoundedCornerShape(LibraryMetrics.radius))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = LibraryType.button, color = scheme.onErrorContainer)
    }
}

private const val DELETE_PREVIEW = 6

@Composable
private fun batchLabel(field: SongField): String = when (field) {
    SongField.SONGBOOK -> stringResource(Res.string.column_song_book)
    SongField.AUTHOR -> stringResource(Res.string.column_author)
    SongField.COMPOSER -> stringResource(Res.string.column_composer)
    SongField.TUNE -> stringResource(Res.string.column_tune)
    SongField.CCLI -> stringResource(Res.string.column_ccli)
    else -> stringResource(Res.string.column_title)
}
