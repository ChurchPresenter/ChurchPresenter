package org.churchpresenter.serverui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.components.KeyButton
import org.churchpresenter.theme.components.RaisedButton

/** A neutral raised key at the card's button height. */
@Composable
internal fun CompactKey(text: String, onClick: () -> Unit) {
    KeyButton(
        onClick = onClick,
        shape = AppShape(8.dp),
        contentPadding = COMPACT_PADDING,
        modifier = Modifier.compactButton(),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

/** The filled, primary key at the card's button height -- one per group. */
@Composable
internal fun CompactPrimary(text: String, onClick: () -> Unit) {
    RaisedButton(
        onClick = onClick,
        shape = AppShape(8.dp),
        contentPadding = COMPACT_PADDING,
        modifier = Modifier.compactButton(),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

/** Secondary words in the muted ink. */
@Composable
internal fun DimText(text: String, modifier: Modifier = Modifier, small: Boolean = false) {
    Text(
        text,
        style = if (small) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

internal val COMPACT_PADDING = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
private val COMPACT_HEIGHT = 32.dp

/** A button row's height in the card. */
internal fun Modifier.compactButton(): Modifier = this.height(COMPACT_HEIGHT)
