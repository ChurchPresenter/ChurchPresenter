package org.churchpresenter.qa

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import org.churchpresenter.strings.generated.resources.qa_session_running
import org.churchpresenter.theme.semantic
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.cancel
import org.churchpresenter.strings.generated.resources.qa_new_session
import org.churchpresenter.strings.generated.resources.qa_no_session_banner
import org.churchpresenter.strings.generated.resources.qa_resume_session
import org.churchpresenter.strings.generated.resources.qa_session_name_label
import org.churchpresenter.strings.generated.resources.qa_session_option
import org.churchpresenter.strings.generated.resources.qa_start_session
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.components.GhostButton
import org.churchpresenter.theme.components.KeyButton
import org.churchpresenter.theme.components.SettingsTextField
import org.jetbrains.compose.resources.stringResource

// Naming, resuming and the absence of a session -- the tab's pieces that deal in sessions.

/** Resume, opening the list of finished sessions to pick one from. */
@Composable
internal fun ResumeMenu(tab: QATabScope) = with(tab) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        KeyButton(onClick = { expanded = true }, shape = AppShape(8.dp)) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(Res.string.qa_resume_session))
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            sessions.forEach { session ->
                DropdownMenuItem(
                    text = { Text(sessionLabel(session)) },
                    onClick = {
                        expanded = false
                        qaManager.resumeSession(session.id)
                    }
                )
            }
        }
    }
}

/** A finished session as a list shows it: its name and how many questions it holds. */
@Composable
internal fun sessionLabel(session: QASession): String =
    stringResource(Res.string.qa_session_option, session.name, session.questions.size)

/** Says, for as long as no session runs, that phones cannot send questions yet. */
@Composable
internal fun QATabScope.NoSessionBanner() {
    Text(
        stringResource(Res.string.qa_no_session_banner),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .background(MaterialTheme.colorScheme.errorContainer, AppShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

/** Names a new session before it starts; the start time is the name already typed in. */
@Composable
internal fun QATabScope.NewSessionDialog() {
    var name by remember {
        val default = defaultSessionName(System.currentTimeMillis())
        mutableStateOf(TextFieldValue(default, TextRange(0, default.length)))
    }
    val focus = remember { FocusRequester() }
    val start = {
        qaManager.startSession(name.text)
        showNewSessionDialog = false
    }
    AlertDialog(
        onDismissRequest = { showNewSessionDialog = false },
        title = { Text(stringResource(Res.string.qa_new_session)) },
        text = {
            SettingsTextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(Res.string.qa_session_name_label),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).onPreviewKeyEvent { event ->
                    val enter = event.key == Key.Enter || event.key == Key.NumPadEnter
                    if (enter && event.type == KeyEventType.KeyDown) start()
                    enter
                },
                fillWidth = true,
            )
        },
        confirmButton = {
            GhostButton(shape = AppShape(6.dp), onClick = start) {
                Text(stringResource(Res.string.qa_start_session))
            }
        },
        dismissButton = {
            GhostButton(shape = AppShape(6.dp), onClick = { showNewSessionDialog = false }) {
                Text(stringResource(Res.string.cancel))
            }
        }
    )
    LaunchedEffect(Unit) { focus.requestFocus() }
}

/** The running session's name, in a green pill beside Stop Session. */
@Composable
internal fun RunningSessionChip(name: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(MaterialTheme.semantic.successContainer, AppShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).background(MaterialTheme.semantic.success, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(
            stringResource(Res.string.qa_session_running, name),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.semantic.onSuccessContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
