package org.churchpresenter.serverui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.server.CalendarJoin
import org.churchpresenter.server.JoinParse
import org.churchpresenter.settings.CalendarSyncSettings
import org.churchpresenter.sharedui.composables.tabMovesFocus
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.calendar_computer_add_create
import org.churchpresenter.strings.generated.resources.calendar_computer_add_failed
import org.churchpresenter.strings.generated.resources.calendar_computer_add_follower
import org.churchpresenter.strings.generated.resources.calendar_computer_add_follower_hint
import org.churchpresenter.strings.generated.resources.calendar_computer_add_name
import org.churchpresenter.strings.generated.resources.calendar_computer_add_name_placeholder
import org.churchpresenter.strings.generated.resources.calendar_computer_add_role
import org.churchpresenter.strings.generated.resources.calendar_computer_add_subtitle
import org.churchpresenter.strings.generated.resources.calendar_computer_add_title
import org.churchpresenter.strings.generated.resources.calendar_computer_add_viewer
import org.churchpresenter.strings.generated.resources.calendar_computer_add_viewer_hint
import org.churchpresenter.strings.generated.resources.calendar_computer_code_for
import org.churchpresenter.strings.generated.resources.calendar_computer_code_for_default
import org.churchpresenter.strings.generated.resources.calendar_computer_code_subtitle
import org.churchpresenter.strings.generated.resources.calendar_computer_copied
import org.churchpresenter.strings.generated.resources.calendar_computer_copy_code
import org.churchpresenter.strings.generated.resources.calendar_computer_done
import org.churchpresenter.strings.generated.resources.calendar_computer_email
import org.churchpresenter.strings.generated.resources.calendar_computer_email_body
import org.churchpresenter.strings.generated.resources.calendar_computer_email_subject
import org.churchpresenter.strings.generated.resources.calendar_computer_join_ready
import org.churchpresenter.strings.generated.resources.calendar_computer_save_file
import org.churchpresenter.strings.generated.resources.calendar_computer_saved
import org.churchpresenter.strings.generated.resources.calendar_join_body
import org.churchpresenter.strings.generated.resources.calendar_join_code
import org.churchpresenter.strings.generated.resources.calendar_join_expired
import org.churchpresenter.strings.generated.resources.calendar_join_failed
import org.churchpresenter.strings.generated.resources.calendar_join_file_type
import org.churchpresenter.strings.generated.resources.calendar_join_hint_empty
import org.churchpresenter.strings.generated.resources.calendar_join_invalid
import org.churchpresenter.strings.generated.resources.calendar_join_join
import org.churchpresenter.strings.generated.resources.calendar_join_open_file
import org.churchpresenter.strings.generated.resources.calendar_join_paste
import org.churchpresenter.strings.generated.resources.calendar_join_replaces
import org.churchpresenter.strings.generated.resources.calendar_join_replaces_title
import org.churchpresenter.strings.generated.resources.calendar_join_title
import org.churchpresenter.strings.generated.resources.calendar_join_valid
import org.churchpresenter.strings.generated.resources.cancel
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.components.GhostButton
import org.churchpresenter.theme.components.KeyButton
import org.churchpresenter.theme.components.RaisedRadioButton
import org.churchpresenter.theme.components.SettingsTextField
import org.churchpresenter.theme.elevationPalette
import org.churchpresenter.theme.keyboardFocusRing
import org.churchpresenter.theme.semantic
import org.churchpresenter.theme.sunken
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import java.io.File
import java.net.URLEncoder
import java.time.Instant
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Adding a computer, in two steps: its name and what it may do, then the join code to hand over --
 * copied, saved to a file, or put in an email from the user's own mail app.
 */
@Composable
internal fun AddComputerDialog(
    onCreate: suspend (role: String, name: String) -> CalendarJoin?,
    onDismiss: () -> Unit,
) {
    CalendarDialog(title = stringResource(Res.string.calendar_computer_add_title), onDismiss = onDismiss) {
        AddComputerContent(onCreate, onDismiss)
    }
}

