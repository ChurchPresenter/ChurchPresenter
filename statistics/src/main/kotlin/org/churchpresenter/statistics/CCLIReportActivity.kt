package org.churchpresenter.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import org.churchpresenter.theme.AppShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.ccli_activity_title
import org.churchpresenter.strings.generated.resources.ccli_legend_bible
import org.churchpresenter.strings.generated.resources.obs_mode_songs
import org.churchpresenter.strings.generated.resources.ccli_no_data
import org.churchpresenter.strings.generated.resources.ccli_stat_bible_verses
import org.churchpresenter.strings.generated.resources.ccli_stat_busiest
import org.churchpresenter.strings.generated.resources.ccli_stat_songs_presented
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.theme.semantic

@Composable
internal fun ActivityContent(activity: List<ActivityPoint>) {
    val primary = MaterialTheme.colorScheme.primary
    val verseColor = MaterialTheme.semantic.success

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (activity.isEmpty() || activity.all { it.songCount + it.verseCount == 0 }) {
            EmptyState(stringResource(Res.string.ccli_no_data))
            return
        }

        // Summary stat cards
        val totalSongs = activity.sumOf { it.songCount }
        val totalVerses = activity.sumOf { it.verseCount }
        val busiest = activity.maxByOrNull { it.songCount + it.verseCount }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            StatCard(stringResource(Res.string.ccli_stat_songs_presented), "$totalSongs", primary)
            StatCard(stringResource(Res.string.ccli_stat_bible_verses), "$totalVerses", verseColor)
            StatCard(
                stringResource(Res.string.ccli_stat_busiest),
                if (busiest != null) "${busiest.label} (${busiest.songCount + busiest.verseCount})" else "—",
                MaterialTheme.semantic.warning
            )
        }

        Spacer(Modifier.height(16.dp))

        // Chart header + date range
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(bottom = 8.dp)) {
            Text(
                stringResource(Res.string.ccli_activity_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "${activity.first().label} – ${activity.last().label}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Chart
        ActivityBarChart(
            data = activity,
            songColor = primary,
            verseColor = verseColor,
            modifier = Modifier.weight(1f).fillMaxWidth()
        )

        // Legend
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            LegendDot(primary)
            Spacer(Modifier.width(4.dp))
            Text(
                stringResource(Res.string.obs_mode_songs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(16.dp))
            LegendDot(verseColor)
            Spacer(Modifier.width(4.dp))
            Text(
                stringResource(Res.string.ccli_legend_bible),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Tables ────────────────────────────────────────────────────────────────────

@Composable
internal fun ChartPanelHeader(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

/**
 * Ranked list where each row shows the label and its value on one line with a thin
 * gradient bar below it, sized relative to the largest value. The bar is a quick
 * visual ranking indicator, not an axis-based chart.
 */
@Composable
internal fun TopItemsChart(
    data: List<Pair<String, Int>>,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val maxValue = data.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val brightEnd = lerp(accent, Color.White, 0.35f)
    val barBrush = Brush.horizontalGradient(listOf(accent, brightEnd))
    val scrollState = rememberScrollState()

    Box(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(end = 14.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            data.forEach { (label, value) ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            label,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "$value",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = accent
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(AppShape(3.dp))
                            .background(trackColor)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction = value.toFloat() / maxValue)
                                .clip(AppShape(3.dp))
                                .background(barBrush)
                        )
                    }
                }
            }
        }
        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            adapter = rememberScrollbarAdapter(scrollState)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActivityBarChart(
    data: List<ActivityPoint>,
    songColor: Color,
    verseColor: Color,
    modifier: Modifier = Modifier
) {
    val maxTotal = data.maxOf { it.songCount + it.verseCount }.coerceAtLeast(1)
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val songsLabel = stringResource(Res.string.obs_mode_songs)
    val versesLabel = stringResource(Res.string.ccli_legend_bible)
    val barCorner = AppShape(topStart = 4.dp, topEnd = 4.dp)
    // Lighter at the top, fading down to the series color.
    val songBrush = Brush.verticalGradient(listOf(lerp(songColor, Color.White, 0.3f), songColor))
    val verseBrush = Brush.verticalGradient(listOf(lerp(verseColor, Color.White, 0.3f), verseColor))
    val barWidth = when {
        data.size <= 14 -> 18.dp
        data.size <= 26 -> 9.dp
        else -> 5.dp
    }

    // How many labels to show on x-axis to avoid crowding
    val labelStep = ((data.size / 10) + 1).coerceAtLeast(1)

    Column(modifier = modifier) {
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            ActivityYAxis(maxTotal, labelColor)

            // Plot area: grid lines behind grouped bars
            Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(bottom = 4.dp)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    for (i in 0..4) {
                        val y = size.height * (1f - i / 4f)
                        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 0.8f)
                    }
                }
                Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.Bottom) {
                    data.forEach { pt ->
                        val songFrac = pt.songCount.toFloat() / maxTotal
                        val verseFrac = pt.verseCount.toFloat() / maxTotal
                        TooltipArea(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            tooltip = { ActivityTooltip(pt, songsLabel, versesLabel) },
                            tooltipPlacement = TooltipPlacement.ComponentRect(
                                anchor = Alignment.TopCenter,
                                offset = DpOffset(0.dp, (-TOOLTIP_VERTICAL_OFFSET_DP).dp)
                            )
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally)
                            ) {
                                if (pt.songCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .width(barWidth)
                                            .fillMaxHeight(songFrac.coerceIn(MIN_BAR_FRACTION, 1f))
                                            .clip(barCorner)
                                            .background(songBrush)
                                    )
                                }
                                if (pt.verseCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .width(barWidth)
                                            .fillMaxHeight(verseFrac.coerceIn(MIN_BAR_FRACTION, 1f))
                                            .clip(barCorner)
                                            .background(verseBrush)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // X-axis labels (aligned under the plot area, past the 30dp y-axis gutter)
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 30.dp),
            horizontalArrangement = Arrangement.Start
        ) {
            data.forEachIndexed { idx, pt ->
                Box(modifier = Modifier.weight(1f)) {
                    if (idx % labelStep == 0 || idx == data.size - 1) {
                        Text(
                            pt.label,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = labelColor,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }
        }
    }
}

// ── Date picker ───────────────────────────────────────────────────────────────

@Composable
private fun RowScope.StatCard(label: String, value: String, color: Color) {
    Column(
        modifier = Modifier
            .weight(1f)
            .background(color.copy(alpha = 0.10f), AppShape(10.dp))
            .border(1.dp, color.copy(alpha = 0.35f), AppShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun LegendDot(color: Color) {
    Box(modifier = Modifier.size(10.dp).background(color, AppShape(2.dp)))
}

private const val AXIS_TICK_COUNT = 4

private const val TOOLTIP_VERTICAL_OFFSET_DP = 6

private const val MIN_BAR_FRACTION = 0.004f

/** One bar group's tooltip: the period, then its song and verse counts. */
@Composable
private fun ActivityTooltip(point: ActivityPoint, songsLabel: String, versesLabel: String) {
    Surface(
        color = MaterialTheme.colorScheme.inverseSurface,
        shape = MaterialTheme.shapes.extraSmall,
        tonalElevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(
                point.label,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "$songsLabel: ${point.songCount}",
                color = MaterialTheme.colorScheme.inverseOnSurface,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "$versesLabel: ${point.verseCount}",
                color = MaterialTheme.colorScheme.inverseOnSurface,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/** The chart's count axis, from [maxTotal] at the top down to zero. */
@Composable
private fun ActivityYAxis(maxTotal: Int, labelColor: Color) {
    Column(
        modifier = Modifier.width(30.dp).fillMaxHeight().padding(bottom = 4.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        for (i in AXIS_TICK_COUNT downTo 0) {
            Text(
                "${(maxTotal * i / AXIS_TICK_COUNT)}",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = labelColor,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
