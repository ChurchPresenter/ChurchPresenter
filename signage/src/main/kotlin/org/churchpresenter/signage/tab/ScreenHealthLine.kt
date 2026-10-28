package org.churchpresenter.signage.tab

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import org.churchpresenter.signage.core.PlayerMessage
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.signage_health_cpu
import org.churchpresenter.strings.generated.resources.signage_health_memory
import org.churchpresenter.strings.generated.resources.signage_health_temperature
import org.churchpresenter.strings.generated.resources.signage_health_up_days
import org.churchpresenter.strings.generated.resources.signage_health_up_hours
import org.churchpresenter.strings.generated.resources.signage_health_up_minutes
import org.churchpresenter.theme.semantic
import org.jetbrains.compose.resources.stringResource

/**
 * A connected screen's health, as its last status reported it: processor, memory, temperature and
 * uptime, only the parts its player sent. Hot (from 80 °C) or nearly out of memory (from 90%) is in
 * the warning colour. Nothing for a screen whose player sends none.
 */
@Composable
internal fun ScreenHealthLine(
    status: PlayerMessage.Status?,
    /** Only a screen running hot or short of memory: the line as a warning, on the card. */
    onlyWhenStrained: Boolean = false,
) {
    status ?: return
    val parts = listOfNotNull(
        status.cpuPercent?.let { stringResource(Res.string.signage_health_cpu, it) },
        status.memoryPercent?.let { stringResource(Res.string.signage_health_memory, it) },
        status.temperatureC?.let { stringResource(Res.string.signage_health_temperature, it.toInt()) },
        status.uptimeSeconds?.let { uptime(it) },
    )
    if (parts.isEmpty()) return
    val strained = (status.temperatureC ?: 0.0) >= HOT_C || (status.memoryPercent ?: 0) >= FULL_PERCENT
    if (onlyWhenStrained && !strained) return
    Text(
        parts.joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = if (strained) MaterialTheme.semantic.warning else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun uptime(seconds: Long): String = when {
    seconds >= SECONDS_PER_DAY ->
        stringResource(Res.string.signage_health_up_days, (seconds / SECONDS_PER_DAY).toInt())
    seconds >= SECONDS_PER_HOUR ->
        stringResource(Res.string.signage_health_up_hours, (seconds / SECONDS_PER_HOUR).toInt())
    else -> stringResource(Res.string.signage_health_up_minutes, (seconds / SECONDS_PER_MINUTE).toInt())
}

private const val HOT_C = 80.0
private const val FULL_PERCENT = 90
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L
private const val SECONDS_PER_DAY = 86_400L
