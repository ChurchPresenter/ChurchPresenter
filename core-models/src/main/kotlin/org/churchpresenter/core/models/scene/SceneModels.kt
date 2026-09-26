package org.churchpresenter.core.models.scene

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.churchpresenter.core.models.text.TextBackdrop
import org.churchpresenter.core.models.text.TextOutline
import java.util.UUID

/**
 * Where a layer sits and how it is composited — the properties every kind of [SceneSource] has,
 * applied once by the canvas rather than re-implemented per source type.
 *
 * [x], [y], [width] and [height] are fractions of the canvas.
 */
@Serializable
data class SourceTransform(
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 1f,
    val height: Float = 1f,
    val rotation: Float = 0f,
    val opacity: Float = 1f
)

@Serializable
sealed class SceneSource {
    abstract val id: String
    abstract val name: String
    abstract val transform: SourceTransform
    abstract val visible: Boolean
    abstract val locked: Boolean

    /**
     * This source with every setting kept but a new [id] — how a duplicated scene stops sharing its
     * layers with the original.
     */
    abstract fun withId(id: String): SceneSource

    /** This source with every setting kept but a new [transform] — where it sits in another layout. */
    abstract fun withTransform(transform: SourceTransform): SceneSource

    @Serializable
    @SerialName("org.churchpresenter.app.churchpresenter.models.SceneSource.ImageSource")
    data class ImageSource(
        override val id: String,
        override val name: String,
        override val transform: SourceTransform = SourceTransform(),
        override val visible: Boolean = true,
        override val locked: Boolean = false,
        val filePath: String,
        val contentScale: String = "FIT"
    ) : SceneSource() {
        override fun withId(id: String): SceneSource = copy(id = id)
        override fun withTransform(transform: SourceTransform): SceneSource = copy(transform = transform)
    }

    @Serializable
    @SerialName("org.churchpresenter.app.churchpresenter.models.SceneSource.TextSource")
    data class TextSource(
        override val id: String,
        override val name: String,
        override val transform: SourceTransform = SourceTransform(),
        override val visible: Boolean = true,
        override val locked: Boolean = false,
        val text: String = "Text",
        val fontFamily: String = "Arial",
        val fontSize: Int = 48,
        val fontColor: String = "#FFFFFF",
        val backgroundColor: String = "#00000000",
        val bold: Boolean = false,
        val italic: Boolean = false,
        val underline: Boolean = false,
        val strikethrough: Boolean = false,
        /** The band behind each line and the box around the block. */
        val backdrop: TextBackdrop = TextBackdrop(),
        /** The stroke drawn around the glyphs, under the fill. */
        val outline: TextOutline = TextOutline(),
        val horizontalAlignment: String = "center",
        val verticalAlignment: String = "center",
        val lineSpacing: Int = 100,
        /**
         * Space added between letters, as a percentage of the font size: 0 is the font's own
         * spacing, positive tracks it out, negative tightens it.
         */
        val letterSpacing: Float = 0f,
        /**
         * How far the line is bent, as a percentage: 0 is straight, 100 arches it over a half
         * circle, -100 cups it under one. A bent line is drawn a glyph at a time, so it is always
         * one line — any newlines in [text] are drawn as spaces.
         */
        val curve: Float = 0f
    ) : SceneSource() {
        override fun withId(id: String): SceneSource = copy(id = id)
        override fun withTransform(transform: SourceTransform): SceneSource = copy(transform = transform)
    }

    @Serializable
    @SerialName("org.churchpresenter.app.churchpresenter.models.SceneSource.ColorSource")
    data class ColorSource(
        override val id: String,
        override val name: String,
        override val transform: SourceTransform = SourceTransform(),
        override val visible: Boolean = true,
        override val locked: Boolean = false,
        val color: String = "#000000",
        val sourceOpacity: Float = 1f,
        val isGradient: Boolean = false,
        val gradientColor2: String = "#FFFFFF",
        val gradientColor2Opacity: Float = 1f,
        val gradientAngle: Float = 0f,
        val gradientPosition: Float = 0.5f
    ) : SceneSource() {
        override fun withId(id: String): SceneSource = copy(id = id)
        override fun withTransform(transform: SourceTransform): SceneSource = copy(transform = transform)
    }

    @Serializable
    @SerialName("org.churchpresenter.app.churchpresenter.models.SceneSource.VideoSource")
    data class VideoSource(
        override val id: String,
        override val name: String,
        override val transform: SourceTransform = SourceTransform(),
        override val visible: Boolean = true,
        override val locked: Boolean = false,
        val filePath: String,
        val loop: Boolean = false,
        val volume: Float = 1f
    ) : SceneSource() {
        override fun withId(id: String): SceneSource = copy(id = id)
        override fun withTransform(transform: SourceTransform): SceneSource = copy(transform = transform)
    }

