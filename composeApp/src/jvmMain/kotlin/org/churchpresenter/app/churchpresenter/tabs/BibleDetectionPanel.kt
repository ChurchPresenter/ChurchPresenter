package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import org.churchpresenter.theme.components.KeyIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.bible_next_verse_speed_balanced
import churchpresenter.composeapp.generated.resources.bible_next_verse_speed_fast
import churchpresenter.composeapp.generated.resources.bible_next_verse_speed_label
import churchpresenter.composeapp.generated.resources.bible_next_verse_speed_tooltip_balanced
import churchpresenter.composeapp.generated.resources.bible_next_verse_speed_tooltip_fast
import churchpresenter.composeapp.generated.resources.bible_stt_auto_follow
import churchpresenter.composeapp.generated.resources.bible_stt_auto_follow_hint
import churchpresenter.composeapp.generated.resources.bible_stt_clear
import churchpresenter.composeapp.generated.resources.bible_stt_detected_version_tooltip
import churchpresenter.composeapp.generated.resources.bible_stt_engine_connecting
import churchpresenter.composeapp.generated.resources.bible_stt_engine_stt_down
import churchpresenter.composeapp.generated.resources.bible_stt_engine_unavailable
import churchpresenter.composeapp.generated.resources.bible_stt_flag_missed
import churchpresenter.composeapp.generated.resources.bible_stt_flag_missed_hint
import churchpresenter.composeapp.generated.resources.bible_stt_flag_needs_live
import churchpresenter.composeapp.generated.resources.bible_stt_flag_premature
import churchpresenter.composeapp.generated.resources.bible_stt_flag_premature_hint
import churchpresenter.composeapp.generated.resources.bible_stt_flag_wrong
import churchpresenter.composeapp.generated.resources.bible_stt_flag_wrong_hint
import churchpresenter.composeapp.generated.resources.bible_stt_level_aggressive
import churchpresenter.composeapp.generated.resources.bible_stt_level_balanced
import churchpresenter.composeapp.generated.resources.bible_stt_level_conservative
import churchpresenter.composeapp.generated.resources.bible_stt_level_off
import churchpresenter.composeapp.generated.resources.bible_stt_listening
import churchpresenter.composeapp.generated.resources.bible_stt_match_label
import churchpresenter.composeapp.generated.resources.bible_stt_no_bible
import churchpresenter.composeapp.generated.resources.bible_stt_src_chapter_history
import churchpresenter.composeapp.generated.resources.bible_stt_src_chapter_scan
import churchpresenter.composeapp.generated.resources.bible_stt_src_continuation
import churchpresenter.composeapp.generated.resources.bible_stt_src_explicit
import churchpresenter.composeapp.generated.resources.bible_stt_src_reverse
import churchpresenter.composeapp.generated.resources.bible_stt_text_match_hint
import churchpresenter.composeapp.generated.resources.bible_stt_track_transcription
import churchpresenter.composeapp.generated.resources.bible_stt_track_translation
import churchpresenter.composeapp.generated.resources.bible_stt_waiting_for_stt
import churchpresenter.composeapp.generated.resources.ic_close
import churchpresenter.composeapp.generated.resources.stt_status_connecting
import churchpresenter.composeapp.generated.resources.stt_status_not_connected
import churchpresenter.composeapp.generated.resources.stt_status_reconnecting
import churchpresenter.composeapp.generated.resources.stt_status_unreachable
import kotlinx.coroutines.delay
import org.churchpresenter.app.churchpresenter.composables.initialPassCombinedClickable
import org.churchpresenter.app.churchpresenter.viewmodel.BibleSttStatus
import org.churchpresenter.app.churchpresenter.viewmodel.DetectedReference
import org.churchpresenter.app.churchpresenter.viewmodel.ContinuationSpeed
import org.churchpresenter.app.churchpresenter.viewmodel.DetectionSource
import org.churchpresenter.app.churchpresenter.viewmodel.DetectionTrack
import org.churchpresenter.app.churchpresenter.viewmodel.TextMatchLevel
import org.churchpresenter.theme.semantic
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.theme.components.RaisedFilterChip
import androidx.compose.material3.LocalContentColor

private const val SELECTION_BAR_WIDTH = 4f