/** What [AddComputerDialog] holds, without its window -- what a test draws. */
@Composable
fun AddComputerContent(
    onCreate: suspend (role: String, name: String) -> CalendarJoin?,
    onDismiss: () -> Unit,
    io: JoinCodeIo = SystemJoinCodeIo,
) {
    var join by remember { mutableStateOf<CalendarJoin?>(null) }
    var name by remember { mutableStateOf("") }
    val ready = join
    Column {
        DialogHeader(
            icon = Icons.Outlined.Computer,
            tint = MaterialTheme.semantic.contentSongs,
            title = stringResource(Res.string.calendar_computer_add_title),
            subtitle = stringResource(
                if (ready == null) {
                    Res.string.calendar_computer_add_subtitle
                } else {
                    Res.string.calendar_computer_code_subtitle
                },
            ),
        ) { StepDots(step = if (ready == null) 1 else 2) }
        if (ready == null) {
            AddComputerForm(name = name, onNameChange = { name = it }, onCreate = onCreate, onCancel = onDismiss) {
                join = it
            }
        } else {
            JoinReady(ready, name.trim(), io, onDone = onDismiss)
        }
    }
}

@Composable
private fun AddComputerForm(
    name: String,
    onNameChange: (String) -> Unit,
    onCreate: suspend (role: String, name: String) -> CalendarJoin?,
    onCancel: () -> Unit,
    onCreated: (CalendarJoin) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var role by remember { mutableStateOf(CalendarSyncSettings.ROLE_FOLLOWER) }
    var working by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    DialogBody {
        SettingsTextField(
            value = name,
            onValueChange = onNameChange,
            label = stringResource(Res.string.calendar_computer_add_name),
            placeholder = { Text(stringResource(Res.string.calendar_computer_add_name_placeholder)) },
            fillWidth = true,
        )
        FieldHeading(stringResource(Res.string.calendar_computer_add_role), Modifier.padding(top = 6.dp))
        RoleCard(
            value = CalendarSyncSettings.ROLE_FOLLOWER,
            selected = role,
            icon = Icons.Outlined.Edit,
            tint = MaterialTheme.semantic.info,
            label = Res.string.calendar_computer_add_follower,
            hint = Res.string.calendar_computer_add_follower_hint,
        ) { role = it }
        RoleCard(
            value = CalendarSyncSettings.ROLE_VIEWER,
            selected = role,
            icon = Icons.Outlined.Visibility,
            tint = MaterialTheme.semantic.success,
            label = Res.string.calendar_computer_add_viewer,
            hint = Res.string.calendar_computer_add_viewer_hint,
        ) { role = it }
        if (failed) {
            Text(
                stringResource(Res.string.calendar_computer_add_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
    DialogFooter {
        GhostButton(onClick = onCancel, shape = AppShape(8.dp)) {
            Text(stringResource(Res.string.cancel), style = MaterialTheme.typography.labelLarge)
        }
        DialogPrimaryButton(
            text = stringResource(Res.string.calendar_computer_add_create),
            enabled = !working && name.isNotBlank(),
            onClick = {
                working = true
                failed = false
                scope.launch {
                    val created = onCreate(role, name.trim())
                    working = false
                    if (created == null) failed = true else onCreated(created)
                }
            },
        )
    }
}

/** One role as a card to pick: its chip, its name and what it may do, and the radio dot. */
@Composable
private fun RoleCard(
    value: String,
    selected: String,
    icon: ImageVector,
    tint: Color,
    label: StringResource,
    hint: StringResource,
    onSelect: (String) -> Unit,
) {
    val on = value == selected
    val primary = MaterialTheme.colorScheme.primary
    val shape = AppShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .keyboardFocusRing(shape)
            .background(if (on) primary.copy(alpha = SELECTED_ALPHA) else Color.Transparent, shape)
            .border(1.5.dp, if (on) primary else MaterialTheme.colorScheme.outlineVariant, shape)
            .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(value) })
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconChip(icon, tint, size = 32.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(label), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            DimText(stringResource(hint))
        }
        RaisedRadioButton(selected = on, onClick = null)
    }
}

/** The join code made, Copy built into its box, and the other two ways to hand it over. */
@Composable
private fun JoinReady(join: CalendarJoin, name: String, io: JoinCodeIo, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val code = remember(join) { join.toCode() }
    var note by remember { mutableStateOf("") }
    var copied by remember { mutableStateOf(false) }
    val saveTitle = stringResource(Res.string.calendar_computer_save_file)
    val filter = joinFileFilter()
    val savedFormat = stringResource(Res.string.calendar_computer_saved, "%s")
    val subject = stringResource(Res.string.calendar_computer_email_subject)
    val body = stringResource(Res.string.calendar_computer_email_body, code)
    DialogBody {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldHeading(
                stringResource(
                    Res.string.calendar_computer_code_for,
                    name.ifBlank { stringResource(Res.string.calendar_computer_code_for_default) },
                ),
                Modifier.weight(1f),
            )
            ExpiryChip()
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLowest, AppShape(10.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, AppShape(10.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                code,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = CODE_LINES,
            )
            val success = MaterialTheme.semantic.success
            KeyButton(
                shape = AppShape(8.dp),
                contentPadding = COMPACT_PADDING,
                modifier = Modifier.height(28.dp),
                onClick = { if (io.copy(code)) copied = true },
            ) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(
                        if (copied) Res.string.calendar_computer_copied else Res.string.calendar_computer_copy_code,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (copied) success else Color.Unspecified,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WideKey(text = saveTitle, icon = Icons.Outlined.FolderOpen) {
                scope.launch { io.save(join, saveTitle, filter)?.let { note = savedFormat.format(it) } }
            }
            WideKey(text = stringResource(Res.string.calendar_computer_email), icon = Icons.Outlined.MailOutline) {
                io.email("mailto:?subject=${mailEncode(subject)}&body=${mailEncode(body)}")
            }
        }
        if (note.isNotEmpty()) {
            Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.semantic.success)
        }
        Text(
            stringResource(Res.string.calendar_computer_join_ready),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, AppShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
    DialogFooter {
        DialogPrimaryButton(stringResource(Res.string.calendar_computer_done), onClick = onDone)
    }
}

/**
 * Joining another computer's calendar from its code or file. [replacesOwn] when this computer is
 * the main one of a calendar of its own, which joining ends.
 */
@Composable
internal fun JoinCalendarDialog(
    replacesOwn: Boolean,
    onJoin: suspend (CalendarJoin) -> Boolean,
    onDismiss: () -> Unit,
) {
    CalendarDialog(title = stringResource(Res.string.calendar_join_title), onDismiss = onDismiss) {
        JoinCalendarContent(replacesOwn, onJoin, onDismiss)
    }
}

/** What [JoinCalendarDialog] holds, without its window -- what a test draws. */
@Composable
internal fun JoinCalendarContent(
    replacesOwn: Boolean,
    onJoin: suspend (CalendarJoin) -> Boolean,
    onDismiss: () -> Unit,
    io: JoinCodeIo = SystemJoinCodeIo,
) {
    Column {
        val scope = rememberCoroutineScope()
        var text by remember { mutableStateOf("") }
        var file by remember { mutableStateOf<File?>(null) }
        var failed by remember { mutableStateOf(false) }
        var working by remember { mutableStateOf(false) }
        val openTitle = stringResource(Res.string.calendar_join_open_file)
        val filter = joinFileFilter()
        val parsed = remember(text) { if (text.isBlank()) null else CalendarJoin.parse(text.trim(), Instant.now()) }
        DialogHeader(
            icon = Icons.Outlined.CalendarMonth,
            tint = MaterialTheme.semantic.info,
            title = stringResource(Res.string.calendar_join_title),
            subtitle = stringResource(Res.string.calendar_join_body),
        )
        DialogBody {
            CodeField(
                text = text,
                parsed = parsed,
                onTextChange = { text = it; file = null; failed = false },
                onPaste = { io.paste()?.let { text = it.trim(); file = null; failed = false } },
                onOpenFile = {
                    scope.launch {
                        io.open(openTitle, filter)?.let { (read, opened) ->
                            text = read
                            file = opened
                            failed = false
                        }
                    }
                },
            )
            if (replacesOwn) {
                Banner(
                    tint = MaterialTheme.semantic.warning,
                    icon = Icons.Outlined.WarningAmber,
                    title = stringResource(Res.string.calendar_join_replaces_title),
                    body = {
                        Text(
                            stringResource(Res.string.calendar_join_replaces),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }
            if (failed) {
                Text(
                    stringResource(Res.string.calendar_join_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        DialogFooter {
            GhostButton(onClick = onDismiss, shape = AppShape(8.dp)) {
                Text(stringResource(Res.string.cancel), style = MaterialTheme.typography.labelLarge)
            }
            DialogPrimaryButton(
                text = stringResource(Res.string.calendar_join_join),
                enabled = !working && parsed is JoinParse.Ok,
                onClick = {
                    val ok = parsed as? JoinParse.Ok ?: return@DialogPrimaryButton
                    working = true
                    failed = false
                    scope.launch {
                        val joined = onJoin(ok.join)
                        working = false
                        if (joined) {
                            // The file did its job; left in a shared folder it is a key lying about.
                            file?.let { withContext(Dispatchers.IO) { runCatching { it.delete() } } }
                            onDismiss()
                        } else {
                            failed = true
                        }
                    }
                },
            )
        }
    }
}

/** The code box: what was pasted, whether it reads as a code, and Paste and Open file under it. */
@Composable
private fun CodeField(
    text: String,
    parsed: JoinParse?,
    onTextChange: (String) -> Unit,
    onPaste: () -> Unit,
    onOpenFile: () -> Unit,
) {
    val palette = elevationPalette()
    val shape = AppShape(10.dp)
    val error = MaterialTheme.colorScheme.error
    val (hint, hintColor) = when (parsed) {
        null -> stringResource(Res.string.calendar_join_hint_empty, CalendarJoin.CODE_PREFIX) to
            MaterialTheme.colorScheme.onSurfaceVariant
        is JoinParse.Ok -> stringResource(Res.string.calendar_join_valid) to MaterialTheme.semantic.success
        JoinParse.Expired -> stringResource(Res.string.calendar_join_expired) to error
        JoinParse.Invalid -> stringResource(Res.string.calendar_join_invalid) to error
    }
    val label = stringResource(Res.string.calendar_join_code)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .sunken(shape, palette, rim = if (parsed == null || parsed is JoinParse.Ok) Color.Unspecified else error)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box {
            if (text.isEmpty()) {
                Text(
                    "${CalendarJoin.CODE_PREFIX}…",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = PLACEHOLDER_ALPHA),
                )
            }
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                maxLines = CODE_LINES,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp)
                    .tabMovesFocus()
                    .semantics { contentDescription = label },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                hint,
                style = MaterialTheme.typography.labelSmall,
                color = hintColor,
                modifier = Modifier.weight(1f),
            )
            TokenButton(stringResource(Res.string.calendar_join_paste), onPaste)
            TokenButton(stringResource(Res.string.calendar_join_open_file), onOpenFile)
        }
    }
}

@Composable
private fun joinFileFilter(): FileNameExtensionFilter {
    val label = stringResource(Res.string.calendar_join_file_type)
    return remember(label) { FileNameExtensionFilter(label, CalendarJoin.FILE_EXTENSION) }
}

/** A `mailto:` parameter: percent-encoded, with spaces as `%20` -- mail apps do not read `+` as one. */
private fun mailEncode(text: String): String =
    URLEncoder.encode(text, Charsets.UTF_8).replace("+", "%20")

private const val CODE_LINES = 4
private const val SELECTED_ALPHA = 0.10f
private const val PLACEHOLDER_ALPHA = 0.6f
