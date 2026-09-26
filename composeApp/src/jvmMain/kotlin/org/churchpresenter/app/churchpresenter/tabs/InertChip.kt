package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.elevationPalette

private val INERT_CHIP_HEIGHT = 32.dp
private val INERT_CHIP_RADIUS = 8.dp
private const val INERT_CHIP_ALPHA = 0.45f

/**
 * A `RaisedFilterChip` that cannot be pressed right now, drawn as the disabled chip but with no
 * click action at all -- not a disabled one. For a control whose action does not apply yet: a
 * disabled click still reads as a button that swallows the press, which is what this replaces.
 */
@Composable
internal fun InertChip(
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
) {
    val ink = elevationPalette().key.ink
    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .defaultMinSize(minHeight = INERT_CHIP_HEIGHT)
            .clip(AppShape(INERT_CHIP_RADIUS))
            .alpha(INERT_CHIP_ALPHA)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides ink) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(color = ink)) {
                leadingIcon?.invoke()
                label()
            }
        }
    }
}
