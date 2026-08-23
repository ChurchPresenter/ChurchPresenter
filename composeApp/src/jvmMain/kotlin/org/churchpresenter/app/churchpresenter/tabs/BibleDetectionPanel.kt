package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.resources.generated.resources.Res
import org.churchpresenter.resources.generated.resources.bible_next_verse_speed_balanced
import org.churchpresenter.resources.generated.resources.bible_next_verse_speed_fast
import org.churchpresenter.resources.generated.resources.bible_next_verse_speed_label
import org.churchpresenter.resources.generated.resources.bible_next_verse_speed_tooltip_balanced
import org.churchpresenter.resources.generated.resources.bible_next_verse_speed_tooltip_fast
import org.churchpresenter.resources.generated.resources.bible_stt_auto_follow
import org.churchpresenter.resources.generated.resources.bible_stt_auto_follow_hint
import org.churchpresenter.resources.generated.resources.bible_stt_clear
import org.churchpresenter.resources.generated.resources.bible_stt_detected_version_tooltip
import org.churchpresenter.resources.generated.resources.bible_stt_engine_connecting
import org.churchpresenter.resources.generated.resources.bible_stt_engine_stt_down
import org.churchpresenter.resources.generated.resources.bible_stt_engine_unavailable
import org.churchpresenter.resources.generated.resources.bible_stt_flag_missed
import org.churchpresenter.resources.generated.resources.bible_stt_flag_missed_hint
import org.churchpresenter.resources.generated.resources.bible_stt_flag_needs_live
import org.churchpresenter.resources.generated.resources.bible_stt_flag_premature
import org.churchpresenter.resources.generated.resources.bible_stt_flag_premature_hint
import org.churchpresenter.resources.generated.resources.bible_stt_flag_wrong
import org.churchpresenter.resources.generated.resources.bible_stt_flag_wrong_hint
import org.churchpresenter.resources.generated.resources.bible_stt_level_aggressive
import org.churchpresenter.resources.generated.resources.bible_stt_level_balanced
import org.churchpresenter.resources.generated.resources.bible_stt_level_conservative
import org.churchpresenter.resources.generated.resources.bible_stt_level_off
import org.churchpresenter.resources.generated.resources.bible_stt_listening
import org.churchpresenter.resources.generated.resources.bible_stt_match_label
import org.churchpresenter.resources.generated.resources.bible_stt_no_bible
import org.churchpresenter.resources.generated.resources.bible_stt_src_chapter_history
import org.churchpresenter.resources.generated.resources.bible_stt_src_chapter_scan
import org.churchpresenter.resources.generated.resources.bible_stt_src_continuation
import org.churchpresenter.resources.generated.resources.bible_stt_src_explicit
import org.churchpresenter.resources.generated.resources.bible_stt_src_reverse
import org.churchpresenter.resources.generated.resources.bible_stt_text_match_hint
import org.churchpresenter.resources.generated.resources.bible_stt_track_transcription
import org.churchpresenter.resources.generated.resources.bible_stt_track_translation
import org.churchpresenter.resources.generated.resources.bible_stt_waiting_for_stt
import org.churchpresenter.resources.generated.resources.ic_close
import org.churchpresenter.resources.generated.resources.stt_status_connecting
import org.churchpresenter.resources.generated.resources.stt_status_not_connected
import org.churchpresenter.resources.generated.resources.stt_status_reconnecting
import org.churchpresenter.resources.generated.resources.stt_status_unreachable
import kotlinx.coroutines.delay
import org.churchpresenter.ui.initialPassCombinedClickable
import org.churchpresenter.app.churchpresenter.viewmodel.BibleSttStatus
import org.churchpresenter.app.churchpresenter.viewmodel.DetectedReference
import org.churchpresenter.app.churchpresenter.viewmodel.ContinuationSpeed
import org.churchpresenter.app.churchpresenter.viewmodel.DetectionSource
import org.churchpresenter.app.churchpresenter.viewmodel.DetectionTrack
import org.churchpresenter.app.churchpresenter.viewmodel.TextMatchLevel
import org.churchpresenter.theme.semantic
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private const val SELECTION_BAR_WIDTH = 4f

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
) {
    Column(modifier = Modifier.fillMaxWidth()) {
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

                TooltipArea(tooltip = {
                    Surface(shadowElevation = 4.dp, color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(
                            text = stringResource(Res.string.bible_stt_auto_follow_hint),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }) {
                    Box(
                        modifier = Modifier
                            .height(27.dp)
                            .background(
                                if (autoFollowEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(6.dp)
                            )
                            .border(
                                1.dp,
                                if (autoFollowEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(6.dp)
                            )
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) {
                                onAutoFollowChange(!autoFollowEnabled)
                            }
                            .padding(horizontal = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckBoxOutlineBlank,
                                contentDescription = null,
                                modifier = Modifier.size(11.dp),
                                tint = if (autoFollowEnabled) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                            Text(
                                text = stringResource(Res.string.bible_stt_auto_follow),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = if (autoFollowEnabled) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                TooltipArea(tooltip = {
                    Surface(shadowElevation = 4.dp, color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(
                            text = stringResource(Res.string.bible_stt_text_match_hint),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }) {
                Box(
                    modifier = Modifier
                        .height(27.dp)
                        .background(
                            if (textMatchLevel != TextMatchLevel.OFF) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(6.dp)
                        )
                        .border(
                            1.dp,
                            if (textMatchLevel != TextMatchLevel.OFF) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(6.dp)
                        )
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            val all = TextMatchLevel.values()
                            onTextMatchLevelChange(all[(textMatchLevel.ordinal + 1) % all.size])
                        }
                        .padding(horizontal = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.FormatAlignLeft,
                            contentDescription = null,
                            modifier = Modifier.size(11.dp),
                            tint = if (textMatchLevel != TextMatchLevel.OFF) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Text(
                            text = "${stringResource(Res.string.bible_stt_match_label)}: $levelName",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = if (textMatchLevel != TextMatchLevel.OFF) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
                }

                val verseSpeedName = when (continuationSpeed) {
                    ContinuationSpeed.BALANCED -> stringResource(Res.string.bible_next_verse_speed_balanced)
                    ContinuationSpeed.FAST -> stringResource(Res.string.bible_next_verse_speed_fast)
                }
                val verseSpeedHint = when (continuationSpeed) {
                    ContinuationSpeed.BALANCED -> stringResource(Res.string.bible_next_verse_speed_tooltip_balanced)
                    ContinuationSpeed.FAST -> stringResource(Res.string.bible_next_verse_speed_tooltip_fast)
                }
                TooltipArea(tooltip = {
                    Surface(shadowElevation = 4.dp, color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(
                            text = verseSpeedHint,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }) {
                Box(
                    modifier = Modifier
                        .height(27.dp)
                        .background(
                            if (continuationSpeed != ContinuationSpeed.BALANCED) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(6.dp)
                        )
                        .border(
                            1.dp,
                            if (continuationSpeed != ContinuationSpeed.BALANCED) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(6.dp)
                        )
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            val all = ContinuationSpeed.values()
                            onContinuationSpeedChange(all[(continuationSpeed.ordinal + 1) % all.size])
                        }
                        .padding(horizontal = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Speed,
                            contentDescription = null,
                            modifier = Modifier.size(11.dp),
                            tint = if (continuationSpeed != ContinuationSpeed.BALANCED) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Text(
                            text = "${stringResource(Res.string.bible_next_verse_speed_label)}: $verseSpeedName",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = if (continuationSpeed != ContinuationSpeed.BALANCED) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
                }
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
                    IconButton(
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

            val detRowHeight = 24.dp
            val detMaxVisibleRows = 4

            val markerColor = MaterialTheme.semantic.marker
            if (detections.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val detScroll = rememberScrollState()
            Box(modifier = Modifier.fillMaxWidth().heightIn(max = detRowHeight * detMaxVisibleRows)) {
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
                if (detections.size > detMaxVisibleRows) {
                    VerticalScrollbar(
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                        adapter = rememberScrollbarAdapter(detScroll)
                    )
                }
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

    val muted = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    val contentColor = when {
        !enabled -> muted
        flashing -> MaterialTheme.colorScheme.surface
        else -> tint
    }
    val background = when {
        flashing -> tint
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val borderColor = if (enabled) tint.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant

    TooltipArea(tooltip = {
        Surface(shadowElevation = 4.dp, color = MaterialTheme.colorScheme.surfaceVariant) {
            Text(
                text = if (enabled) tooltip else (disabledTooltip ?: tooltip),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(8.dp)
            )
        }
    }) {
        Box(
            modifier = Modifier
                .height(27.dp)
                .background(background, RoundedCornerShape(6.dp))
                .border(1.dp, borderColor, RoundedCornerShape(6.dp))
                .then(
                    if (!enabled) Modifier
                    else Modifier.clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) {
                        flashing = true
                        onClick()
                    }
                )
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(11.dp),
                    tint = contentColor
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Medium),
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private const val FLAG_FLASH_MS = 600L
