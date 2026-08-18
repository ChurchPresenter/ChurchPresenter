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
import core.models.songs.SongField
import core.models.songs.SongItem

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
    val c = colors
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.width(width)
                .clip(RoundedCornerShape(LibraryMetrics.panelRadius))
                .background(c.surface)
                .border(1.dp, c.border, RoundedCornerShape(LibraryMetrics.panelRadius)),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                Box(
                    Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(c.accentSurface),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, null, tint = c.accentText, modifier = Modifier.size(15.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = LibraryType.bodyStrong, color = c.text)
                    Text(subtitle, style = LibraryType.small, color = c.textMuted)
                }
                Box(
                    Modifier.size(28.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(c.inputSurface)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Close, null, tint = c.textMuted, modifier = Modifier.size(12.dp))
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
            Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) { body() }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
            Row(
                Modifier.fillMaxWidth().background(c.background).padding(horizontal = 18.dp, vertical = 12.dp),
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
    val c = colors
    var name by remember { mutableStateOf("") }
    var assign by remember { mutableStateOf(selectedCount > 0) }
    val trimmed = name.trim().trim('/')
    val clash = trimmed.isNotEmpty() && existing.any { it.equals(trimmed, ignoreCase = true) }
    val invalid = trimmed.contains("..")
    val valid = trimmed.isNotEmpty() && !clash && !invalid

    LibraryDialog(
        icon = Icons.Default.LibraryBooks,
        title = Strings["new_song_book"],
        subtitle = Strings["new_song_book_subhead"],
        onDismiss = onDismiss,
        footer = {
            QuietButton(Strings["cancel"], onClick = onDismiss)
            PrimaryButton(Strings["create"], onClick = { onCreate(trimmed, assign) }, enabled = valid)
        },
    ) {
        Text(Strings["name"].uppercase(), style = LibraryType.columnHead, color = c.textFaint)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier.fillMaxWidth()
                .height(36.dp)
                .clip(RoundedCornerShape(LibraryMetrics.radius))
                .background(c.background)
                .border(
                    1.dp,
                    if (clash || invalid) c.dangerBorder else c.accentBorder,
                    RoundedCornerShape(LibraryMetrics.radius),
                )
                .padding(horizontal = 11.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            PlainTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = Strings["new_song_book_placeholder"],
            )
        }
        if (clash || invalid) {
            Spacer(Modifier.height(6.dp))
            Text(
                Strings[if (clash) "new_song_book_exists" else "new_song_book_invalid"],
                style = LibraryType.small,
                color = c.danger,
            )
        }
        if (selectedCount > 0) {
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(LibraryMetrics.radius))
                    .background(c.accentSurface)
                    .border(1.dp, c.accentBorder, RoundedCornerShape(LibraryMetrics.radius))
                    .clickable { assign = !assign }
                    .padding(horizontal = 11.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                LibraryCheckbox(checked = assign)
                Text(
                    Strings.format("new_song_book_assign", selectedCount),
                    style = LibraryType.body,
                    color = c.accentText,
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
    val c = colors
    val ticked = remember { mutableStateMapOf<SongField, Boolean>() }
    val values = remember { mutableStateMapOf<SongField, String>() }
    val fields = listOf(
        SongField.SONGBOOK, SongField.AUTHOR, SongField.COMPOSER, SongField.TUNE, SongField.CCLI,
    )
    val chosen = fields.filter { ticked[it] == true }.associateWith { values[it].orEmpty() }

    LibraryDialog(
        icon = Icons.Default.Edit,
        title = Strings["batch_edit"],
        subtitle = Strings.format("batch_edit_subhead", count),
        width = 520.dp,
        onDismiss = onDismiss,
        footer = {
            Text(
                if (chosen.isEmpty()) Strings["batch_edit_nothing"] else "",
                style = LibraryType.small,
                color = c.textFaint,
            )
            QuietButton(Strings["cancel"], onClick = onDismiss)
            PrimaryButton(
                Strings.format("batch_edit_apply", count),
                onClick = { onApply(chosen) },
                enabled = chosen.isNotEmpty(),
            )
        },
    ) {
        Text(Strings["batch_edit_hint"], style = LibraryType.small, color = c.textFaint)
        Spacer(Modifier.height(4.dp))
        fields.forEach { field ->
            val on = ticked[field] == true
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairline))
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
                        color = if (on) c.text else c.textMuted,
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
                            .background(if (on) c.background else c.surface)
                            .border(1.dp, if (on) c.accentBorder else c.hairline, RoundedCornerShape(8.dp))
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
    val c = colors
    var open by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth()
                .height(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (enabled) c.background else c.surface)
                .border(1.dp, if (enabled) c.accentBorder else c.hairline, RoundedCornerShape(8.dp))
                .clickable(enabled = enabled) { open = true }
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                value.ifBlank { Strings["no_song_book"] },
                style = LibraryType.body,
                color = if (enabled) c.text else c.textFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.ArrowDropDown, null, tint = c.textFaint, modifier = Modifier.size(13.dp))
        }
        if (open) {
            LibraryPopup(width = 260.dp, onDismiss = { open = false }) {
                MenuRow(Strings["no_song_book"], selected = value.isBlank()) {
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
    val c = colors
    LibraryDialog(
        icon = Icons.Default.Delete,
        title = if (songs.size == 1) Strings.format("delete_confirm_one", songs.single().title)
        else Strings.format("delete_confirm_title", Strings.format("song_count", songs.size)),
        subtitle = Strings["delete_confirm_body"],
        onDismiss = onDismiss,
        footer = {
            QuietButton(Strings["cancel"], onClick = onDismiss)
            DangerButton(Strings["delete_confirm_action"], onClick = onConfirm)
        },
    ) {
        songs.take(DELETE_PREVIEW).forEach { song ->
            Text(song.title, style = LibraryType.body, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (songs.size > DELETE_PREVIEW) {
            Text(
                Strings.format("song_count", songs.size - DELETE_PREVIEW),
                style = LibraryType.small,
                color = c.textFaint,
            )
        }
    }
}

/** The filled button for the one action that destroys something. */
@Composable
private fun DangerButton(label: String, onClick: () -> Unit) {
    val c = colors
    Row(
        Modifier.height(LibraryMetrics.control)
            .clip(RoundedCornerShape(LibraryMetrics.radius))
            .background(c.dangerSurface)
            .border(1.dp, c.dangerBorder, RoundedCornerShape(LibraryMetrics.radius))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = LibraryType.button, color = c.danger)
    }
}

private const val DELETE_PREVIEW = 6

private fun batchLabel(field: SongField): String = when (field) {
    SongField.SONGBOOK -> Strings["column_song_book"]
    SongField.AUTHOR -> Strings["column_author"]
    SongField.COMPOSER -> Strings["column_composer"]
    SongField.TUNE -> Strings["column_tune"]
    SongField.CCLI -> Strings["column_ccli"]
    else -> Strings["column_title"]
}
