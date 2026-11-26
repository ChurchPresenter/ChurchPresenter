package org.churchpresenter.sharedui.composables

import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.icons.generated.resources.ic_download
import org.churchpresenter.theme.semantic
import androidx.compose.foundation.background
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.components.RaisedButton
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.io.File

private val TILE_SIZE = 56.dp
private val TILE_RADIUS = 16.dp
private val TILE_ICON_SIZE = 26.dp
private val COMPACT_TILE_SIZE = 40.dp
private val COMPACT_TILE_RADIUS = 12.dp
private val COMPACT_TILE_ICON_SIZE = 20.dp
private val BUTTON_HEIGHT = 36.dp
private val BUTTON_RADIUS = 8.dp
private val BUTTON_ICON_SIZE = 16.dp
private val CHIP_HEIGHT = 22.dp
private val CHIP_RADIUS = 6.dp
private val CONTENT_MAX_WIDTH = 400.dp
private val BODY_MAX_WIDTH = 340.dp
private val DROP_INSET = 10.dp
private val DROP_RADIUS = 12.dp
private val DROP_STROKE = 2.dp
private const val DROP_BORDER_ALPHA = 0.7f
private const val DROP_FILL_ALPHA = 0.06f
private const val DROP_DASH = 6f
private const val DROP_GAP = 5f

/**
 * The card an empty tab shows its state on. It takes files dropped on it through [drop], and while
 * they are dragged over it shows [dropTitle] and [dropBody] in place of [content].
 */
@Composable
fun EmptyStatePanel(
    drop: FileDrop,
    dropTitle: String,
    dropBody: String,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .padding(start = 4.dp, end = 4.dp, bottom = 4.dp)
            .bibleListCard()
            .dragAndDropTarget(shouldStartDragAndDrop = drop::accepts, target = drop)
            .drawBehind { if (drop.dragOver) drawDropOutline(primary) },
        contentAlignment = Alignment.Center,
    ) {
        if (drop.dragOver) {
            EmptyStateContent(icon = IconRes.drawable.ic_download, neutral = false, title = dropTitle, body = dropBody)
        } else {
            content()
        }
    }
}

/**
 * Icon tile, title, body, then the action, the hint and the [footer] under it; centered. A
 * [neutral] tile is gray, for nothing here right now; otherwise blue, for something to set up.
 */
@Composable
fun EmptyStateContent(
    icon: DrawableResource,
    neutral: Boolean,
    title: String,
    body: String,
    hint: String? = null,
    /** A red tile, for something that went wrong; wins over [neutral]. */
    error: Boolean = false,
    /** A green tile, for something that just worked; wins over [neutral]. */
    success: Boolean = false,
    /** Smaller tile, type and gaps, for a narrow list such as search results. */
    compact: Boolean = false,
    footer: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.widthIn(max = CONTENT_MAX_WIDTH).padding(if (compact) 16.dp else 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(if (compact) COMPACT_TILE_SIZE else TILE_SIZE)
                .background(
                    when {
                        error -> colors.errorContainer
                        success -> MaterialTheme.semantic.successContainer
                        neutral -> colors.surfaceVariant
                        else -> colors.primaryContainer
                    },
                    AppShape(if (compact) COMPACT_TILE_RADIUS else TILE_RADIUS),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(if (compact) COMPACT_TILE_ICON_SIZE else TILE_ICON_SIZE),
                tint = when {
                    error -> colors.onErrorContainer
                    success -> MaterialTheme.semantic.onSuccessContainer
                    neutral -> colors.onSurfaceVariant
                    else -> colors.onPrimaryContainer
                },
            )
        }
        Spacer(Modifier.height(if (compact) 12.dp else 16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = if (compact) 14.sp else 16.sp,
                lineHeight = if (compact) 20.sp else 22.sp,
            ),
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(if (compact) 4.dp else 6.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = if (compact) 12.5.sp else 13.5.sp,
                lineHeight = if (compact) 18.sp else 20.sp,
            ),
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = BODY_MAX_WIDTH),
        )
        if (action != null) {
            Spacer(Modifier.height(if (compact) 14.dp else 20.dp))
            action()
        }
        if (hint != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = colors.outline,
                textAlign = TextAlign.Center,
            )
        }
        if (footer != null) {
            Spacer(Modifier.height(10.dp))
            footer()
        }
    }
}