    @Serializable
    @SerialName("org.churchpresenter.app.churchpresenter.models.SceneSource.BrowserSource")
    data class BrowserSource(
        override val id: String,
        override val name: String,
        override val transform: SourceTransform = SourceTransform(),
        override val visible: Boolean = true,
        override val locked: Boolean = false,
        val url: String,
        val refreshInterval: Int = 0,
        val renderWidth: Int = 1920,
        val renderHeight: Int = 1080,
        val customCss: String = "",
        val fps: Int = 30,
        val forceTransparent: Boolean = false
    ) : SceneSource() {
        override fun withId(id: String): SceneSource = copy(id = id)
        override fun withTransform(transform: SourceTransform): SceneSource = copy(transform = transform)
    }

    @Serializable
    @SerialName("org.churchpresenter.app.churchpresenter.models.SceneSource.ShapeSource")
    data class ShapeSource(
        override val id: String,
        override val name: String,
        override val transform: SourceTransform = SourceTransform(),
        override val visible: Boolean = true,
        override val locked: Boolean = false,
        val shapeType: String = "rectangle",
        val strokeColor: String = "#FFFFFF",
        val fillColor: String = "#00000000",
        val strokeWidth: Float = 3f,
        val points: List<PathPoint> = emptyList(),
        val fillOpacity: Float = 1f,
        val strokeOpacity: Float = 1f,
        val showStroke: Boolean = true,
        val isGradient: Boolean = false,
        val gradientColor2: String = "#FFFFFF",
        val gradientColor2Opacity: Float = 1f,
        val gradientAngle: Float = 0f,
        val gradientPosition: Float = 0.5f
    ) : SceneSource() {
        override fun withId(id: String): SceneSource = copy(id = id)
        override fun withTransform(transform: SourceTransform): SceneSource = copy(transform = transform)
    }

    @Serializable
    @SerialName("org.churchpresenter.app.churchpresenter.models.SceneSource.ClockSource")
    data class ClockSource(
        override val id: String,
        override val name: String,
        override val transform: SourceTransform = SourceTransform(),
        override val visible: Boolean = true,
        override val locked: Boolean = false,
        val mode: String = ClockModes.CLOCK,
        val timeFormat: String = "24h",
        val showHours: Boolean = true,
        val showSeconds: Boolean = true,
        val fontFamily: String = "Arial",
        val fontSize: Int = 64,
        val fontColor: String = "#FFFFFF",
        val backgroundColor: String = "#00000000",
        val bold: Boolean = true,
        val italic: Boolean = false,
        val underline: Boolean = false,
        val strikethrough: Boolean = false,
        /** The band behind each line and the box around the block. */
        val backdrop: TextBackdrop = TextBackdrop(),
        /** The stroke drawn around the glyphs, under the fill. */
        val outline: TextOutline = TextOutline(),
        /** The length a [ClockModes.COUNTDOWN] counts down from. Not a time of day. */
        val targetHour: Int = 0,
        val targetMinute: Int = 0,
        val targetSecond: Int = 0,
        /** The time of day a [ClockModes.TARGET_TIME] counts down to, on a 24-hour clock. */
        val targetTimeHour: Int = 0,
        val targetTimeMinute: Int = 0,
        val targetTimeSecond: Int = 0,
        /** Shown in place of 00:00 once a [ClockModes.COUNTDOWN] runs out. Blank keeps the zeroes. */
        val expiredText: String = "",
        /** Space added between letters, as on [TextSource.letterSpacing]. */
        val letterSpacing: Float = 0f,
        /** Bends the read-out around a circle, as on [TextSource.curve]. */
        val curve: Float = 0f
    ) : SceneSource() {
        override fun withId(id: String): SceneSource = copy(id = id)
        override fun withTransform(transform: SourceTransform): SceneSource = copy(transform = transform)
    }

    @Serializable
    @SerialName("org.churchpresenter.app.churchpresenter.models.SceneSource.QRCodeSource")
    data class QRCodeSource(
        override val id: String,
        override val name: String,
        override val transform: SourceTransform = SourceTransform(),
        override val visible: Boolean = true,
        override val locked: Boolean = false,
        val contentType: String = "url",
        val content: String = "https://example.com",
        val wifiSsid: String = "",
        val wifiPassword: String = "",
        val wifiEncryption: String = "WPA",
        val wifiHidden: Boolean = false,
        val foregroundColor: String = "#000000",
        val backgroundColor: String = "#FFFFFF",
        val transparentBackground: Boolean = false,
        val errorCorrection: String = "M"
    ) : SceneSource() {
        override fun withId(id: String): SceneSource = copy(id = id)
        override fun withTransform(transform: SourceTransform): SceneSource = copy(transform = transform)
    }

