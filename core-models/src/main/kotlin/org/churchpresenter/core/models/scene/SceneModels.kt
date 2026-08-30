package org.churchpresenter.core.models.scene

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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
    ) : SceneSource()

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
    ) : SceneSource()

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
    ) : SceneSource()

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
    ) : SceneSource()

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
    ) : SceneSource()

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
    ) : SceneSource()

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
    ) : SceneSource()

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
    ) : SceneSource()

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
    ) : SceneSource()

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
    ) : SceneSource()

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
        val horizontalAlignment: String = "center",
        val verticalAlignment: String = "center",
        val lineSpacing: Int = 100,
        /** Space added between letters, as on [TextSource.letterSpacing]. */
        val letterSpacing: Float = 0f,
        /** Bends the verse and its reference around a circle, as on [TextSource.curve]. */
        val curve: Float = 0f
    ) : SceneSource()
}

@Serializable
data class PathPoint(val x: Float, val y: Float)

@Serializable
data class Scene(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Scene",
    val sources: List<SceneSource> = emptyList(),
    val canvasWidth: Int = 1920,
    val canvasHeight: Int = 1080
)
