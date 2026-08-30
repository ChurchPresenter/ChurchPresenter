package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.contact_email_label
import churchpresenter.composeapp.generated.resources.contact_error
import churchpresenter.composeapp.generated.resources.contact_message_label
import churchpresenter.composeapp.generated.resources.contact_name_label
import churchpresenter.composeapp.generated.resources.contact_network_error
import churchpresenter.composeapp.generated.resources.contact_rate_limited_browser
import churchpresenter.composeapp.generated.resources.contact_sent
import churchpresenter.composeapp.generated.resources.contact_type_bug
import churchpresenter.composeapp.generated.resources.contact_type_feature
import churchpresenter.composeapp.generated.resources.contact_type_feedback
import churchpresenter.composeapp.generated.resources.contact_type_label
import churchpresenter.composeapp.generated.resources.contact_type_testimonial
import churchpresenter.composeapp.generated.resources.contact_us_title
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.churchpresenter.app.churchpresenter.LocalMainWindowState
import org.churchpresenter.app.churchpresenter.centeredOnMainWindow
import org.churchpresenter.app.churchpresenter.composables.SettingsTextField
import org.churchpresenter.app.churchpresenter.utils.ContactReporter
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.app.churchpresenter.utils.UrlOpener

private const val SENT_CONFIRMATION_MS = 1500L

const val CONTACT_TYPE_TESTIMONIAL = "testimonial"

internal fun initialContactType(
    types: List<Pair<String, String>>,
    initialTypeKey: String?,
): Pair<String, String> = types.firstOrNull { it.second == initialTypeKey } ?: types.first()

/**
 * Builds the submit request from the dialog's current field state, trimming free-text
 * fields the way the server expects. Split out from the [onSend][ContactUsDialog] closure
 * — which lives inside a real [DialogWindow] and can't run headless — so this decision is
 * directly testable. See [statusForOutcome] for the matching answer-side decision.
 */
internal fun buildContactRequest(
    type: String,
    name: String,
    email: String,
    message: String,
): ContactReporter.ContactRequest = ContactReporter.ContactRequest(
    type = type,
    name = name.trim(),
    message = message.trim(),
    email = email.trim(),
    context = ContactReporter.defaultContext(),
)

/**
 * Turns a [ContactReporter.submit] outcome into the status the dialog should show, using
 * the caller-supplied fallback texts. Split out for the same reason as [buildContactRequest].
 */
internal fun statusForOutcome(
    outcome: ContactReporter.Outcome,
    errorText: String,
    networkText: String,
    rateLimitedText: String,
): SendStatus = when (outcome) {
    ContactReporter.Outcome.Success -> SendStatus.Sent
    ContactReporter.Outcome.RateLimited -> SendStatus.Error(rateLimitedText)
    is ContactReporter.Outcome.Invalid -> SendStatus.Error(outcome.error ?: errorText)
    ContactReporter.Outcome.NetworkError -> SendStatus.Error(networkText)
    ContactReporter.Outcome.Failure -> SendStatus.Error(errorText)
}

/**
 * Submits the contact form and turns the result into the status the dialog should show. Split out
 * from [ContactUsDialog]'s `onSend` closure — same reason as [buildContactRequest] — so the
 * submit-then-map sequence is directly testable, leaving only the coroutine launch and the
 * send-then-dismiss glue inside the real [DialogWindow].
 */
internal suspend fun submitContactRequest(
    type: String,
    name: String,
    email: String,
    message: String,
    errorText: String,
    networkText: String,
    rateLimitedText: String,
): SendStatus {
    val outcome = ContactReporter.submit(buildContactRequest(type, name, email, message))
    return statusForOutcome(outcome, errorText, networkText, rateLimitedText)
}

