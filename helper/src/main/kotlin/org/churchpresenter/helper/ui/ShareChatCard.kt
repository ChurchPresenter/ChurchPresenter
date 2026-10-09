package org.churchpresenter.helper.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.churchpresenter.helper.HelperState
import org.churchpresenter.helper.ThreadEntry
import org.churchpresenter.helper.report.ChatLine
import org.churchpresenter.helper.report.ChatNames
import org.churchpresenter.helper.report.ChatSendResult
import org.churchpresenter.helper.report.ChatSender
import org.churchpresenter.helper.report.MAX_NOTE_CHARS
import org.churchpresenter.helper.report.chatMessage
import org.churchpresenter.helper.report.redactChat
import org.churchpresenter.helper.report.transcriptOf
import org.churchpresenter.helper.resolve
import org.churchpresenter.helper.summary
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.helper_cancel
import org.churchpresenter.strings.generated.resources.helper_ok
import org.churchpresenter.strings.generated.resources.helper_send
import org.churchpresenter.strings.generated.resources.helper_send_chat_email
import org.churchpresenter.strings.generated.resources.helper_send_chat_failed
import org.churchpresenter.strings.generated.resources.helper_send_chat_note
import org.churchpresenter.strings.generated.resources.helper_send_chat_preview
import org.churchpresenter.strings.generated.resources.helper_send_chat_rate_limited
import org.churchpresenter.strings.generated.resources.helper_send_chat_sending
import org.churchpresenter.strings.generated.resources.helper_send_chat_sent
import org.churchpresenter.strings.generated.resources.helper_send_chat_title
import org.jetbrains.compose.resources.stringResource

/**
 * "Send this chat": the conversation exactly as it will be sent, already masked, an optional note on
 * what went wrong, an optional email for a reply, and Send or Cancel. Nothing leaves the app until Send is pressed.
 */
@Composable
internal fun ShareChatCard(state: HelperState, inputs: HelperInputs, send: ChatSender) {
    val lines = chatLines(state)
    val names = ChatNames(
        profiles = inputs.context.profiles.map { it.name },
        outputs = inputs.context.outputs.map { it.label } + inputs.screens.map { it.name },
    )
    val transcript = remember(lines, names, inputs.context) {
        transcriptOf(redactChat(lines, names, inputs.context))
    }
    var note by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ChatSendResult?>(null) }
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(lifted(CARD_LIFT), shape)
            .border(1.dp, colors.outlineVariant, shape)
            .padding(start = 13.dp, end = 13.dp, top = 13.dp, bottom = 12.dp)
            .testTag("helper.shareChat"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (result == ChatSendResult.SENT) {
            // Answered: the question above it would read as still waiting.
            Text(
                stringResource(Res.string.helper_send_chat_sent),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.testTag("helper.shareChat.result"),
            )
            Actions(primary = Res.string.helper_ok to { state.sharingChat = false })
            return@Column
        }
        Text(stringResource(Res.string.helper_send_chat_title), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(Res.string.helper_send_chat_preview),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
        Text(
            transcript,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = colors.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = PREVIEW_HEIGHT)
                .background(colors.surface, RoundedCornerShape(8.dp))
                .verticalScroll(rememberScrollState())
                .padding(8.dp)
                .testTag("helper.shareChat.preview"),
        )
        ShareChatFields(
            note = note,
            onNote = { note = it.take(MAX_NOTE_CHARS) },
            email = email,
            onEmail = { email = it },
            enabled = !sending,
        )
        val problem = when (result) {
            ChatSendResult.RATE_LIMITED -> Res.string.helper_send_chat_rate_limited
            ChatSendResult.FAILED -> Res.string.helper_send_chat_failed
            else -> null
        }
        when {
            sending -> Text(
                stringResource(Res.string.helper_send_chat_sending),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            problem != null -> Text(
                stringResource(problem),
                style = MaterialTheme.typography.bodySmall,
                color = colors.error,
                modifier = Modifier.testTag("helper.shareChat.result"),
            )
        }
        Actions(
            Res.string.helper_cancel to { if (!sending) state.sharingChat = false },
            primary = Res.string.helper_send to {
                if (!sending) {
                    sending = true
                    scope.launch {
                        result = send(chatMessage(note, transcript), email.trim())
                        sending = false
                    }
                }
            },
            quiet = true,
        )
    }
}

/** What the operator adds to a sent chat: a note on what went wrong, and an email for a reply. Both optional. */
@Composable
private fun ShareChatFields(
    note: String,
    onNote: (String) -> Unit,
    email: String,
    onEmail: (String) -> Unit,
    enabled: Boolean,
) {
    OutlinedTextField(
        value = note,
        onValueChange = onNote,
        enabled = enabled,
        minLines = 2,
        maxLines = NOTE_LINES,
        label = { Text(stringResource(Res.string.helper_send_chat_note)) },
        textStyle = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().testTag("helper.shareChat.note"),
    )
    OutlinedTextField(
        value = email,
        onValueChange = onEmail,
        singleLine = true,
        enabled = enabled,
        label = { Text(stringResource(Res.string.helper_send_chat_email)) },
        textStyle = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().testTag("helper.shareChat.email"),
    )
}

/** The conversation as plain lines, the reply still on screen last, each in the operator's language. */
@Composable
private fun chatLines(state: HelperState): List<ChatLine> {
    val thread = state.thread.entries.map { entry ->
        when (entry) {
            is ThreadEntry.Operator -> ChatLine(fromOperator = true, text = entry.text)
            is ThreadEntry.Wick -> ChatLine(fromOperator = false, text = entry.text.resolve())
        }
    }
    val pending = state.reply.summary(state.undoLabel)?.resolve()
    return if (pending == null) thread else thread + ChatLine(fromOperator = false, text = pending)
}

private val PREVIEW_HEIGHT = 160.dp
private const val NOTE_LINES = 4
