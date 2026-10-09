package org.churchpresenter.helper.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.churchpresenter.helper.HelperText
import org.churchpresenter.helper.suggest.HELPER_COMMANDS
import org.churchpresenter.helper.suggest.HelperCommand
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.helper_commands_intro
import org.churchpresenter.strings.generated.resources.helper_commands_wick_does
import org.churchpresenter.strings.generated.resources.helper_commands_you_type
import org.jetbrains.compose.resources.stringResource

private val TableShape = RoundedCornerShape(14.dp)
private const val EXAMPLE_WEIGHT = 0.52f
private const val DOES_WEIGHT = 0.48f
private const val ROW_HOVER_LIFT = 0.1f
private const val DIVIDER_ALPHA = 0.5f

/** "Help": everything Wick understands, by area — what to type beside what it does. Clicking a row asks it. */
@Composable
internal fun CommandsTable(ask: Ask) {
    Said(HelperText.Res(Res.string.helper_commands_intro))
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(TableShape)
            .background(lifted(CARD_LIFT), TableShape)
            .border(1.dp, colors.outlineVariant.copy(alpha = LINE_ALPHA), TableShape)
            .testTag("helper.commands"),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            HeaderCell(stringResource(Res.string.helper_commands_you_type), Modifier.weight(EXAMPLE_WEIGHT))
            HeaderCell(stringResource(Res.string.helper_commands_wick_does), Modifier.weight(DOES_WEIGHT))
        }
        HELPER_COMMANDS.forEach { section ->
            HorizontalDivider(color = colors.outlineVariant.copy(alpha = DIVIDER_ALPHA))
            Text(
                stringResource(section.title),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.primary,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 4.dp),
            )
            section.commands.forEach { CommandRow(it, ask) }
        }
    }
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun CommandRow(command: HelperCommand, ask: Ask) {
    val example = stringResource(command.example)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (hovered) lifted(ROW_HOVER_LIFT) else Color.Transparent)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null) { ask(example, command.request) }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "“$example”",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(EXAMPLE_WEIGHT),
        )
        Text(
            stringResource(command.does),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(DOES_WEIGHT),
        )
    }
}