/** The empty state's button: filled primary, with [icon] before its label. */
@Composable
fun EmptyStateButton(label: String, icon: DrawableResource, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    RaisedButton(
        onClick = onClick,
        modifier = Modifier.height(BUTTON_HEIGHT),
        shape = AppShape(BUTTON_RADIUS),
        colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(BUTTON_ICON_SIZE))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/** A second, lesser action beside [EmptyStateButton]: tonal, with [icon] before its label. */
@Composable
fun EmptyStateSecondaryButton(label: String, icon: DrawableResource, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    RaisedButton(
        onClick = onClick,
        modifier = Modifier.height(BUTTON_HEIGHT),
        shape = AppShape(BUTTON_RADIUS),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.surfaceVariant,
            contentColor = colors.onSurface,
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(BUTTON_ICON_SIZE))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/** Small monospaced chips naming file formats, such as ".pptx". */
@Composable
fun FormatChips(formats: List<String>) {
    val colors = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        formats.forEach { format ->
            Box(
                modifier = Modifier.height(CHIP_HEIGHT)
                    .background(colors.surfaceVariant, AppShape(CHIP_RADIUS))
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    format,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace,
                    ),
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

/** The dashed primary outline a drag over the panel shows, inset from its edge. */
private fun DrawScope.drawDropOutline(primary: Color) {
    val inset = DROP_INSET.toPx()
    val size = Size(size.width - inset * 2, size.height - inset * 2)
    if (size.width <= 0f || size.height <= 0f) return
    val radius = CornerRadius(DROP_RADIUS.toPx())
    drawRoundRect(primary.copy(alpha = DROP_FILL_ALPHA), Offset(inset, inset), size, radius)
    drawRoundRect(
        primary.copy(alpha = DROP_BORDER_ALPHA), Offset(inset, inset), size, radius,
        style = Stroke(
            width = DROP_STROKE.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(DROP_DASH.dp.toPx(), DROP_GAP.dp.toPx())),
        ),
    )
}

/** Files dragged over an empty panel; [onFiles] gets what was let go. */
@Stable
class FileDrop(private val onFiles: (List<File>) -> Unit) : DragAndDropTarget {
    /** True while files are being dragged over the panel. */
    var dragOver by mutableStateOf(false)

    fun accepts(event: DragAndDropEvent): Boolean = accepts(event.transferableOrNull())

    /** Only the flavor is checked here: macOS refuses the data itself until the drop. */
    fun accepts(data: Transferable?): Boolean = data?.isDataFlavorSupported(DataFlavor.javaFileListFlavor) == true

    override fun onEntered(event: DragAndDropEvent) { dragOver = true }
    override fun onExited(event: DragAndDropEvent) { dragOver = false }
    override fun onEnded(event: DragAndDropEvent) { dragOver = false }

    override fun onDrop(event: DragAndDropEvent): Boolean = drop(event.transferableOrNull())

    /** Hands the files [data] carries to [onFiles]; false when it carries none. */
    fun drop(data: Transferable?): Boolean {
        dragOver = false
        val files = readOrNull { data?.droppedFiles() } ?: return false
        onFiles(files)
        return true
    }
}

@Suppress("UNCHECKED_CAST")
private fun Transferable.droppedFiles(): List<File>? =
    takeIf { it.isDataFlavorSupported(DataFlavor.javaFileListFlavor) }
        ?.getTransferData(DataFlavor.javaFileListFlavor)
        ?.let { it as? List<File> }
        ?.takeIf { it.isNotEmpty() }

@OptIn(ExperimentalComposeUiApi::class)
private fun DragAndDropEvent.transferableOrNull(): Transferable? = readOrNull { awtTransferable }

/** A drag whose data cannot be read is one this panel does not take. */
@Suppress("SwallowedException", "TooGenericExceptionCaught")
private inline fun <T> readOrNull(read: () -> T?): T? = try {
    read()
} catch (_: Exception) {
    null
}
