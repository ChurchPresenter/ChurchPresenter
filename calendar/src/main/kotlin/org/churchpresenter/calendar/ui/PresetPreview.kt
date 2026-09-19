package org.churchpresenter.calendar.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.churchpresenter.calendar.generated.resources.Res
import org.churchpresenter.calendar.generated.resources.calendar_preview_missing
import org.churchpresenter.calendar.generated.resources.calendar_preview_none
import org.churchpresenter.calendar.generated.resources.calendar_preview_pictures
import org.churchpresenter.calendar.generated.resources.calendar_preview_pictures_cap
import org.churchpresenter.calendar.generated.resources.calendar_preview_play_again
import org.churchpresenter.calendar.generated.resources.calendar_preview_scene_unavailable
import org.churchpresenter.calendar.generated.resources.calendar_preview_stream
import org.churchpresenter.calendar.generated.resources.calendar_preview_slides
import org.churchpresenter.calendar.generated.resources.calendar_preview_slides_cap
import org.churchpresenter.calendar.generated.resources.calendar_preview_timer_clock_display
import org.churchpresenter.calendar.generated.resources.calendar_preview_timer_clock
import org.churchpresenter.calendar.generated.resources.calendar_preview_timer_duration
import org.churchpresenter.calendar.generated.resources.calendar_preview_timer_then
import org.churchpresenter.calendar.generated.resources.calendar_preview_timer_up
import org.churchpresenter.calendar.generated.resources.calendar_preview_video_done
import org.churchpresenter.calendar.generated.resources.calendar_preview_video_unavailable
import org.churchpresenter.calendar.model.clockText
import org.churchpresenter.calendar.model.formatDuration
import org.churchpresenter.calendar.model.secondsUntil
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.schedule.TimerModes
import org.jetbrains.compose.resources.stringResource
import java.io.File
import java.net.URI
import java.time.LocalTime

/** How many pictures or slides a preview shows -- the first ten, and it says so. */
const val PREVIEW_CAP: Int = 10

/** How long a video plays in the preview before it stops on its own. */
const val VIDEO_PREVIEW_MILLIS: Long = 5_000L

internal val THUMB_WIDTH = 96.dp
internal val THUMB_HEIGHT = 54.dp
private val VIDEO_WIDTH = 240.dp
private val VIDEO_HEIGHT = 135.dp
private val TIMER_READOUT_SIZE = 34.sp
private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3600
private const val MAX_HOUR = 23
private const val MAX_MINUTE = 59
private val PICTURE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")

/**
 * The accordion body under a preset row: what the preset puts on screen.
 *
 * A picture folder shows its first [PREVIEW_CAP] pictures and says how many there are; a deck its
 * first slides the same way; a video plays for [VIDEO_PREVIEW_MILLIS] and stops; a scene is drawn
 * by the app; a timer shows the readout it starts on. Anything else -- an announcement -- is
 * described in a line, there being nothing to draw.
 */
@Composable
fun PresetPreview(item: ScheduleItem, sources: PreviewSources, modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.fillMaxWidth().padding(start = 34.dp, end = 10.dp, top = 4.dp, bottom = 8.dp),
    ) {
        when (item) {
            is ScheduleItem.PictureItem -> PicturePreview(item)
            is ScheduleItem.PresentationItem -> SlidePreview(item, sources)
            is ScheduleItem.MediaItem -> VideoPreview(item, sources)
            is ScheduleItem.SceneItem -> ScenePreview(item, sources)
            is ScheduleItem.AnnouncementItem ->
                if (item.isTimer) {
                    TimerPreview(item)
                } else {
                    Caption(item.displayText.ifBlank { stringResource(Res.string.calendar_preview_none) })
                }
            else -> Caption(item.displayText.ifBlank { stringResource(Res.string.calendar_preview_none) })
        }
    }
}

@Composable
private fun PicturePreview(item: ScheduleItem.PictureItem) {
    val folder = remember(item.folderPath) { File(item.folderPath) }
    var thumbs by remember(item.folderPath) { mutableStateOf<List<ImageBitmap>?>(null) }
    var total by remember(item.folderPath) { mutableStateOf(0) }
    LaunchedEffect(item.folderPath) {
        val loaded = withContext(Dispatchers.IO) {
            val files = folder.listFiles()
                ?.filter { it.isFile && it.extension.lowercase() in PICTURE_EXTENSIONS }
                ?.sortedBy { it.name.lowercase() }
                .orEmpty()
            files.size to files.take(PREVIEW_CAP).mapNotNull { thumbnailOf(it) }
        }
        total = loaded.first
        thumbs = loaded.second
    }
    if (!folder.isDirectory) {
        Caption(stringResource(Res.string.calendar_preview_missing, item.folderPath))
        return
    }
    val shown = thumbs
    if (shown == null) {
        SkeletonStrip()
        return
    }
    ThumbnailStrip(shown)
    Caption(
        if (total > PREVIEW_CAP) {
            stringResource(Res.string.calendar_preview_pictures_cap, shown.size, total)
        } else {
            stringResource(Res.string.calendar_preview_pictures, total)
        }
    )
}

@Composable
private fun SlidePreview(item: ScheduleItem.PresentationItem, sources: PreviewSources) {
    var thumbs by remember(item.filePath) { mutableStateOf<List<ImageBitmap>?>(null) }
    LaunchedEffect(item.filePath) {
        thumbs = runCatching { sources.slideThumbnails(item.filePath, PREVIEW_CAP) }.getOrDefault(emptyList())
    }
    if (!File(item.filePath).isFile) {
        Caption(stringResource(Res.string.calendar_preview_missing, item.filePath))
        return
    }
    val shown = thumbs
    if (shown == null) {
        SkeletonStrip(count = item.slideCount.coerceIn(1, PREVIEW_CAP))
        return
    }
    ThumbnailStrip(shown)
    Caption(
        if (item.slideCount > PREVIEW_CAP) {
            stringResource(Res.string.calendar_preview_slides_cap, shown.size, item.slideCount)
        } else {
            stringResource(Res.string.calendar_preview_slides, item.slideCount)
        }
    )
}

