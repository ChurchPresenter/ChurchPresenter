package org.churchpresenter.hosanna.composables.foundation

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import org.churchpresenter.theme.ThemeColors
import org.churchpresenter.hosanna.composables.model.MessageIntent

@Immutable
internal data class MessageColors(val container: Color, val content: Color, val icon: Color, val edge: Color)

internal fun ThemeColors.messageColors(intent: MessageIntent): MessageColors = when (intent) {
    MessageIntent.Info -> MessageColors(primaryContainer, onPrimaryContainer, onPrimaryContainer, Color.Transparent)
    MessageIntent.Success -> MessageColors(successContainer, onSuccessContainer, onSuccessContainer, Color.Transparent)
    MessageIntent.Warning ->
        MessageColors(tertiaryContainer, onTertiaryContainer, onTertiaryContainer, Color.Transparent)
    MessageIntent.Error -> MessageColors(errorContainer, onErrorContainer, onErrorContainer, Color.Transparent)
    MessageIntent.Neutral -> MessageColors(surfaceContainerLow, onSurface, onSurfaceVariant, outlineVariant)
}