    @Serializable
    @SerialName("org.churchpresenter.app.churchpresenter.models.SceneSource.CameraSource")
    data class CameraSource(
        override val id: String,
        override val name: String,
        override val transform: SourceTransform = SourceTransform(),
        override val visible: Boolean = true,
        override val locked: Boolean = false,
        val devicePath: String = "",
        val deviceName: String = "",
        val videoFormat: String = "",
        val videoConnection: Int = 0,
        val isDeckLink: Boolean = false,
        val deckLinkIndex: Int = -1
    ) : SceneSource() {
        override fun withId(id: String): SceneSource = copy(id = id)
        override fun withTransform(transform: SourceTransform): SceneSource = copy(transform = transform)
    }

    @Serializable
    @SerialName("org.churchpresenter.app.churchpresenter.models.SceneSource.ScreenCaptureSource")
    data class ScreenCaptureSource(
        override val id: String,
        override val name: String,
        override val transform: SourceTransform = SourceTransform(),
        override val visible: Boolean = true,
        override val locked: Boolean = false,
        val captureMode: String = "region",
        val captureX: Int = 0,
        val captureY: Int = 0,
        val captureWidth: Int = 1920,
        val captureHeight: Int = 1080,
        val captureInterval: Int = 100,
        val windowTitle: String = "",
        val windowId: String = ""
    ) : SceneSource() {
        override fun withId(id: String): SceneSource = copy(id = id)
        override fun withTransform(transform: SourceTransform): SceneSource = copy(transform = transform)
    }

    /**
     * A live NDI source from the network, received and drawn as a layer.
     *
     * [sourceName] is the full `MACHINE (Source)` name the sender advertises, which is the only
     * durable handle a source has — its address moves with DHCP, so that is stored beside it in
     * [sourceAddress] as a fallback for a source on another subnet rather than as the identity.
     *
     * [lowBandwidth] asks the sender for the low-resolution proxy it generates anyway. Worth having
     * on a layer that ends up as a corner inset: on a congested network it is the difference
     * between a service that runs and one that stutters.
     */
    @Serializable
    @SerialName("org.churchpresenter.app.churchpresenter.models.SceneSource.NdiSource")
    data class NdiSource(
        override val id: String,
        override val name: String,
        override val transform: SourceTransform = SourceTransform(),
        override val visible: Boolean = true,
        override val locked: Boolean = false,
        val sourceName: String = "",
        val sourceAddress: String = "",
        val lowBandwidth: Boolean = false
    ) : SceneSource() {
        override fun withId(id: String): SceneSource = copy(id = id)
        override fun withTransform(transform: SourceTransform): SceneSource = copy(transform = transform)
    }

    @Serializable
    @SerialName("org.churchpresenter.app.churchpresenter.models.SceneSource.BibleSource")
    data class BibleSource(
        override val id: String,
        override val name: String,
        override val transform: SourceTransform = SourceTransform(),
        override val visible: Boolean = true,
        override val locked: Boolean = false,
        val verseText: String = "",
        val referenceText: String = "",
        val fontFamily: String = "Arial",
        val fontSize: Int = 48,
        val fontColor: String = "#FFFFFF",
        val referenceFontSize: Int = 32,
        val referenceFontColor: String = "#FFFFFF",
        val backgroundColor: String = "#00000000",
        val bold: Boolean = false,
        val italic: Boolean = false,
        val underline: Boolean = false,
        val strikethrough: Boolean = false,
        val referenceBold: Boolean = false,
        val referenceItalic: Boolean = false,
        val referenceUnderline: Boolean = false,
        val referenceStrikethrough: Boolean = false,
        /** The band and box behind the verse, and behind its reference line. */
        val backdrop: TextBackdrop = TextBackdrop(),
        val referenceBackdrop: TextBackdrop = TextBackdrop(),
        /** The stroke around the verse's glyphs, and around its reference line's. */
        val outline: TextOutline = TextOutline(),
        val referenceOutline: TextOutline = TextOutline(),
        val horizontalAlignment: String = "center",
        val verticalAlignment: String = "center",
        val lineSpacing: Int = 100,
        /** Space added between letters, as on [TextSource.letterSpacing]. */
        val letterSpacing: Float = 0f,
        /** Bends the verse and its reference around a circle, as on [TextSource.curve]. */
        val curve: Float = 0f
    ) : SceneSource() {
        override fun withId(id: String): SceneSource = copy(id = id)
        override fun withTransform(transform: SourceTransform): SceneSource = copy(transform = transform)
    }
}

