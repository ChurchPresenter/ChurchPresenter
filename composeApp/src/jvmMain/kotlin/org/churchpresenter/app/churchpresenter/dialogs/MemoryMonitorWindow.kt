package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import org.churchpresenter.icons.generated.resources.ic_app_icon
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
import org.churchpresenter.theme.AppThemeWrapper
import org.churchpresenter.theme.ThemeMode
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.lang.management.GarbageCollectorMXBean
import java.lang.management.ManagementFactory
import java.lang.management.MemoryMXBean
import org.churchpresenter.icons.generated.resources.Res as IconRes

private const val BYTES_PER_MB = 1024L * 1024L
private const val REFRESH_INTERVAL_MS = 1000L

private const val MAX_SAMPLES = 60

private fun formatMb(bytes: Long): String = "%,d MB".format(bytes / BYTES_PER_MB)

internal data class MemorySnapshot(
    val heapUsed: Long,
    val heapCommitted: Long,
    val heapMax: Long,
    val nonHeapUsed: Long,
    val nonHeapCommitted: Long,
    val gcCount: Long,
    val gcTimeMs: Long
)

/**
 * One JMX read: current heap/non-heap usage and cumulative GC counters. A bean reporting a
 * negative collection count/time (meaning the collector doesn't support it) counts as zero
 * rather than throwing off the total.
 */
internal fun readMemorySnapshot(memoryBean: MemoryMXBean, gcBeans: List<GarbageCollectorMXBean>): MemorySnapshot {
    val heap = memoryBean.heapMemoryUsage
    val nonHeap = memoryBean.nonHeapMemoryUsage
    return MemorySnapshot(
        heapUsed = heap.used,
        heapCommitted = heap.committed,
        heapMax = heap.max,
        nonHeapUsed = nonHeap.used,
        nonHeapCommitted = nonHeap.committed,
        gcCount = gcBeans.sumOf { if (it.collectionCount >= 0) it.collectionCount else 0L },
        gcTimeMs = gcBeans.sumOf { if (it.collectionTime >= 0) it.collectionTime else 0L }
    )
}

/** Appends [value] to [history], then evicts the oldest samples until at most [maxSamples] remain. */
internal fun appendSample(history: MutableList<Long>, value: Long, maxSamples: Int = MAX_SAMPLES) {
    history.add(value)
    while (history.size > maxSamples) history.removeAt(0)
}

/**
 * Developer-only live JVM memory monitor. Polls heap/non-heap usage and GC counters once a
 * second, renders a small sparkline of heap-used history, and offers a Force-GC button.
 * Reports JVM heap/non-heap only — native (Skia/JCEF/VLC) memory is not exposed via JMX.
 */
@Composable
fun MemoryMonitorWindow(isVisible: Boolean, theme: ThemeMode, onClose: () -> Unit) {
    if (!isVisible) return

    Window(
        onCloseRequest = onClose,
        title = stringResource(Res.string.memory_monitor_window_title),
        icon = painterResource(IconRes.drawable.ic_app_icon),
        // Named rather than inline so `DialogViewportTest` measures against this exact value — see
        // `DialogSizes.kt`, which records why it is 500dp and not the 440dp that shipped clipped.
        state = rememberWindowState(width = MEMORY_MONITOR_WINDOW_WIDTH, height = MEMORY_MONITOR_WINDOW_HEIGHT)
    ) {
        AppThemeWrapper(theme = theme) {
            MemoryMonitorContent()
        }
    }
}

@Composable
internal fun MemoryMonitorContent() {
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
            val snapshot = readMemorySnapshot(memoryBean, gcBeans)
            heapUsed = snapshot.heapUsed
            heapCommitted = snapshot.heapCommitted
            heapMax = snapshot.heapMax
            nonHeapUsed = snapshot.nonHeapUsed
            nonHeapCommitted = snapshot.nonHeapCommitted
            gcCount = snapshot.gcCount
            gcTimeMs = snapshot.gcTimeMs

            appendSample(history, snapshot.heapUsed)

            delay(REFRESH_INTERVAL_MS)
        }
    }

    MemoryMonitorDialogContent(
        heapUsed = heapUsed,
        heapCommitted = heapCommitted,
        heapMax = heapMax,
        nonHeapUsed = nonHeapUsed,
        nonHeapCommitted = nonHeapCommitted,
        gcCount = gcCount,
        gcTimeMs = gcTimeMs,
        history = history,
        onForceGc = { System.gc() },
    )
}

@Composable
internal fun MemoryMonitorDialogContent(
    heapUsed: Long,
    heapCommitted: Long,
    heapMax: Long,
    nonHeapUsed: Long,
    nonHeapCommitted: Long,
    gcCount: Long,
    gcTimeMs: Long,
    history: List<Long>,
    onForceGc: () -> Unit,
) {
    val usedFraction = if (heapMax > 0) (heapUsed.toFloat() / heapMax.toFloat()).coerceIn(0f, 1f) else 0f

    Surface(modifier = Modifier.fillMaxWidth()) {
        // Scrolls as a floor, not as the plan: at 440dp this window opened with the bottom 26dp of
        // its own content cut off as soon as text grew — 414dp of readings in a 440dp window is 6%
        // of slack, and OS font scaling past ~1.06x spent it. The height below is set so nothing
        // scrolls at ordinary sizes; this is what stops the Force GC row disappearing silently if
        // that budget is ever exceeded again, whether by scaling, translation, or the window being
        // dragged short.
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
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
                Button(onClick = onForceGc) {
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
