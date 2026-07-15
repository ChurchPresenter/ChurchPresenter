package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.ic_app_icon
import churchpresenter.composeapp.generated.resources.memory_monitor_committed
import churchpresenter.composeapp.generated.resources.memory_monitor_force_gc
import churchpresenter.composeapp.generated.resources.memory_monitor_gc
import churchpresenter.composeapp.generated.resources.memory_monitor_heap
import churchpresenter.composeapp.generated.resources.memory_monitor_max
import churchpresenter.composeapp.generated.resources.memory_monitor_native_note
import churchpresenter.composeapp.generated.resources.memory_monitor_non_heap
import churchpresenter.composeapp.generated.resources.memory_monitor_used
import churchpresenter.composeapp.generated.resources.memory_monitor_window_title
import kotlinx.coroutines.delay
import org.churchpresenter.app.churchpresenter.ui.theme.AppThemeWrapper
import org.churchpresenter.app.churchpresenter.ui.theme.ThemeMode
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.lang.management.ManagementFactory

private const val MAX_SAMPLES = 60

private fun formatMb(bytes: Long): String = "%,d MB".format(bytes / (1024L * 1024L))

/**
 * Developer-only live JVM memory monitor. Polls heap/non-heap usage and GC counters once a
 * second, renders a small sparkline of heap-used history, and offers a Force-GC button.
 * Reports JVM heap/non-heap only — native (Skia/JCEF/VLC) memory is not exposed via JMX.
 */
@Composable
fun MemoryMonitorWindow(theme: ThemeMode, onClose: () -> Unit) {
    Window(
        onCloseRequest = onClose,
        title = stringResource(Res.string.memory_monitor_window_title),
        icon = painterResource(Res.drawable.ic_app_icon),
        state = rememberWindowState(width = 460.dp, height = 440.dp)
    ) {
        AppThemeWrapper(theme = theme) {
            MemoryMonitorContent()
        }
    }
}

@Composable
private fun MemoryMonitorContent() {
    val memoryBean = remember { ManagementFactory.getMemoryMXBean() }
    val gcBeans = remember { ManagementFactory.getGarbageCollectorMXBeans() }

    var heapUsed by remember { mutableStateOf(0L) }
    var heapCommitted by remember { mutableStateOf(0L) }
    var heapMax by remember { mutableStateOf(0L) }
    var nonHeapUsed by remember { mutableStateOf(0L) }
    var nonHeapCommitted by remember { mutableStateOf(0L) }
    var gcCount by remember { mutableStateOf(0L) }
    var gcTimeMs by remember { mutableStateOf(0L) }
    val history: SnapshotStateList<Long> = remember { mutableListOf<Long>().toMutableStateList() }

    LaunchedEffect(Unit) {
        while (true) {
            val heap = memoryBean.heapMemoryUsage
            val nonHeap = memoryBean.nonHeapMemoryUsage
            heapUsed = heap.used
            heapCommitted = heap.committed
            heapMax = heap.max
            nonHeapUsed = nonHeap.used
            nonHeapCommitted = nonHeap.committed
            gcCount = gcBeans.sumOf { if (it.collectionCount >= 0) it.collectionCount else 0L }
            gcTimeMs = gcBeans.sumOf { if (it.collectionTime >= 0) it.collectionTime else 0L }

            history.add(heap.used)
            while (history.size > MAX_SAMPLES) history.removeAt(0)

            delay(1000)
        }
    }

    val usedFraction = if (heapMax > 0) (heapUsed.toFloat() / heapMax.toFloat()).coerceIn(0f, 1f) else 0f

    Surface(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = stringResource(Res.string.memory_monitor_heap),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { usedFraction },
                modifier = Modifier.fillMaxWidth().height(10.dp)
            )
            Spacer(Modifier.height(6.dp))
            StatRow(stringResource(Res.string.memory_monitor_used), formatMb(heapUsed))
            StatRow(stringResource(Res.string.memory_monitor_committed), formatMb(heapCommitted))
            StatRow(
                stringResource(Res.string.memory_monitor_max),
                if (heapMax > 0) formatMb(heapMax) else "—"
            )

            Spacer(Modifier.height(14.dp))
            HeapSparkline(history)

            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(Res.string.memory_monitor_non_heap),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            StatRow(stringResource(Res.string.memory_monitor_used), formatMb(nonHeapUsed))
            StatRow(stringResource(Res.string.memory_monitor_committed), formatMb(nonHeapCommitted))

            Spacer(Modifier.height(10.dp))
            StatRow(stringResource(Res.string.memory_monitor_gc), "$gcCount (${gcTimeMs} ms)")

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(Res.string.memory_monitor_native_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = { System.gc() }) {
                    Text(stringResource(Res.string.memory_monitor_force_gc))
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun HeapSparkline(history: List<Long>) {
    val lineColor = MaterialTheme.colorScheme.primary
    Canvas(modifier = Modifier.fillMaxWidth().height(60.dp)) {
        if (history.size < 2) return@Canvas
        val maxValue = (history.maxOrNull() ?: 1L).coerceAtLeast(1L).toFloat()
        val stepX = size.width / (MAX_SAMPLES - 1).toFloat()
        val path = Path()
        history.forEachIndexed { index, value ->
            val x = index * stepX
            val y = size.height - (value.toFloat() / maxValue) * size.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 2f)
        )
        // End-point marker
        val lastX = (history.size - 1) * stepX
        val lastY = size.height - (history.last().toFloat() / maxValue) * size.height
        drawCircle(color = lineColor, radius = 3f, center = Offset(lastX, lastY))
    }
}
