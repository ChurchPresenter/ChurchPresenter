package org.churchpresenter.hosanna.composables.model

import androidx.compose.runtime.Immutable

/**
 * What an inline message is about, which picks its colors and icon: [Info], [Success], [Warning], [Error] or
 * [Neutral].
 */
enum class MessageIntent { Info, Success, Warning, Error, Neutral }

/**
 * A button on a banner.
 *
 * @property label the button's text, or the dismiss button's name and tooltip.
 * @property onClick called when it is pressed.
 */
@Immutable
class MessageAction(val label: String, val onClick: () -> Unit)

internal fun MessageIntent.dismissible(): Boolean =
    this == MessageIntent.Info || this == MessageIntent.Success || this == MessageIntent.Neutral

internal fun MessageIntent.buttonIntent(): ButtonIntent = when (this) {
    MessageIntent.Info -> ButtonIntent.Default
    MessageIntent.Success -> ButtonIntent.Success
    MessageIntent.Warning -> ButtonIntent.Warning
    MessageIntent.Error -> ButtonIntent.Critical
    MessageIntent.Neutral -> ButtonIntent.Default
}
