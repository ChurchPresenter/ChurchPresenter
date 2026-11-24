package org.churchpresenter.hosanna.composables.model

import androidx.compose.runtime.Immutable

enum class MessageIntent { Info, Success, Warning, Error, Neutral }

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
