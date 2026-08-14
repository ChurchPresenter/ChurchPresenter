package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.bible_translation_order_hint
import churchpresenter.composeapp.generated.resources.bible_translation_order_more
import churchpresenter.composeapp.generated.resources.bible_translation_order_panel_subtitle
import churchpresenter.composeapp.generated.resources.bible_translation_order_panel_title
import churchpresenter.composeapp.generated.resources.drag_to_reorder_translation
import churchpresenter.composeapp.generated.resources.ic_arrow_down
import churchpresenter.composeapp.generated.resources.ic_arrow_up
import churchpresenter.composeapp.generated.resources.ic_drag_dots
import churchpresenter.composeapp.generated.resources.move_translation_down
import churchpresenter.composeapp.generated.resources.move_translation_up
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first
import org.churchpresenter.app.churchpresenter.data.settings.BibleTranslationSettings
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private const val EXPANDED_ROTATION = 180f

private val TRANSLATION_ORDER_ROW_HEIGHT = 46.dp

private fun translationTitle(displayNames: Map<String, String>, translation: BibleTranslationSettings): String =
    (displayNames[translation.fileName] ?: translation.fileName.substringBeforeLast('.'))
        .substringBefore("  (")

@Composable
internal fun TranslationOrderSelector(
    label: String,
    translations: List<BibleTranslationSettings>,
    displayNames: Map<String, String>,
    onMove: (index: Int, offset: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val primary = translations.first()
    val primaryName = translationTitle(displayNames, primary)
    val extraCount = translations.size - 1

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                .border(
                    1.dp,
                    if (expanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    RoundedCornerShape(10.dp),
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { expanded = true }
                .padding(horizontal = 10.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(
                    text = label.uppercase(),
                    fontSize = 8.5.sp,
                    lineHeight = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    maxLines = 1,
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    text = primaryName,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        lineHeight = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (extraCount > 0) {
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(5.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.bible_translation_order_more, extraCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
            Icon(
                painter = painterResource(Res.drawable.ic_arrow_down),
                contentDescription = null,
                modifier = Modifier.size(12.dp).rotate(if (expanded) EXPANDED_ROTATION else 0f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(13.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            offset = DpOffset(0.dp, 8.dp),
        ) {
            TranslationOrderPanel(translations = translations, displayNames = displayNames, onMove = onMove)
        }
    }
}

@Composable
private fun TranslationOrderPanel(
    translations: List<BibleTranslationSettings>,
    displayNames: Map<String, String>,
    onMove: (index: Int, offset: Int) -> Unit,
) {
    Column(modifier = Modifier.width(320.dp)) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                text = stringResource(Res.string.bible_translation_order_panel_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(Res.string.bible_translation_order_panel_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        val density = LocalDensity.current
        val rowHeightPx = with(density) { TRANSLATION_ORDER_ROW_HEIGHT.toPx() }
        val translationsState = rememberUpdatedState(translations)
        var draggingFileName by remember { mutableStateOf<String?>(null) }
        var dragOffsetY by remember { mutableStateOf(0f) }

        Column(modifier = Modifier.padding(6.dp)) {
            translations.forEachIndexed { index, translation ->
                val isPrimary = index == 0
                val isDragged = translation.fileName == draggingFileName
                val name = translationTitle(displayNames, translation)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(TRANSLATION_ORDER_ROW_HEIGHT)
                        .zIndex(if (isDragged) 1f else 0f)
                        .graphicsLayer { translationY = if (isDragged) dragOffsetY else 0f }
                        .background(
                            if (isPrimary) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                                else Color.Transparent,
                            RoundedCornerShape(9.dp),
                        )
                        .border(
                            1.dp,
                            if (isPrimary) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else Color.Transparent,
                            RoundedCornerShape(9.dp),
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_drag_dots),
                        contentDescription = stringResource(Res.string.drag_to_reorder_translation),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier
                            .size(width = 4.dp, height = 16.dp)
                            .pointerHoverIcon(PointerIcon.Hand)
                            .pointerInput(translation.fileName) {
                                detectDragGestures(
                                    onDragStart = {
                                        draggingFileName = translation.fileName
                                        dragOffsetY = 0f
                                    },
                                    onDragEnd = {
                                        val current = translationsState.value
                                        val from = current.indexOfFirst { it.fileName == draggingFileName }
                                        if (from >= 0) {
                                            val steps = (dragOffsetY / rowHeightPx).roundToInt()
                                            val to = (from + steps).coerceIn(0, current.lastIndex)
                                            if (to != from) onMove(from, to - from)
                                        }
                                        draggingFileName = null
                                        dragOffsetY = 0f
                                    },
                                    onDragCancel = {
                                        draggingFileName = null
                                        dragOffsetY = 0f
                                    },
                                ) { change, dragAmount ->
                                    change.consume()
                                    dragOffsetY += dragAmount.y
                                }
                            },
                    )

                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .background(
                                if (isPrimary) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(7.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "${index + 1}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isPrimary) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 13.sp,
                                    fontWeight = if (isPrimary) FontWeight.SemiBold else FontWeight.Medium,
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                        Text(
                            text = translation.fileName,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        ReorderArrowButton(
                            icon = painterResource(Res.drawable.ic_arrow_up),
                            contentDescription = stringResource(Res.string.move_translation_up),
                            enabled = index > 0,
                            onClick = { onMove(index, -1) },
                        )
                        ReorderArrowButton(
                            icon = painterResource(Res.drawable.ic_arrow_down),
                            contentDescription = stringResource(Res.string.move_translation_down),
                            enabled = index < translations.lastIndex,
                            onClick = { onMove(index, 1) },
                        )
                    }
                }
                if (index != translations.lastIndex) Spacer(Modifier.height(4.dp))
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
            Text(
                text = stringResource(Res.string.bible_translation_order_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun ReorderArrowButton(
    icon: Painter,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(width = 22.dp, height = 16.dp)
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 1f else 0.4f),
                RoundedCornerShape(5.dp)
            )
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (enabled) 1f else 0.5f),
                RoundedCornerShape(5.dp),
            )
            .then(
                if (enabled) {
                    Modifier.clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onClick,
                    )
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(10.dp),
            tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
        )
    }
}