@Serializable
data class PathPoint(val x: Float, val y: Float)

@Serializable
data class Scene(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Scene",
    val sources: List<SceneSource> = emptyList(),
    val canvasWidth: Int = 1920,
    val canvasHeight: Int = 1080,
    /**
     * A second layout of the same layers for screens of the other orientation, or null for a scene
     * with one canvas. Its canvas is this one turned sideways — see [alternateScene].
     */
    val alternate: SceneAlternateLayout? = null
)

/**
 * Where each layer sits in a scene's second layout. Only position and size differ between the two
 * layouts; the layers themselves — their content and styling — are the scene's own.
 *
 * [transforms] is keyed by source id. A layer with no entry sits where it does in the main layout,
 * at the same fractions of the turned canvas.
 */
@Serializable
data class SceneAlternateLayout(
    val transforms: Map<String, SourceTransform> = emptyMap()
)

/** True when the canvas is wider than it is tall. A square counts as landscape. */
val Scene.isLandscape: Boolean get() = canvasWidth >= canvasHeight

/**
 * The scene as its second layout draws it: the canvas turned sideways (width and height swapped)
 * and each layer moved to its [SceneAlternateLayout.transforms] entry. With no alternate layout
 * this is the scene itself.
 */
fun Scene.alternateScene(): Scene {
    val layout = alternate ?: return this
    return copy(
        canvasWidth = canvasHeight,
        canvasHeight = canvasWidth,
        sources = sources.map { source ->
            val transform = layout.transforms[source.id]
                ?: source.transform.turnedFor(source, canvasWidth, canvasHeight, canvasHeight, canvasWidth)
            source.withTransform(transform)
        },
    )
}

/** Slack at the canvas edges, so a box flush with one still counts as inside. */
private const val EDGE_SLACK = 0.001f

/**
 * Where [source] starts on a [toWidth]×[toHeight] canvas when this is where it sits on a
 * [fromWidth]×[fromHeight] one — for a layer not yet moved in a scene's second layout.
 *
 * Copying the fractions would change the box's shape along with the canvas's, so:
 * - Pictures, video, captures, QR codes and shapes keep their shape, scaled by as much as the whole
 *   canvas has to shrink to fit the new one. A full-screen capture becomes a full-width band.
 * - Text, clocks and Bible verses keep their text size (fonts are in canvas pixels): the box keeps
 *   its pixel width, up to the canvas width, and its pixel area, so wrapped lines have room.
 * - A colour keeps its fractions, so a full-screen background stays full screen.
 *
 * The box's centre stays at the same fractions, and a box that was inside the canvas is kept inside.
 */
fun SourceTransform.turnedFor(
    source: SceneSource,
    fromWidth: Int,
    fromHeight: Int,
    toWidth: Int,
    toHeight: Int,
): SourceTransform {
    val sized = minOf(fromWidth, fromHeight, toWidth, toHeight) > 0
    if (source is SceneSource.ColorSource || !sized) return this
    val pixelWidth = width * fromWidth
    val pixelHeight = height * fromHeight
    val (newWidth, newHeight) = when (source) {
        is SceneSource.TextSource, is SceneSource.ClockSource, is SceneSource.BibleSource -> {
            val w = minOf(pixelWidth, toWidth.toFloat())
            val h = if (w > 0f) pixelWidth * pixelHeight / w else pixelHeight
            w / toWidth to h / toHeight
        }
        else -> {
            val scale = minOf(toWidth.toFloat() / fromWidth, toHeight.toFloat() / fromHeight)
            pixelWidth * scale / toWidth to pixelHeight * scale / toHeight
        }
    }
    var newX = x + width / 2f - newWidth / 2f
    var newY = y + height / 2f - newHeight / 2f
    val wasInside = x >= -EDGE_SLACK && y >= -EDGE_SLACK &&
        x + width <= 1f + EDGE_SLACK && y + height <= 1f + EDGE_SLACK
    if (wasInside) {
        newX = newX.coerceIn(0f, (1f - newWidth).coerceAtLeast(0f))
        newY = newY.coerceIn(0f, (1f - newHeight).coerceAtLeast(0f))
    }
    return copy(x = newX, y = newY, width = newWidth, height = newHeight)
}

/**
 * The layout to draw on an area of [width]×[height]: the alternate when there is one and the area
 * is the alternate's orientation, this scene otherwise. An area with no size keeps the main layout.
 */
fun Scene.forArea(width: Float, height: Float): Scene {
    val sized = width > 0f && height > 0f
    if (alternate == null || !sized || width == height) return this
    val areaLandscape = width > height
    return if (areaLandscape == isLandscape) this else alternateScene()
}