/** The scene drawn small by the app, in the same 16:9 box a video gets. */
@Composable
private fun ScenePreview(item: ScheduleItem.SceneItem, sources: PreviewSources) {
    val draw = sources.scene
    if (draw == null) {
        Caption(stringResource(Res.string.calendar_preview_scene_unavailable))
        return
    }
    PreviewFrame { draw(item.sceneId, Modifier.fillMaxSize()) }
    Caption(item.sceneName)
}

/**
 * The timer as it reaches the screen: its opening readout, in its own color on black.
 *
 * Static, and [now] is a parameter rather than a call inside the composition -- a preview that
 * ticks is a live clock in a list, and a clock read from inside a composable is the leak that
 * makes a picture of it different every time it is taken.
 */
@Composable
private fun TimerPreview(item: ScheduleItem.AnnouncementItem, now: LocalTime = LocalTime.now()) {
    val use24Hour = LocalUse24HourClock.current
    val target = LocalTime.of(
        item.targetHour.coerceIn(0, MAX_HOUR),
        item.targetMinute.coerceIn(0, MAX_MINUTE),
        item.targetSecond.coerceIn(0, MAX_MINUTE),
    )
    val readout = when (item.timerMode) {
        TimerModes.COUNT_UP -> formatDuration(0)
        TimerModes.CLOCK -> formatDuration(secondsUntil(now, target))
        TimerModes.CLOCK_DISPLAY -> clockText(now, use24Hour)
        else -> formatDuration(
            item.timerHours * SECONDS_PER_HOUR + item.timerMinutes * SECONDS_PER_MINUTE + item.timerSeconds
        )
    }
    PreviewFrame(background = Color.Black) {
        Text(
            text = readout,
            style = MaterialTheme.typography.displaySmall.copy(fontSize = TIMER_READOUT_SIZE),
            fontWeight = FontWeight.Bold,
            color = parseHex(item.timerTextColor),
            maxLines = 1,
        )
    }
    Caption(
        when (item.timerMode) {
            TimerModes.COUNT_UP -> stringResource(Res.string.calendar_preview_timer_up)
            TimerModes.CLOCK -> stringResource(Res.string.calendar_preview_timer_clock, clockText(target, use24Hour))
            TimerModes.CLOCK_DISPLAY -> stringResource(Res.string.calendar_preview_timer_clock_display)
            else -> stringResource(Res.string.calendar_preview_timer_duration, readout)
        }
    )
    if (item.timerExpiredText.isNotBlank()) {
        Caption(stringResource(Res.string.calendar_preview_timer_then, item.timerExpiredText))
    }
}

/** A path as a media row stores it -- a plain path, or a `file:` URI -- as a file, or null for a stream. */
private fun localMediaFile(mediaUrl: String): File? = when {
    mediaUrl.startsWith("file:") -> runCatching { File(URI(mediaUrl)) }.getOrNull()
    mediaUrl.contains("://") -> null
    else -> File(mediaUrl)
}

@Composable
private fun VideoPreview(item: ScheduleItem.MediaItem, sources: PreviewSources) {
    val player = sources.video
    val file = remember(item.mediaUrl) { localMediaFile(item.mediaUrl) }
    if (player == null) {
        Caption(stringResource(Res.string.calendar_preview_video_unavailable))
        return
    }
    if (file == null) {
        // A stream has nothing to preview without playing it live; say what it is instead.
        Caption(stringResource(Res.string.calendar_preview_stream, item.mediaUrl))
        return
    }
    if (!file.isFile) {
        Caption(stringResource(Res.string.calendar_preview_missing, item.mediaUrl))
        return
    }
    // Plays for a few seconds and stops: enough to recognise the clip, not enough to sit through.
    var playing by remember(item.mediaUrl) { mutableStateOf(true) }
    var run by remember(item.mediaUrl) { mutableStateOf(0) }
    LaunchedEffect(item.mediaUrl, run) {
        playing = true
        delay(VIDEO_PREVIEW_MILLIS)
        playing = false
    }
    PreviewFrame {
        if (playing) {
            // The player draws nothing until its first frame; the skeleton shows through until then.
            SkeletonBox(Modifier.fillMaxSize())
            player(file.absolutePath, Modifier.fillMaxSize())
        } else {
            QuietButton(
                label = stringResource(Res.string.calendar_preview_play_again),
                onClick = { run++ },
                height = SheetMetrics.smallButton,
                accent = true,
            )
        }
    }
    if (!playing) Caption(stringResource(Res.string.calendar_preview_video_done))
}

/** The 16:9 box a video, a scene or a timer is drawn in. */
@Composable
private fun PreviewFrame(background: Color? = null, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(VIDEO_WIDTH, VIDEO_HEIGHT)
            .clip(RoundedCornerShape(6.dp))
            .background(background ?: scheme.surfaceVariant)
            .border(1.dp, scheme.outlineVariant, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThumbnailStrip(thumbs: List<ImageBitmap>) {
    val scheme = MaterialTheme.colorScheme
    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        thumbs.forEach { bitmap ->
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(THUMB_WIDTH)
                    .height(THUMB_HEIGHT)
                    .clip(RoundedCornerShape(5.dp))
                    .border(1.dp, scheme.outlineVariant, RoundedCornerShape(5.dp)),
            )
        }
    }
}

@Composable
private fun Caption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
    )
}