/**
 * One detected-reference row, **measured rather than imposed**.
 *
 * The rows are content-sized — single-line `bodySmall` with 4dp above and below — so that they keep
 * the same vertical rhythm as the History panel beside them. They were pinned at a fixed height once
 * and `7e5b2f94e` deliberately un-pinned them for exactly that reason, so this number agrees with
 * the typography rather than dictating it. `BibleDetectionPanelTest` measures a real row against it,
 * which is what turns a drift in `bodySmall` into a failing test instead of a clipped fourth row.
 */
internal val DETECTION_ROW_HEIGHT = 24.dp
internal const val DETECTION_VISIBLE_ROWS = 4

/**
 * The list's height, reserved from the moment the panel appears and never changed again.
 *
 * A *fixed* height rather than a maximum: the browser below takes `weight(1f)`, so a list that grows
 * with its contents moves the book, chapter and verse columns under the operator's cursor every time
 * the engine detects something — four times over, as the first four detections land.
 */
internal val DETECTION_LIST_HEIGHT = DETECTION_ROW_HEIGHT * DETECTION_VISIBLE_ROWS

/** Test handle for the reserved list area. */
internal const val DETECTION_LIST_TAG = "bible_detection_list"

/** Test handle for one detected-reference row, so its measured height can be held to the constant. */
internal const val DETECTION_ROW_TAG = "bible_detection_row"

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BibleDetectionPanel(
    status: BibleSttStatus,
    statusIsError: Boolean,
    autoFollowEnabled: Boolean,
    textMatchLevel: TextMatchLevel,
    continuationSpeed: ContinuationSpeed,
    detections: List<DetectedReference>,
    selectedIndex: Int,

    showFlagButtons: Boolean,

    canFlagLive: Boolean,
    onAutoFollowChange: (Boolean) -> Unit,
    onTextMatchLevelChange: (TextMatchLevel) -> Unit,
    onContinuationSpeedChange: (ContinuationSpeed) -> Unit,
    onFlag: (kind: String) -> Unit,
    onClearDetections: () -> Unit,
    onDetectionClick: (Int) -> Unit,
    onDetectionDoubleClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
            val levelName = when (textMatchLevel) {
                TextMatchLevel.OFF -> stringResource(Res.string.bible_stt_level_off)
                TextMatchLevel.CONSERVATIVE -> stringResource(Res.string.bible_stt_level_conservative)
                TextMatchLevel.BALANCED -> stringResource(Res.string.bible_stt_level_balanced)
                TextMatchLevel.AGGRESSIVE -> stringResource(Res.string.bible_stt_level_aggressive)
            }

            val statusText = when (status) {
                BibleSttStatus.ENGINE_UNAVAILABLE -> stringResource(Res.string.bible_stt_engine_unavailable)
                BibleSttStatus.NO_BIBLE -> stringResource(Res.string.bible_stt_no_bible)
                BibleSttStatus.ENGINE_CONNECTING -> stringResource(Res.string.bible_stt_engine_connecting)
                BibleSttStatus.ENGINE_STT_DOWN -> stringResource(Res.string.bible_stt_engine_stt_down)
                BibleSttStatus.WAITING_FOR_STT -> stringResource(Res.string.bible_stt_waiting_for_stt)
                BibleSttStatus.LISTENING -> stringResource(Res.string.bible_stt_listening)
                BibleSttStatus.RECONNECTING -> stringResource(Res.string.stt_status_reconnecting)
                BibleSttStatus.UNREACHABLE -> stringResource(Res.string.stt_status_unreachable)
                BibleSttStatus.CONNECTING -> stringResource(Res.string.stt_status_connecting)
                BibleSttStatus.NOT_CONNECTED -> stringResource(Res.string.stt_status_not_connected)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = null,
                        tint = if (statusIsError) MaterialTheme.colorScheme.error
                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                        color = if (statusIsError) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    )
                }

                EngineChip(
                    label = stringResource(Res.string.bible_stt_auto_follow),
                    icon = Icons.Default.CheckBoxOutlineBlank,
                    selected = autoFollowEnabled,
                    tooltip = stringResource(Res.string.bible_stt_auto_follow_hint),
                    onClick = { onAutoFollowChange(!autoFollowEnabled) },
                )

                EngineChip(
                    label = "${stringResource(Res.string.bible_stt_match_label)}: $levelName",
                    icon = Icons.AutoMirrored.Filled.FormatAlignLeft,
                    selected = textMatchLevel != TextMatchLevel.OFF,
                    tooltip = stringResource(Res.string.bible_stt_text_match_hint),
                    onClick = {
                        val all = TextMatchLevel.values()
                        onTextMatchLevelChange(all[(textMatchLevel.ordinal + 1) % all.size])
                    },
                )

                val verseSpeedName = when (continuationSpeed) {
                    ContinuationSpeed.BALANCED -> stringResource(Res.string.bible_next_verse_speed_balanced)
                    ContinuationSpeed.FAST -> stringResource(Res.string.bible_next_verse_speed_fast)
                }
                val verseSpeedHint = when (continuationSpeed) {
                    ContinuationSpeed.BALANCED -> stringResource(Res.string.bible_next_verse_speed_tooltip_balanced)
                    ContinuationSpeed.FAST -> stringResource(Res.string.bible_next_verse_speed_tooltip_fast)
                }
                EngineChip(
                    label = "${stringResource(Res.string.bible_next_verse_speed_label)}: $verseSpeedName",
                    icon = Icons.Filled.Speed,
                    selected = continuationSpeed != ContinuationSpeed.BALANCED,
                    tooltip = verseSpeedHint,
                    onClick = {
                        val all = ContinuationSpeed.values()
                        onContinuationSpeedChange(all[(continuationSpeed.ordinal + 1) % all.size])
                    },
                )
                if (showFlagButtons) {
                    FlagPillButton(
                        icon = Icons.Filled.Flag,
                        label = stringResource(Res.string.bible_stt_flag_wrong),
                        tooltip = stringResource(Res.string.bible_stt_flag_wrong_hint),
                        tint = MaterialTheme.colorScheme.error,

                        enabled = canFlagLive,
                        disabledTooltip = stringResource(Res.string.bible_stt_flag_needs_live),
                        onClick = { onFlag("wrong_passage") }
                    )
                    FlagPillButton(
                        icon = Icons.Filled.FastForward,
                        label = stringResource(Res.string.bible_stt_flag_premature),
                        tooltip = stringResource(Res.string.bible_stt_flag_premature_hint),
                        tint = MaterialTheme.colorScheme.tertiary,
                        enabled = canFlagLive,
                        disabledTooltip = stringResource(Res.string.bible_stt_flag_needs_live),
                        onClick = { onFlag("premature") }
                    )
                    FlagPillButton(
                        icon = Icons.Filled.SearchOff,
                        label = stringResource(Res.string.bible_stt_flag_missed),
                        tooltip = stringResource(Res.string.bible_stt_flag_missed_hint),

                        tint = MaterialTheme.colorScheme.secondary,
                        onClick = { onFlag("missed_passage") }
                    )
                }
                if (detections.isNotEmpty()) {
                    KeyIconButton(
                        onClick = onClearDetections,
                        modifier = Modifier.size(27.dp)
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_close),
                            contentDescription = stringResource(Res.string.bible_stt_clear),
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            val markerColor = MaterialTheme.semantic.marker
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val detScroll = rememberScrollState()
            Box(
                modifier = Modifier.fillMaxWidth()
                    .height(DETECTION_LIST_HEIGHT)
                    .testTag(DETECTION_LIST_TAG)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .verticalScroll(detScroll)
                        .padding(end = 10.dp)
                ) {
                detections.forEachIndexed { idx, ref ->
                val isSelected = idx == selectedIndex
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                        .testTag(DETECTION_ROW_TAG)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.surfaceVariant
                            else MaterialTheme.colorScheme.surface
                        )
                        .drawBehind {
                            if (isSelected) drawRect(color = markerColor, size = Size(SELECTION_BAR_WIDTH, size.height))
                        }
                        .initialPassCombinedClickable(
                            onClick = { onDetectionClick(idx) },
                            onDoubleClick = { onDetectionDoubleClick(idx) }
                        )
                        .padding(start = 12.dp, top = 4.dp, end = 6.dp, bottom = 4.dp)
                ) {

                    Row(
                        modifier = Modifier.width(96.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ref.sources.forEach { src ->
                            val (icon, descRes, tint) = when (src) {
                                DetectionSource.EXPLICIT -> Triple(
                                    Icons.Filled.RecordVoiceOver, Res.string.bible_stt_src_explicit,
                                    MaterialTheme.colorScheme.primary
                                )
                                DetectionSource.REVERSE -> Triple(
                                    Icons.Filled.FormatQuote, Res.string.bible_stt_src_reverse,
                                    MaterialTheme.colorScheme.tertiary
                                )
                                DetectionSource.CONTINUATION -> Triple(
                                    Icons.AutoMirrored.Filled.ArrowForward, Res.string.bible_stt_src_continuation,
                                    MaterialTheme.colorScheme.secondary
                                )
                                DetectionSource.CHAPTER_SCAN -> Triple(
                                    Icons.AutoMirrored.Filled.ManageSearch, Res.string.bible_stt_src_chapter_scan,
                                    MaterialTheme.colorScheme.tertiary
                                )
                                DetectionSource.CHAPTER_HISTORY -> Triple(
                                    Icons.Filled.History, Res.string.bible_stt_src_chapter_history,
                                    MaterialTheme.colorScheme.tertiary
                                )
                            }
                            TooltipArea(tooltip = {
                                Surface(shadowElevation = 4.dp, color = MaterialTheme.colorScheme.surfaceVariant) {
                                    Text(
                                        text = stringResource(descRes),
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = stringResource(descRes),
                                    tint = tint,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            Spacer(Modifier.width(3.dp))
                        }

                        listOf(
                            Triple(DetectionTrack.TRANSCRIPTION, Icons.Filled.Mic, Res.string.bible_stt_track_transcription),
                            Triple(DetectionTrack.TRANSLATION, Icons.Filled.Public, Res.string.bible_stt_track_translation),
                        ).forEach { (track, icon, descRes) ->
                            if (track in ref.tracks) {
                                TooltipArea(tooltip = {
                                    Surface(shadowElevation = 4.dp, color = MaterialTheme.colorScheme.surfaceVariant) {
                                        Text(
                                            text = stringResource(descRes),
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                }) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = stringResource(descRes),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                                Spacer(Modifier.width(3.dp))
                            }
                        }
                    }
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)) {
                                append(ref.label)
                            }
                            ref.verseText?.let { append("  $it") }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    ref.detectedVersion?.let { version ->
                        Spacer(Modifier.width(6.dp))
                        TooltipArea(tooltip = {
                            Surface(shadowElevation = 4.dp, color = MaterialTheme.colorScheme.surfaceVariant) {
                                Text(
                                    text = stringResource(Res.string.bible_stt_detected_version_tooltip),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }) {
                            Text(
                                text = version,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
                }
                }
                if (detections.size > DETECTION_VISIBLE_ROWS) {
                    VerticalScrollbar(
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                        adapter = rememberScrollbarAdapter(detScroll)
                    )
                }
            }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FlagPillButton(
    icon: ImageVector,
    label: String,
    tooltip: String,
    tint: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
    disabledTooltip: String? = null,
) {

    var flashing by remember { mutableStateOf(false) }
    LaunchedEffect(flashing) {
        if (flashing) {
            delay(FLAG_FLASH_MS)
            flashing = false
        }
    }

    TooltipArea(tooltip = { EngineTooltip(if (enabled) tooltip else (disabledTooltip ?: tooltip)) }) {
        val leading: @Composable () -> Unit = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = if (flashing || !enabled) LocalContentColor.current else tint,
            )
        }
        val text: @Composable () -> Unit = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // A raised key in the neutral colour, filling with its own colour for a moment when pressed.
        // With nothing live to describe it is shown but not pressable, rather than swallowing clicks.
        if (enabled) {
            RaisedFilterChip(
                selected = flashing,
                onClick = {
                    flashing = true
                    onClick()
                },
                selectedContainerColor = tint,
                selectedLabelColor = MaterialTheme.colorScheme.surface,
                leadingIcon = leading,
                label = text,
            )
        } else {
            InertChip(leadingIcon = leading, label = text)
        }
    }
}

private const val FLAG_FLASH_MS = 600L

/** One of the engine's settings as a raised toggle key: the accent key while [selected]. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EngineChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    tooltip: String,
    onClick: () -> Unit,
) {
    TooltipArea(tooltip = { EngineTooltip(tooltip) }) {
        RaisedFilterChip(
            selected = selected,
            onClick = onClick,
            leadingIcon = { Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(12.dp)) },
            label = {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                )
            },
        )
    }
}

@Composable
private fun EngineTooltip(text: String) {
    Surface(shadowElevation = 4.dp, color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(text = text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp))
    }
}
