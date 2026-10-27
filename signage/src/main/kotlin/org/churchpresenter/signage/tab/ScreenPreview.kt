package org.churchpresenter.signage.tab

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.churchpresenter.signage.core.PlayerMessage
import org.churchpresenter.signage.hub.ScreenLink
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.signage_no_preview
import org.churchpresenter.strings.generated.resources.signage_preview
import org.churchpresenter.strings.generated.resources.signage_preview_close
import org.churchpresenter.strings.generated.resources.signage_preview_large
import org.churchpresenter.theme.keyboardFocusRing
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.skia.Image as SkiaImage
import java.util.Base64

/**
 * A small picture of what a screen shows, as it last sent one; black with a line of text until the
 * first arrives. Clicking it opens it larger.
 */
@Composable
internal fun PreviewThumbnail(preview: PlayerMessage.Preview?, onOpen: () -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    val bitmap = rememberPreview(preview)
    val description = stringResource(Res.string.signage_preview)
    Box(
        modifier = Modifier.width(THUMBNAIL_WIDTH.dp).aspectRatio(TV_ASPECT)
            .keyboardFocusRing(shape)
            .clip(shape)
            // The TV's own black, in either theme: this is a picture of the screen.
            .background(Color.Black)
            .clickable(enabled = bitmap != null, onClickLabel = description, onClick = onOpen),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap,
                contentDescription = description,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                stringResource(Res.string.signage_no_preview),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = NO_PREVIEW_ALPHA),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

/**
 * [link]'s last picture while it is recent; null once none has come for [STALE_MS], so a screen that
 * stops sending (an older player, mpv restarting) does not keep showing an old picture as its own.
 */
@Composable
internal fun currentPreview(link: ScreenLink?): PlayerMessage.Preview? {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(TICK_MS)
            value = System.currentTimeMillis()
        }
    }
    val at = link?.previewAtMs ?: return null
    return link.preview?.takeIf { now - at <= STALE_MS }
}

/** [screenName]'s picture, large; it sharpens once the screen answers the request for a bigger one. */
@Composable
internal fun LargePreviewDialog(screenName: String, preview: PlayerMessage.Preview?, onDismiss: () -> Unit) {
    val bitmap = rememberPreview(preview)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.signage_preview_large, screenName)) },
        text = {
            Box(
                modifier = Modifier.widthIn(max = LARGE_WIDTH.dp).aspectRatio(TV_ASPECT).background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.signage_preview_close)) } },
    )
}

/** Decoded once per picture; a picture that will not decode shows as none. */
@Composable
private fun rememberPreview(preview: PlayerMessage.Preview?): ImageBitmap? =
    remember(preview?.takenAtMs, preview?.width) {
        preview?.let {
            runCatching { SkiaImage.makeFromEncoded(Base64.getDecoder().decode(it.jpegBase64)).toComposeImageBitmap() }
                .getOrNull()
        }
    }

private const val THUMBNAIL_WIDTH = 176
private const val STALE_MS = 20_000L
private const val TICK_MS = 5_000L
private const val LARGE_WIDTH = 720
private const val TV_ASPECT = 16f / 9f
private const val NO_PREVIEW_ALPHA = 0.7f
