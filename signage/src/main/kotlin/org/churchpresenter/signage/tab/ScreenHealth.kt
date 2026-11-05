package org.churchpresenter.signage.tab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.VerticalDivider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import org.churchpresenter.strings.generated.resources.signage_chip_cpu
import org.churchpresenter.strings.generated.resources.signage_chip_days
import org.churchpresenter.strings.generated.resources.signage_chip_hours
import org.churchpresenter.strings.generated.resources.signage_chip_minutes
import org.churchpresenter.strings.generated.resources.signage_chip_up
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import org.churchpresenter.signage.core.PlayerMessage
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.signage_health_temperature
import org.churchpresenter.strings.generated.resources.signage_chip_memory
import org.churchpresenter.strings.generated.resources.signage_chip_percent
import org.churchpresenter.strings.generated.resources.signage_chip_temperature
import org.churchpresenter.theme.semantic
import org.jetbrains.compose.resources.stringResource

/**
 * Only what needs a look on a connected screen, as amber chips: memory from 90% and temperature from
 * 80 °C. The rest of its health is in its settings; nothing at all while it is well.
 */
@Composable
internal fun StrainChips(status: PlayerMessage.Status?) {
    status ?: return
    val warn = MaterialTheme.semantic.warning
    val chips = listOfNotNull(
        status.memoryPercent?.takeIf { it >= FULL_PERCENT }?.let {
            Res.string.signage_chip_memory to stringResource(Res.string.signage_chip_percent, it)
        },
        status.temperatureC?.takeIf { it >= HOT_C }?.let {
            Res.string.signage_chip_temperature to stringResource(Res.string.signage_health_temperature, it.toInt())
        },
    )
    if (chips.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        chips.forEach { (name, value) -> HealthChip(stringResource(name), value, warn) }
    }
}

/**
 * A connected screen's health in the settings dialog, as a row of cells: CPU, memory, temperature and
 * how long it has been up, only the readings its player sent. Memory from 90% and temperature from
 * 80 °C are in the warning colour. Nothing for a screen that sends none.
 */
@Composable
internal fun HealthGrid(status: PlayerMessage.Status?) {
    status ?: return
    val warn = MaterialTheme.semantic.warning
    val cells = listOfNotNull(
        status.cpuPercent?.let {
            Triple(Res.string.signage_chip_cpu, stringResource(Res.string.signage_chip_percent, it), false)
        },
        status.memoryPercent?.let {
            val value = stringResource(Res.string.signage_chip_percent, it)
            Triple(Res.string.signage_chip_memory, value, it >= FULL_PERCENT)
        },
        status.temperatureC?.let {
            val value = stringResource(Res.string.signage_health_temperature, it.toInt())
            Triple(Res.string.signage_chip_temperature, value, it >= HOT_C)
        },
        status.uptimeSeconds?.let { Triple(Res.string.signage_chip_up, uptimeLong(it), false) },
    )
    if (cells.isEmpty()) return
    val edge = MaterialTheme.colorScheme.outlineVariant
    val shape = RoundedCornerShape(12.dp)
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).border(1.dp, edge, shape)) {
        cells.forEachIndexed { index, (name, value, strained) ->
            if (index > 0) VerticalDivider(color = edge)
            Column(
                modifier = Modifier.weight(1f)
                    .background(if (strained) warn.copy(alpha = STRAINED_WASH) else Color.Transparent)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    stringResource(name),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (strained) warn else MaterialTheme.colorScheme.outline,
                )
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (strained) warn else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun uptimeLong(seconds: Long): String = when {
    seconds >= SECONDS_PER_DAY -> stringResource(Res.string.signage_chip_days, (seconds / SECONDS_PER_DAY).toInt())
    seconds >= SECONDS_PER_HOUR -> stringResource(Res.string.signage_chip_hours, (seconds / SECONDS_PER_HOUR).toInt())
    else -> stringResource(Res.string.signage_chip_minutes, (seconds / SECONDS_PER_MINUTE).toInt())
}

private const val HOT_C = 80.0
private const val STRAINED_WASH = 0.12f
private const val FULL_PERCENT = 90
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L
private const val SECONDS_PER_DAY = 86_400L
