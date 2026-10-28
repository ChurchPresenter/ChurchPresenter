package org.churchpresenter.signage.tab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.churchpresenter.signage.core.Features
import org.churchpresenter.signage.core.PlayerMessage
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.signage_web_test_cpu
import org.churchpresenter.strings.generated.resources.signage_web_test_failed
import org.churchpresenter.strings.generated.resources.signage_web_test_gpu
import org.churchpresenter.strings.generated.resources.signage_web_test_hot
import org.churchpresenter.strings.generated.resources.signage_web_test_normal_only
import org.churchpresenter.strings.generated.resources.signage_web_test_run
import org.churchpresenter.strings.generated.resources.signage_web_test_running
import org.churchpresenter.strings.generated.resources.signage_web_test_smooth
import org.churchpresenter.strings.generated.resources.signage_web_test_software
import org.churchpresenter.strings.generated.resources.signage_web_test_too_slow
import org.churchpresenter.theme.semantic
import org.jetbrains.compose.resources.stringResource
import java.util.Locale

/**
 * For a connected screen that can draw web pages: the web page test running, then what it found (the
 * card's test icon starts it). Nothing for any other screen, or before a test.
 */
@Composable
internal fun WebTestLine(row: ScreenRowState) {
    val link = row.link ?: return
    if (Features.WEB_TEST !in link.features) return
    val result = link.webTest
    when {
        link.webTestStartedAtMs != null -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            Text(stringResource(Res.string.signage_web_test_running), style = MaterialTheme.typography.bodySmall)
        }
        result != null -> WebTestResultText(result)
    }
}

@Composable
private fun WebTestResultText(result: PlayerMessage.WebTestResult) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val verdict = result.verdict()
        val (text, color) = when {
            result.error != null -> {
                stringResource(Res.string.signage_web_test_failed, result.error.orEmpty()) to
                    MaterialTheme.colorScheme.error
            }
            verdict == WebTestVerdict.SMOOTH ->
                stringResource(Res.string.signage_web_test_smooth) to MaterialTheme.semantic.success
            verdict == WebTestVerdict.TOO_SLOW ->
                stringResource(Res.string.signage_web_test_too_slow) to MaterialTheme.colorScheme.error
            else -> stringResource(Res.string.signage_web_test_normal_only) to MaterialTheme.semantic.warning
        }
        Text(text, style = MaterialTheme.typography.bodySmall, color = color)
        val runs = result.runs.map { run ->
            val shown = String.format(Locale.getDefault(), "%.1f", run.shownFps)
            stringResource(Res.string.signage_web_test_run, run.fps, shown)
        }
        val busiest = result.runs.mapNotNull { it.cpuPercent }.maxOrNull()
        val drawnBy = result.gpu?.let { gpu ->
            stringResource(if (gpu) Res.string.signage_web_test_gpu else Res.string.signage_web_test_software)
        }
        val cpu = busiest?.let { stringResource(Res.string.signage_web_test_cpu, it.toInt()) }
        val numbers = runs + listOfNotNull(cpu, drawnBy)
        if (numbers.isNotEmpty()) {
            Text(
                numbers.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        result.hotDegrees()?.let { degrees ->
            Text(
                stringResource(Res.string.signage_web_test_hot, degrees),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.semantic.warning,
            )
        }
    }
}
