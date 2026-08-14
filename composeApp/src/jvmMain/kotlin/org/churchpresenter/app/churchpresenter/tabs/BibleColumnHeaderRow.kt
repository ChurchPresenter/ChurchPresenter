package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.data.settings.BibleTranslationSettings
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.add_to_schedule
import churchpresenter.composeapp.generated.resources.bible_translation_order
import churchpresenter.composeapp.generated.resources.bible_verse_selection_hint
import churchpresenter.composeapp.generated.resources.go_live
import churchpresenter.composeapp.generated.resources.hold_live
import churchpresenter.composeapp.generated.resources.swap_bibles
import churchpresenter.composeapp.generated.resources.bible_cross_references
import churchpresenter.composeapp.generated.resources.bible_cross_references_title
import churchpresenter.composeapp.generated.resources.book
import churchpresenter.composeapp.generated.resources.chapter
import churchpresenter.composeapp.generated.resources.hold_live_modifier_hint
import churchpresenter.composeapp.generated.resources.ic_link
import churchpresenter.composeapp.generated.resources.ic_pause
import churchpresenter.composeapp.generated.resources.ic_swap
import churchpresenter.composeapp.generated.resources.stt_connect
import churchpresenter.composeapp.generated.resources.stt_disconnect
import churchpresenter.composeapp.generated.resources.swap_bibles_hint
import churchpresenter.composeapp.generated.resources.verse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.churchpresenter.app.churchpresenter.composables.ActionIconButton
import org.churchpresenter.app.churchpresenter.composables.AddToScheduleButton
import org.churchpresenter.app.churchpresenter.composables.GoLiveButton
import org.churchpresenter.app.churchpresenter.data.bibleDisplayNames
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BibleColumnHeaderRow(
    bookWidth: Dp,
    chapterWidth: Dp,
    crossRefsVisible: Boolean,
    crossRefsDocked: Boolean,
    holdAvailable: Boolean,
    holdLive: Boolean,
    sttToggleVisible: Boolean,
    sttConnected: Boolean,
    translations: List<BibleTranslationSettings>,
    storageDirectory: String,
    translationSelectionKey: List<String>,
    onCrossReferencesToggle: () -> Unit,
    onHoldLiveToggle: () -> Unit,
    onSttToggle: () -> Unit,
    onSwapTranslations: () -> Unit,
    onMoveTranslation: (index: Int, offset: Int) -> Unit,
    onAddToSchedule: () -> Unit,
    onGoLive: () -> Unit,
) {
    val holdLiveStr = stringResource(Res.string.hold_live)
    val verseSelectionHint = stringResource(Res.string.bible_verse_selection_hint)
    val swapBiblesStr = stringResource(Res.string.swap_bibles)
    val translationOrderStr = stringResource(Res.string.bible_translation_order)
    val goLiveStr = stringResource(Res.string.go_live)
    val addScheduleStr = stringResource(Res.string.add_to_schedule)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 31.dp)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(start = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.width(bookWidth).padding(start = 12.dp)) {
                    Text(
                        text = stringResource(Res.string.book).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
                VerticalDivider(modifier = Modifier.height(16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Box(modifier = Modifier.width(chapterWidth).padding(start = 12.dp)) {
                    Text(
                        text = stringResource(Res.string.chapter).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                VerticalDivider(modifier = Modifier.height(16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier.weight(1f).padding(start = 12.dp, end = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.verse).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                    Spacer(Modifier.weight(1f))

                    if (crossRefsVisible) {
                        val crossRefsLabel = stringResource(Res.string.bible_cross_references_title)
                        TooltipArea(
                            tooltip = {
                                Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall) {
                                    Text(
                                        stringResource(Res.string.bible_cross_references),
                                        color = MaterialTheme.colorScheme.inverseOnSurface,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            },
                            tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp)),
                        ) {
                            Row(
                                modifier = Modifier
                                    .height(27.dp)
                                    .background(
                                        if (crossRefsDocked) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(6.dp),
                                    )
                                    .border(
                                        1.dp,
                                        if (crossRefsDocked) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outlineVariant,
                                        RoundedCornerShape(6.dp),
                                    )
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() },
                                    ) {
                                        onCrossReferencesToggle()
                                    }
                                    .padding(horizontal = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_link),
                                    contentDescription = stringResource(Res.string.bible_cross_references),
                                    modifier = Modifier.size(12.dp),
                                    tint = if (crossRefsDocked) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                )
                                Text(
                                    crossRefsLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                    color = if (crossRefsDocked) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                )
                            }
                        }
                    }

                    val holdPillActive = holdAvailable
                    val holdLiveState = holdLive
                    TooltipArea(
                        tooltip = {
                            Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall) {
                                Text(
                                    if (holdPillActive) holdLiveStr else verseSelectionHint,
                                    color = MaterialTheme.colorScheme.inverseOnSurface,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        },
                        tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .height(27.dp)
                                .background(
                                    when {
                                        holdLiveState -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    RoundedCornerShape(6.dp)
                                )
                                .border(
                                    1.dp,
                                    when {
                                        holdLiveState -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.outlineVariant
                                    },
                                    RoundedCornerShape(6.dp)
                                )
                                .then(
                                    if (holdPillActive)
                                        Modifier.clickable(
                                            indication = null,
                                            interactionSource = remember { MutableInteractionSource() }
                                        ) {
                                            onHoldLiveToggle()
                                        }
                                    else Modifier
                                )
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_pause),
                                    contentDescription = null,
                                    modifier = Modifier.size(10.dp),
                                    tint = when {
                                        holdLiveState -> MaterialTheme.colorScheme.onError
                                        holdPillActive -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                    }
                                )
                                Text(
                                    stringResource(Res.string.hold_live_modifier_hint),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                    color = when {
                                        holdLiveState -> MaterialTheme.colorScheme.onError
                                        holdPillActive -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                                    }
                                )
                            }
                        }
                    }

                    if (sttToggleVisible) {
                        val sttActionStr = if (sttConnected) stringResource(Res.string.stt_disconnect) else stringResource(Res.string.stt_connect)
                        ActionIconButton(
                            onClick = {
                                onSttToggle()
                            },
                            tooltipText = sttActionStr,
                            icon = Icons.Filled.Mic,
                            containerColor = if (sttConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (sttConnected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (translations.size == 2) {
                        ActionIconButton(
                            onClick = {
                                onSwapTranslations()
                            },
                            tooltipText = swapBiblesStr,
                            painter = painterResource(Res.drawable.ic_swap),
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary,
                            tooltipContent = {
                                val pair = translations
                                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                    Text(stringResource(Res.string.swap_bibles_hint), color = MaterialTheme.colorScheme.inverseOnSurface, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    pair.forEachIndexed { position, item ->
                                        Text(
                                            "${position + 1}. ${item.fileName.substringBeforeLast('.').ifEmpty { "-" }}",
                                            color = MaterialTheme.colorScheme.inverseOnSurface,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            }
                        )
                    } else if (translations.size > 2) {

                        val translationDisplayNames by produceState(
                            initialValue = emptyMap<String, String>(),
                            storageDirectory,
                            translationSelectionKey,
                        ) {
                            value = withContext(Dispatchers.IO) {
                                bibleDisplayNames(storageDirectory, translations.map { it.fileName })
                            }
                        }
                        TranslationOrderSelector(
                            label = translationOrderStr,
                            translations = translations,
                            displayNames = translationDisplayNames,
                            onMove = { index, offset ->
                                onMoveTranslation(index, offset)
                            },
                            modifier = Modifier
                                .widthIn(min = 127.dp, max = 174.dp),
                        )
                    }

                    AddToScheduleButton(
                        onClick = {
                            onAddToSchedule()
                        },
                        tooltipText = addScheduleStr
                    )

                    GoLiveButton(
                        onClick = onGoLive,
                        tooltipText = goLiveStr
                    )
                }
            }
}
