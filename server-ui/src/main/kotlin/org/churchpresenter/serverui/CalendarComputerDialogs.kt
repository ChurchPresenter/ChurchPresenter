package org.churchpresenter.serverui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.server.CalendarJoin
import org.churchpresenter.server.JoinParse
import org.churchpresenter.settings.CalendarSyncSettings
import org.churchpresenter.sharedui.filechooser.SwingFileChooser
import org.churchpresenter.sharedui.utils.AppWindowRoot
import org.churchpresenter.sharedui.utils.LocalMainWindowState
import org.churchpresenter.sharedui.utils.SystemClipboard
import org.churchpresenter.sharedui.utils.UrlOpener
import org.churchpresenter.sharedui.utils.centeredOnMainWindow
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.calendar_computer_add_create
import org.churchpresenter.strings.generated.resources.calendar_computer_add_failed
import org.churchpresenter.strings.generated.resources.calendar_computer_add_follower
import org.churchpresenter.strings.generated.resources.calendar_computer_add_name
import org.churchpresenter.strings.generated.resources.calendar_computer_add_title
import org.churchpresenter.strings.generated.resources.calendar_computer_add_viewer
import org.churchpresenter.strings.generated.resources.calendar_computer_copied
import org.churchpresenter.strings.generated.resources.calendar_computer_copy_code
import org.churchpresenter.strings.generated.resources.calendar_computer_email
import org.churchpresenter.strings.generated.resources.calendar_computer_email_body
import org.churchpresenter.strings.generated.resources.calendar_computer_email_subject
import org.churchpresenter.strings.generated.resources.calendar_computer_join_ready
import org.churchpresenter.strings.generated.resources.calendar_computer_save_file
import org.churchpresenter.strings.generated.resources.calendar_computer_saved
import org.churchpresenter.strings.generated.resources.calendar_join_body
import org.churchpresenter.strings.generated.resources.calendar_join_code
import org.churchpresenter.strings.generated.resources.calendar_join_expired
import org.churchpresenter.strings.generated.resources.calendar_join_file_type
import org.churchpresenter.strings.generated.resources.calendar_join_failed
import org.churchpresenter.strings.generated.resources.calendar_join_invalid
import org.churchpresenter.strings.generated.resources.calendar_join_join
import org.churchpresenter.strings.generated.resources.calendar_join_open_file
import org.churchpresenter.strings.generated.resources.calendar_join_replaces
import org.churchpresenter.strings.generated.resources.calendar_join_title
import org.churchpresenter.strings.generated.resources.close
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.ThemeMode
import org.churchpresenter.theme.components.GhostButton
import org.churchpresenter.theme.components.KeyButton
import org.churchpresenter.theme.components.RaisedButton
import org.churchpresenter.theme.components.SettingsTextField
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import java.io.File
import java.net.URLEncoder
import java.time.Instant
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Adding a computer: its name and what it may do, then the join code to hand over -- saved to a
 * folder, copied, or put in an email from the user's own mail app.
 */
@Composable
internal fun AddComputerDialog(
    onCreate: suspend (role: String, name: String) -> CalendarJoin?,
    onDismiss: () -> Unit,
) {
    CalendarDialog(title = stringResource(Res.string.calendar_computer_add_title), onDismiss = onDismiss) {
        var join by remember { mutableStateOf<CalendarJoin?>(null) }
        val ready = join
        if (ready == null) {
            AddComputerForm(onCreate = onCreate, onCreated = { join = it })
        } else {
            JoinReady(ready)
        }
        Spacer(Modifier.weight(1f))
        RaisedButton(shape = AppShape(6.dp), onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(Res.string.close), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun AddComputerForm(
    onCreate: suspend (role: String, name: String) -> CalendarJoin?,
    onCreated: (CalendarJoin) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(CalendarSyncSettings.ROLE_FOLLOWER) }
    var working by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    SettingsTextField(
        value = name,
        onValueChange = { name = it },
        label = stringResource(Res.string.calendar_computer_add_name),
        fillWidth = true,
    )
    RoleChoice(CalendarSyncSettings.ROLE_FOLLOWER, role, Res.string.calendar_computer_add_follower) { role = it }
    RoleChoice(CalendarSyncSettings.ROLE_VIEWER, role, Res.string.calendar_computer_add_viewer) { role = it }
    if (failed) ErrorText(stringResource(Res.string.calendar_computer_add_failed))
    KeyButton(
        shape = AppShape(6.dp),
        contentPadding = SMALL_PADDING,
        enabled = !working,
        onClick = {
            working = true
            failed = false
            scope.launch {
                val created = onCreate(role, name.trim())
                working = false
                if (created == null) failed = true else onCreated(created)
            }
        },
    ) { Text(stringResource(Res.string.calendar_computer_add_create), style = MaterialTheme.typography.labelSmall) }
}

@Composable
private fun RoleChoice(
    value: String,
    selected: String,
    label: StringResource,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = value == selected, role = Role.RadioButton, onClick = { onSelect(value) }),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = value == selected, onClick = null)
        Text(stringResource(label), style = MaterialTheme.typography.bodySmall)
    }
}

