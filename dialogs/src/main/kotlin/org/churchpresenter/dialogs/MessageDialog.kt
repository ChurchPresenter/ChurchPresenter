package org.churchpresenter.dialogs

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import org.churchpresenter.strings.generated.resources.message_purpose
import org.churchpresenter.theme.components.KeyButton
import androidx.compose.foundation.layout.height
import org.churchpresenter.strings.generated.resources.message_stays_up
import androidx.compose.foundation.layout.Arrangement
import org.churchpresenter.sharedui.composables.SettingsScrollColumn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.icons.generated.resources.ic_close
import org.churchpresenter.liveshow.Cue
import org.churchpresenter.settings.MessageTemplate
import org.churchpresenter.settings.fillMessage
import org.churchpresenter.settings.messageTokens
import org.churchpresenter.sharedui.utils.LocalMainWindowState
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.close
import org.churchpresenter.strings.generated.resources.delete_saved_string
import org.churchpresenter.strings.generated.resources.go_live
import org.churchpresenter.strings.generated.resources.message_clear
import org.churchpresenter.strings.generated.resources.message_on_air
import org.churchpresenter.strings.generated.resources.message_save
import org.churchpresenter.strings.generated.resources.message_saved
import org.churchpresenter.strings.generated.resources.message_shows_as
import org.churchpresenter.strings.generated.resources.message_text
import org.churchpresenter.strings.generated.resources.message_text_hint
import org.churchpresenter.strings.generated.resources.message_title
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.ProvideUiScale
import org.churchpresenter.theme.components.GhostButton
import org.churchpresenter.theme.components.SettingsTextField
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.sharedui.utils.rememberCenteredDialogState

/**
 * Puts a message up (`docs/SHOW_CONTROL.md`, Messages): a saved one or one typed here, its
 * `{tokens}` filled in, for as long as asked or until cleared.
 */
@Composable
fun MessageDialog(
    isVisible: Boolean,
    templates: List<MessageTemplate>,
    onTemplatesChange: (List<MessageTemplate>) -> Unit,
    onAir: Cue.Message?,
    onGoLive: (Cue.Message) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
    /** The window it opens in -- see [DialogFrame]. */
    frame: DialogFrame = appDialogFrame,
) {
    if (!isVisible) return
    val dialogState = rememberCenteredDialogState(
        LocalMainWindowState.current,
        MESSAGE_DIALOG_WIDTH,
        MESSAGE_DIALOG_HEIGHT,
    )
    frame(
        DialogFrameSpec(
            onClose = onDismiss,
            state = dialogState,
            title = stringResource(Res.string.message_title),
        ),
    ) {
        ProvideUiScale {
            MessageDialogContent(templates, onTemplatesChange, onAir, onGoLive, onClear, onDismiss)
        }
    }
}

