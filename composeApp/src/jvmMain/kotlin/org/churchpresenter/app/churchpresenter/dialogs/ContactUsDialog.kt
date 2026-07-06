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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import churchpresenter.composeapp.generated.resources.cancel
import churchpresenter.composeapp.generated.resources.contact_email_label
import churchpresenter.composeapp.generated.resources.contact_error
import churchpresenter.composeapp.generated.resources.contact_message_label
import churchpresenter.composeapp.generated.resources.contact_name_label
import churchpresenter.composeapp.generated.resources.contact_network_error
import churchpresenter.composeapp.generated.resources.contact_open_browser
import churchpresenter.composeapp.generated.resources.contact_rate_limited_browser
import churchpresenter.composeapp.generated.resources.contact_send
import churchpresenter.composeapp.generated.resources.contact_sending
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
import java.awt.Desktop
import java.net.URI

private sealed interface SendStatus {
    data object Idle : SendStatus
    data object Sending : SendStatus
    data object Sent : SendStatus
    data class Error(val message: String) : SendStatus
}

@Composable
fun ContactUsDialog(
    isVisible: Boolean,
    onDismiss: () -> Unit,
) {
    if (!isVisible) return

    // Localized type labels paired with the API's type keys.
    val types = listOf(
        stringResource(Res.string.contact_type_feature) to "featureRequest",
        stringResource(Res.string.contact_type_feedback) to "feedback",
        stringResource(Res.string.contact_type_testimonial) to "testimonial",
        stringResource(Res.string.contact_type_bug) to "bugReport",
    )

    var selectedType by remember { mutableStateOf(types.first()) }
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
                                    onClick = { selectedType = type }
                                )
                            }
                        }
                    }

                    SettingsTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = stringResource(Res.string.contact_name_label),
                        singleLine = true,
                        fillWidth = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    SettingsTextField(
                        value = email,
                        onValueChange = { email = it },
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
                            onValueChange = { message = it },
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

                // Status — kept outside the scrolling field area above so it is always
                // visible (an error sentence can wrap to two lines and must never clip).
                when (val s = status) {
                    is SendStatus.Sending -> StatusLine(
                        stringResource(Res.string.contact_sending),
                        MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    is SendStatus.Sent -> StatusLine(sentText, MaterialTheme.colorScheme.primary)
                    is SendStatus.Error -> StatusLine(s.message, MaterialTheme.colorScheme.error)
                    SendStatus.Idle -> {}
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Always available: fall back to the full web contact form in a browser.
                    TextButton(
                        shape = RoundedCornerShape(6.dp),
                        onClick = {
                            runCatching {
                                Desktop.getDesktop().browse(URI(ContactReporter.WEB_CONTACT_URL))
                            }
                        }
                    ) {
                        Text(stringResource(Res.string.contact_open_browser), style = MaterialTheme.typography.labelLarge)
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    TextButton(shape = RoundedCornerShape(6.dp), onClick = onDismiss) {
                        Text(stringResource(Res.string.cancel), style = MaterialTheme.typography.labelLarge)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        shape = RoundedCornerShape(6.dp),
                        enabled = name.isNotBlank() && message.isNotBlank() && status != SendStatus.Sending,
                        onClick = {
                            status = SendStatus.Sending
                            scope.launch {
                                val outcome = ContactReporter.submit(
                                    ContactReporter.ContactRequest(
                                        type = selectedType.second,
                                        name = name.trim(),
                                        message = message.trim(),
                                        email = email.trim(),
                                        context = ContactReporter.defaultContext(),
                                    )
                                )
                                when (outcome) {
                                    ContactReporter.Outcome.Success -> {
                                        status = SendStatus.Sent
                                        delay(1500)
                                        onDismiss()
                                    }
                                    ContactReporter.Outcome.RateLimited ->
                                        // The web form (with the always-visible button below) is the escape hatch.
                                        status = SendStatus.Error(rateLimitedText)
                                    is ContactReporter.Outcome.Invalid ->
                                        status = SendStatus.Error(outcome.error ?: errorText)
                                    ContactReporter.Outcome.NetworkError ->
                                        status = SendStatus.Error(networkText)
                                    ContactReporter.Outcome.Failure ->
                                        status = SendStatus.Error(errorText)
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(stringResource(Res.string.contact_send), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
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

@Composable
private fun StatusLine(text: String, color: Color) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        style = MaterialTheme.typography.bodySmall,
        color = color
    )
}