/** The join code made, and the three ways to hand it over. */
@Composable
private fun JoinReady(join: CalendarJoin) {
    val scope = rememberCoroutineScope()
    val code = remember(join) { join.toCode() }
    var note by remember { mutableStateOf("") }
    val saveTitle = stringResource(Res.string.calendar_computer_save_file)
    val filter = joinFileFilter()
    val copied = stringResource(Res.string.calendar_computer_copied)
    val savedFormat = stringResource(Res.string.calendar_computer_saved, "%s")
    val subject = stringResource(Res.string.calendar_computer_email_subject)
    val body = stringResource(Res.string.calendar_computer_email_body, code)
    Text(stringResource(Res.string.calendar_computer_join_ready), style = MaterialTheme.typography.bodySmall)
    SettingsTextField(
        value = code,
        onValueChange = {},
        readOnly = true,
        singleLine = false,
        maxLines = CODE_LINES,
        fillWidth = true,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        SmallButton(text = saveTitle) {
            scope.launch {
                val path = SwingFileChooser.save(null, CalendarJoin.FILE_NAME, listOf(filter), saveTitle)
                    ?: return@launch
                val written = withContext(Dispatchers.IO) { runCatching { path.toFile().writeText(join.toJson()) } }
                if (written.isSuccess) note = savedFormat.format(path.parent?.toString().orEmpty())
            }
        }
        SmallButton(text = stringResource(Res.string.calendar_computer_copy_code)) {
            if (SystemClipboard.copy(code)) note = copied
        }
        SmallButton(text = stringResource(Res.string.calendar_computer_email)) {
            UrlOpener.open("mailto:?subject=${mailEncode(subject)}&body=${mailEncode(body)}")
        }
    }
    if (note.isNotEmpty()) {
        Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
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
        val scope = rememberCoroutineScope()
        var text by remember { mutableStateOf("") }
        var file by remember { mutableStateOf<File?>(null) }
        var error by remember { mutableStateOf("") }
        var working by remember { mutableStateOf(false) }
        val openTitle = stringResource(Res.string.calendar_join_open_file)
        val filter = joinFileFilter()
        val invalid = stringResource(Res.string.calendar_join_invalid)
        val expired = stringResource(Res.string.calendar_join_expired)
        val failed = stringResource(Res.string.calendar_join_failed)
        Text(stringResource(Res.string.calendar_join_body), style = MaterialTheme.typography.bodySmall)
        SettingsTextField(
            value = text,
            onValueChange = { text = it; file = null; error = "" },
            label = stringResource(Res.string.calendar_join_code),
            singleLine = false,
            maxLines = CODE_LINES,
            fillWidth = true,
            isError = error.isNotEmpty(),
        )
        SmallButton(text = openTitle) {
            scope.launch {
                val path = SwingFileChooser.chooseSingle(null, listOf(filter), openTitle, selectDirectory = false)
                    ?: return@launch
                val read = withContext(Dispatchers.IO) { runCatching { path.toFile().readText() } }.getOrNull()
                if (read != null) {
                    text = read
                    file = path.toFile()
                    error = ""
                }
            }
        }
        if (replacesOwn) ErrorText(stringResource(Res.string.calendar_join_replaces))
        if (error.isNotEmpty()) ErrorText(error)
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier.align(Alignment.End),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GhostButton(onClick = onDismiss) {
                Text(stringResource(Res.string.close), style = MaterialTheme.typography.labelSmall)
            }
            KeyButton(
                shape = AppShape(6.dp),
                contentPadding = SMALL_PADDING,
                enabled = !working && text.isNotBlank(),
                onClick = {
                    when (val parsed = CalendarJoin.parse(text, Instant.now())) {
                        JoinParse.Invalid -> error = invalid
                        JoinParse.Expired -> error = expired
                        is JoinParse.Ok -> {
                            working = true
                            scope.launch {
                                val joined = onJoin(parsed.join)
                                working = false
                                if (joined) {
                                    // The file did its job; left in a shared folder it is a key lying about.
                                    file?.let { withContext(Dispatchers.IO) { runCatching { it.delete() } } }
                                    onDismiss()
                                } else {
                                    error = failed
                                }
                            }
                        }
                    }
                },
            ) { Text(stringResource(Res.string.calendar_join_join), style = MaterialTheme.typography.labelSmall) }
        }
    }
}

/** The scaffold both dialogs share: a window over the main one, the app's theme, a padded column. */
@Composable
private fun CalendarDialog(
    title: String,
    onDismiss: () -> Unit,
    width: Dp = DIALOG_WIDTH,
    height: Dp = DIALOG_HEIGHT,
    content: @Composable ColumnScope.() -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < DARK_LUMINANCE
    val mainWindowState = LocalMainWindowState.current
    DialogWindow(
        onCloseRequest = onDismiss,
        state = rememberDialogState(
            position = centeredOnMainWindow(mainWindowState, width, height),
            width = width,
            height = height,
        ),
        title = title,
        resizable = false,
    ) {
        AppWindowRoot(theme = if (isDark) ThemeMode.DARK else ThemeMode.LIGHT) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = content,
                )
            }
        }
    }
}

@Composable
private fun joinFileFilter(): FileNameExtensionFilter {
    val label = stringResource(Res.string.calendar_join_file_type)
    return remember(label) { FileNameExtensionFilter(label, CalendarJoin.FILE_EXTENSION) }
}

@Composable
internal fun SmallButton(text: String, onClick: () -> Unit) {
    RaisedButton(shape = AppShape(6.dp), contentPadding = SMALL_PADDING, onClick = onClick) {
        Text(text, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
internal fun ErrorText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}

/** A `mailto:` parameter: percent-encoded, with spaces as `%20` -- mail apps do not read `+` as one. */
private fun mailEncode(text: String): String =
    URLEncoder.encode(text, Charsets.UTF_8).replace("+", "%20")

internal val SMALL_PADDING = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
private val DIALOG_WIDTH = 460.dp
private val DIALOG_HEIGHT = 480.dp
private const val DARK_LUMINANCE = 0.5f
private const val CODE_LINES = 5
