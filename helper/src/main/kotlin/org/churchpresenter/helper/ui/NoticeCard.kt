package org.churchpresenter.helper.ui

import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.material3.Icon
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Text

/**
 * A suggestion or a tip: something Wick brings up on its own, so it is set apart from its replies — a
 * card in the theme's third colour, edged, under a small [caption] saying which it is ("Suggestion"),
 * rather than a speech bubble.
 */
@Composable
internal fun NoticeCard(
    caption: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.tertiaryContainer.copy(alpha = NOTICE_ALPHA), NoticeShape)
            .border(1.dp, colors.tertiary.copy(alpha = NOTICE_EDGE_ALPHA), NoticeShape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NoticeCaption(caption)
        content()
    }
}

/** The label over a suggestion, a tip and the suggested requests: a lightbulb and its name, in the accent. */
@Composable
internal fun NoticeCaption(text: String, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(Icons.Outlined.Lightbulb, contentDescription = null, tint = accent, modifier = Modifier.size(15.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = accent,
        )
    }
}

private const val NOTICE_ALPHA = 0.55f
private const val NOTICE_EDGE_ALPHA = 0.6f
private val NoticeShape = RoundedCornerShape(14.dp)
