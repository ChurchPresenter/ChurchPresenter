package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.qa_background_color
import churchpresenter.composeapp.generated.resources.qa_font
import churchpresenter.composeapp.generated.resources.qa_opacity
import churchpresenter.composeapp.generated.resources.qa_position
import churchpresenter.composeapp.generated.resources.qa_qr_bg_color
import churchpresenter.composeapp.generated.resources.qa_qr_fg_color
import churchpresenter.composeapp.generated.resources.qa_size
import churchpresenter.composeapp.generated.resources.qa_styling_qr_group
import churchpresenter.composeapp.generated.resources.qa_styling_text_group
import churchpresenter.composeapp.generated.resources.qa_text_color
import churchpresenter.composeapp.generated.resources.qa_transparent
import org.churchpresenter.app.churchpresenter.composables.ColorPickerField
import org.churchpresenter.app.churchpresenter.composables.ScreenPositionPicker
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.QASettings
import org.churchpresenter.theme.components.KeyButton
import org.jetbrains.compose.resources.stringResource

/** What a transparent background is stored as, and the colour choosing one again starts from. */
private const val TRANSPARENT = "transparent"
private const val DEFAULT_BACKGROUND = "#1E1E2E"

/** A write to this profile's Q&A settings, as each control below makes one. */
private typealias QaUpdate = ((QASettings) -> QASettings) -> Unit

/**
 * How a Q&A question and its QR code look on one output -- edited per profile on the Profiles
 * tab's Q&A style tab.
 *
 * This was the lower half of [QARemoteDialog], which keeps only what is one per install: the
 * submission and admin links, public access, the rate limit and the QR code's message.
 */
@Composable
internal fun QADisplaySettings(
    appSettings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    availableFonts: List<String>,
    modifier: Modifier = Modifier,
) {
    val qa = appSettings.qaSettings
    val update: QaUpdate = { transform -> onSettingsChange { s -> s.copy(qaSettings = transform(s.qaSettings)) } }
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            GroupTitle(stringResource(Res.string.qa_styling_qr_group))
            QrCodeStyle(qa, update)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            GroupTitle(stringResource(Res.string.qa_position))
            ScreenPositionPicker(
                positions = screenPositions(),
                selected = qa.position,
                onSelect = { v -> update { it.copy(position = v) } },
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            GroupTitle(stringResource(Res.string.qa_styling_text_group))
            QuestionTextStyle(qa, availableFonts, update)
            Spacer(Modifier.height(2.dp))
            QuestionBackground(qa, update)
        }
    }
}

@Composable
private fun GroupTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/** The QR code's two colours and how opaque its backing is. */
@Composable
private fun QrCodeStyle(qa: QASettings, update: QaUpdate) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ColorPickerField(
            label = stringResource(Res.string.qa_qr_fg_color),
            color = qa.qrForegroundColor,
            onColorChange = { v -> update { it.copy(qrForegroundColor = v) } },
            modifier = Modifier.weight(1f),
        )
        ColorPickerField(
            label = stringResource(Res.string.qa_qr_bg_color),
            color = qa.qrBackgroundColor,
            onColorChange = { v -> update { it.copy(qrBackgroundColor = v) } },
            modifier = Modifier.weight(1f),
        )
    }
    OpacitySliderRow(stringResource(Res.string.qa_opacity), qa.qrBackgroundOpacity) { v ->
        update { it.copy(qrBackgroundOpacity = v) }
    }
}

/** The question's text look. */
@Composable
private fun QuestionTextStyle(qa: QASettings, availableFonts: List<String>, update: QaUpdate) {
    DisplayTextStyleControls(
        style = DisplayTextStyle(
            textColor = qa.textColor, bold = qa.bold, italic = qa.italic, underline = qa.underline,
            shadow = qa.shadow, shadowColor = qa.shadowColor, shadowSize = qa.shadowSize,
            shadowOpacity = qa.shadowOpacity, backdrop = qa.backdrop, outline = qa.outline,
            fontType = qa.fontType, fontSize = qa.fontSize,
        ),
        labels = DisplayTextStyleLabels(
            color = stringResource(Res.string.qa_text_color),
            font = stringResource(Res.string.qa_font),
            size = stringResource(Res.string.qa_size),
        ),
        availableFonts = availableFonts,
        onChange = { t ->
            update {
                it.copy(
                    textColor = t.textColor, bold = t.bold, italic = t.italic, underline = t.underline,
                    shadow = t.shadow, shadowColor = t.shadowColor, shadowSize = t.shadowSize,
                    shadowOpacity = t.shadowOpacity, backdrop = t.backdrop, outline = t.outline,
                    fontType = t.fontType, fontSize = t.fontSize,
                )
            }
        },
    )
}

/** The panel behind the question: a colour or none, and how opaque it is. */
@Composable
private fun QuestionBackground(qa: QASettings, update: QaUpdate) {
    val transparent = qa.backgroundColor.equals(TRANSPARENT, ignoreCase = true)
    val backgroundLabel = stringResource(Res.string.qa_background_color)
    val transparentLabel = stringResource(Res.string.qa_transparent)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        if (transparent) {
            KeyButton(
                onClick = { update { it.copy(backgroundColor = DEFAULT_BACKGROUND) } },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            ) {
                Text("$backgroundLabel · $transparentLabel", style = MaterialTheme.typography.labelSmall)
            }
        } else {
            ColorPickerField(
                label = backgroundLabel,
                color = qa.backgroundColor,
                onColorChange = { v -> update { it.copy(backgroundColor = v) } },
                modifier = Modifier.weight(1f),
            )
            KeyButton(
                onClick = { update { it.copy(backgroundColor = TRANSPARENT) } },
                shape = RoundedCornerShape(6.dp),
            ) {
                Text(transparentLabel, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    OpacitySliderRow(stringResource(Res.string.qa_opacity), qa.backgroundOpacity) { v ->
        update { it.copy(backgroundOpacity = v) }
    }
}
