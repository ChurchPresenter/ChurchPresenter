package org.churchpresenter.slides.tabs

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
import org.churchpresenter.sharedui.composables.bibleListCard
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
private val BUTTON_HEIGHT = 36.dp
private val BUTTON_RADIUS = 8.dp
private val BUTTON_ICON_SIZE = 16.dp
private val CHIP_HEIGHT = 22.dp
private val CHIP_RADIUS = 6.dp
private val CONTENT_MAX_WIDTH = 340.dp
private val DROP_INSET = 10.dp
private val DROP_RADIUS = 12.dp
private val DROP_STROKE = 2.dp
private const val DROP_BORDER_ALPHA = 0.7f
private const val DROP_FILL_ALPHA = 0.06f
private const val DROP_DASH = 6f
private const val DROP_GAP = 5f

/** The card an empty tab shows its state on; it takes files dropped on it through [drop]. */
@Composable
internal fun EmptyStatePanel(drop: FileDrop, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .padding(start = 4.dp, end = 4.dp, bottom = 4.dp)
            .bibleListCard()
            .dragAndDropTarget(shouldStartDragAndDrop = drop::accepts, target = drop)
            .drawBehind { if (drop.dragOver) drawDropOutline(primary) },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/**
 * Icon tile, title, body, then the action, the hint and the [footer] under it; centered. A
 * [neutral] tile is gray, for nothing here right now; otherwise blue, for something to set up.
 */
@Composable
internal fun EmptyStateContent(
    icon: DrawableResource,
    neutral: Boolean,
    title: String,
    body: String,
    hint: String? = null,
    footer: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.widthIn(max = CONTENT_MAX_WIDTH).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(TILE_SIZE)
                .background(if (neutral) colors.secondaryContainer else colors.primaryContainer, AppShape(TILE_RADIUS)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(TILE_ICON_SIZE),
                tint = if (neutral) colors.onSurfaceVariant else colors.onPrimaryContainer,
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, lineHeight = 22.sp),
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp, lineHeight = 20.sp),
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(20.dp))
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
internal fun EmptyStateButton(label: String, icon: DrawableResource, onClick: () -> Unit) {
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
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Small monospaced chips naming file formats, such as ".pptx". */
@Composable
internal fun FormatChips(formats: List<String>) {
    val colors = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        formats.forEach { format ->
            Box(
                modifier = Modifier.height(CHIP_HEIGHT)
                    .background(colors.secondaryContainer, AppShape(CHIP_RADIUS))
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
internal class FileDrop(private val onFiles: (List<File>) -> Unit) : DragAndDropTarget {
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