@Composable
fun ContactUsDialog(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    initialTypeKey: String? = null,
) {
    if (!isVisible) return

    // Localized type labels paired with the API's type keys.
    val types = listOf(
        stringResource(Res.string.contact_type_feature) to "featureRequest",
        stringResource(Res.string.contact_type_feedback) to "feedback",
        stringResource(Res.string.contact_type_testimonial) to CONTACT_TYPE_TESTIMONIAL,
        stringResource(Res.string.contact_type_bug) to "bugReport",
    )

    var selectedType by remember { mutableStateOf(initialContactType(types, initialTypeKey)) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<SendStatus>(SendStatus.Idle) }

    // Captured here (composable scope) so the coroutine can use them without stringResource.
    val sentText = stringResource(Res.string.contact_sent)
    val errorText = stringResource(Res.string.contact_error)
    val networkText = stringResource(Res.string.contact_network_error)
    val rateLimitedText = stringResource(Res.string.contact_rate_limited_browser)

    val scope = rememberCoroutineScope()
    val mainWindowState = LocalMainWindowState.current

    DialogWindow(
        onCloseRequest = onDismiss,
        state = rememberDialogState(
            position = centeredOnMainWindow(mainWindowState, 520.dp, 660.dp),
            width = 520.dp,
            height = 660.dp
        ),
        title = stringResource(Res.string.contact_us_title),
        resizable = false
    ) {
        ContactUsDialogContent(
            onDismiss = onDismiss,
            types = types,
            selectedType = selectedType,
            onSelectedTypeChange = { selectedType = it },
            name = name,
            onNameChange = { name = it },
            email = email,
            onEmailChange = { email = it },
            message = message,
            onMessageChange = { message = it },
            status = status,
            onSend = {
                status = SendStatus.Sending
                scope.launch {
                    status = submitContactRequest(
                        selectedType.second, name, email, message, errorText, networkText, rateLimitedText
                    )
                    if (status == SendStatus.Sent) {
                        delay(SENT_CONFIRMATION_MS)
                        onDismiss()
                    }
                }
            },
            sentText = sentText,
        )
    }
}

@Composable
internal fun ContactUsDialogContent(
    onDismiss: () -> Unit,
    types: List<Pair<String, String>>,
    selectedType: Pair<String, String>,
    onSelectedTypeChange: (Pair<String, String>) -> Unit,
    name: String,
    onNameChange: (String) -> Unit,
    email: String,
    onEmailChange: (String) -> Unit,
    message: String,
    onMessageChange: (String) -> Unit,
    status: SendStatus,
    onSend: () -> Unit,
    sentText: String,
    // A parameter so a test can watch it rather than launch the machine's browser -- UrlOpener
    // falls back to the OS's own open command, which a headless test JVM does not stop.
    openUrl: (String) -> Unit = { UrlOpener.open(it) }
) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                Text(
                    text = stringResource(Res.string.contact_us_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(20.dp))

                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Type selector — pill chips that wrap, mirroring the website's
                    // contact form (a native dropdown here felt out of place).
                    Column {
                        FieldLabel(stringResource(Res.string.contact_type_label))
                        Spacer(modifier = Modifier.height(6.dp))
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            types.forEach { type ->
                                TypePill(
                                    label = type.first,
                                    selected = selectedType == type,
                                    onClick = { onSelectedTypeChange(type) }
                                )
                            }
                        }
                    }

                    SettingsTextField(
                        value = name,
                        onValueChange = onNameChange,
                        label = stringResource(Res.string.contact_name_label),
                        singleLine = true,
                        fillWidth = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    SettingsTextField(
                        value = email,
                        onValueChange = onEmailChange,
                        label = stringResource(Res.string.contact_email_label),
                        singleLine = true,
                        fillWidth = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Multi-line message box. Built inline (rather than via SettingsTextField)
                    // so the fixed height applies to the text area itself and the caret starts
                    // at the top-left — a labeled single-line field can't grow to a real box.
                    Column {
                        FieldLabel(stringResource(Res.string.contact_message_label))
                        Spacer(modifier = Modifier.height(6.dp))
                        BasicTextField(
                            value = message,
                            onValueChange = onMessageChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceContainerHigh,
                                    RoundedCornerShape(6.dp)
                                )
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 9.dp, vertical = 7.dp),
                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            interactionSource = remember { MutableInteractionSource() }
                        )
                    }
                }

                ContactUsStatus(status, sentText)

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                ContactUsActions(
                    canSend = name.isNotBlank() && message.isNotBlank() && status != SendStatus.Sending,
                    onOpenInBrowser = { runCatching { openUrl(ContactReporter.WEB_CONTACT_URL) } },
                    onDismiss = onDismiss,
                    onSend = onSend,
                )
            }
        }
    }


@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun TypePill(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(100.dp)
    val borderColor = if (selected) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .border(1.dp, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