/** The dialog's body, apart from its window so it can be drawn headless. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MessageDialogContent(
    templates: List<MessageTemplate>,
    onTemplatesChange: (List<MessageTemplate>) -> Unit,
    onAir: Cue.Message?,
    onGoLive: (Cue.Message) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var picked by remember { mutableStateOf<MessageTemplate?>(null) }
    var text by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("") }
    var values by remember { mutableStateOf(mapOf<String, String>()) }
    val tokens = messageTokens(text)
    val shown = fillMessage(text, values).trim()

    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Column {
            LiveToolDialogHeader(
                tool = LiveTool.MESSAGE,
                painter = rememberVectorPainter(Icons.AutoMirrored.Filled.Message),
                title = stringResource(Res.string.message_title),
                purpose = stringResource(Res.string.message_purpose),
                onClose = onDismiss,
            )
            SettingsScrollColumn(
                modifier = Modifier.weight(1f).padding(start = LIVE_TOOL_BODY_PADDING, end = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Spacer(Modifier.height(4.dp))
                if (templates.isNotEmpty()) {
                    LiveToolSectionLabel(stringResource(Res.string.message_saved))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        templates.forEach { template ->
                            FilterChip(
                                selected = picked?.id == template.id,
                                onClick = {
                                    picked = template
                                    text = template.text
                                    duration = template.durationSeconds?.toString().orEmpty()
                                    values = emptyMap()
                                },
                                label = { Text(template.name) },
                                modifier = Modifier.testTag(messageTemplateTag(template.id)),
                            )
                        }
                    }
                }
                SettingsTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = stringResource(Res.string.message_text),
                    fillWidth = true,
                    singleLine = false,
                    placeholder = { Text(stringResource(Res.string.message_text_hint)) },
                    modifier = Modifier.fillMaxWidth().testTag(MESSAGE_TEXT_TAG),
                )
                InsertBlank { blank ->
                    text = if (text.isEmpty() || text.last().isWhitespace()) text + blank else "$text $blank"
                }
                tokens.forEach { token ->
                    SettingsTextField(
                        value = values[token].orEmpty(),
                        onValueChange = { values = values + (token to it) },
                        label = token,
                        fillWidth = true,
                        modifier = Modifier.fillMaxWidth().testTag(messageTokenTag(token)),
                    )
                }
                LiveToolSectionLabel(stringResource(Res.string.message_stays_up))
                StaysUpFor(duration) { duration = it }
                LiveToolSectionLabel(stringResource(Res.string.message_shows_as))
                MessagePreview(shown, duration.toIntOrNull())
                onAir?.let { OnAirRow(it, onClear) }
                Spacer(Modifier.height(4.dp))
            }
            LiveToolDialogFooter {
                MessageButtons(
                    canSave = text.isNotBlank(),
                    canGoLive = shown.isNotEmpty(),
                    picked = picked,
                    onSave = {
                        val saved = (picked ?: MessageTemplate(id = newTemplateId(templates), name = "", text = ""))
                            .copy(name = templateName(text), text = text, durationSeconds = duration.toIntOrNull())
                        onTemplatesChange(templates.filterNot { it.id == saved.id } + saved)
                        picked = saved
                    },
                    onDelete = { template ->
                        onTemplatesChange(templates.filterNot { it.id == template.id })
                        picked = null
                    },
                    onGoLive = { onGoLive(Cue.Message(shown, picked?.name, duration.toIntOrNull())) },
                    onDismiss = onDismiss,
                )
            }
        }
    }
}

/** What is on air, with its Clear. */
@Composable
private fun OnAirRow(message: Cue.Message, onClear: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(Res.string.message_on_air) + ": " + message.text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onClear, modifier = Modifier.testTag(MESSAGE_CLEAR_TAG)) {
            Icon(
                painterResource(IconRes.drawable.ic_close),
                contentDescription = stringResource(Res.string.message_clear),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun RowScope.MessageButtons(
    canSave: Boolean,
    canGoLive: Boolean,
    picked: MessageTemplate?,
    onSave: () -> Unit,
    onDelete: (MessageTemplate) -> Unit,
    onGoLive: () -> Unit,
    onDismiss: () -> Unit,
) {
    GhostButton(shape = AppShape(9.dp), onClick = onSave, enabled = canSave) {
        Text(stringResource(Res.string.message_save), color = MaterialTheme.colorScheme.primary)
    }
    if (picked != null) {
        GhostButton(shape = AppShape(9.dp), onClick = { onDelete(picked) }) {
            Text(stringResource(Res.string.delete_saved_string))
        }
    }
    Spacer(Modifier.weight(1f))
    KeyButton(shape = AppShape(10.dp), onClick = onDismiss) { Text(stringResource(Res.string.close)) }
    LiveActionButton(
        label = stringResource(Res.string.go_live),
        enabled = canGoLive,
        onClick = onGoLive,
        modifier = Modifier.testTag(MESSAGE_GO_LIVE_TAG),
    )
}

/** A saved message's name: its text, cut to the first few words. */
internal fun templateName(text: String): String {
    val words = text.trim().split(Regex("\\s+"))
    val name = words.take(NAME_WORDS).joinToString(" ")
    return if (words.size > NAME_WORDS) "$name…" else name
}

/** An id no saved message has. */
internal fun newTemplateId(templates: List<MessageTemplate>): String =
    generateSequence(templates.size + 1) { it + 1 }.map { "message$it" }.first { id -> templates.none { it.id == id } }

internal const val MESSAGE_TEXT_TAG = "message_text"
internal const val MESSAGE_DURATION_TAG = "message_duration"
internal const val MESSAGE_GO_LIVE_TAG = "message_go_live"
internal const val MESSAGE_CLEAR_TAG = "message_clear"

internal fun messageTemplateTag(id: String) = "message_template_$id"

internal fun messageTokenTag(token: String) = "message_token_$token"

private const val NAME_WORDS = 4
internal const val DURATION_DIGITS = 4
