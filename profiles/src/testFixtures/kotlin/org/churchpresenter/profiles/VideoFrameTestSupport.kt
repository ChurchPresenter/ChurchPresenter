package org.churchpresenter.profiles

import org.churchpresenter.media.utils.VideoFirstFrame
import java.awt.Color
import java.awt.GradientPaint
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * A stand-in clip at [clip] whose first frame is already in the preview-frame cache, so a tile draws
 * a still without ffmpeg. The frame is a [top]-to-[bottom] diagonal gradient. Returns the cached
 * frame, for the caller to delete.
 */
fun seedVideoFrame(
    clip: File,
    top: Color = Color(TOP_RGB),
    bottom: Color = Color(BOTTOM_RGB),
): File {
    clip.parentFile?.mkdirs()
    if (!clip.isFile) clip.writeBytes(ByteArray(STAND_IN_CLIP_BYTES))
    val frame = VideoFirstFrame.cacheFileFor(clip)
    frame.parentFile.mkdirs()
    val image = BufferedImage(FRAME_WIDTH, FRAME_HEIGHT, BufferedImage.TYPE_INT_RGB)
    val canvas = image.createGraphics()
    canvas.paint = GradientPaint(0f, 0f, top, FRAME_WIDTH.toFloat(), FRAME_HEIGHT.toFloat(), bottom)
    canvas.fillRect(0, 0, FRAME_WIDTH, FRAME_HEIGHT)
    canvas.dispose()
    ImageIO.write(image, "jpg", frame)
    return frame
}

private const val TOP_RGB = 0x2E6B4F
private const val BOTTOM_RGB = 0xE8C46A
private const val FRAME_WIDTH = 320
private const val FRAME_HEIGHT = 180
private const val STAND_IN_CLIP_BYTES = 8
