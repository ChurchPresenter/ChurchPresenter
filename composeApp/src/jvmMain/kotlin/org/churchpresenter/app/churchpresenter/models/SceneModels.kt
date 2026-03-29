package org.churchpresenter.app.churchpresenter.models

import kotlinx.serialization.Serializable
import java.util.UUID

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
        val horizontalAlignment: String = "center"
    ) : SceneSource()

    @Serializable
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
    data class BrowserSource(
        override val id: String,
        override val name: String,
        override val transform: SourceTransform = SourceTransform(),
        override val visible: Boolean = true,
        override val locked: Boolean = false,
        val url: String,
        val refreshInterval: Int = 0
    ) : SceneSource()

    @Serializable
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
    data class ClockSource(
        override val id: String,
        override val name: String,
        override val transform: SourceTransform = SourceTransform(),
        override val visible: Boolean = true,
        override val locked: Boolean = false,
        val mode: String = "clock",
        val timeFormat: String = "24h",
        val showHours: Boolean = true,
        val showSeconds: Boolean = true,
        val fontFamily: String = "Arial",
        val fontSize: Int = 64,
        val fontColor: String = "#FFFFFF",
        val backgroundColor: String = "#00000000",
        val bold: Boolean = true,
        val targetHour: Int = 0,
        val targetMinute: Int = 0,
        val targetSecond: Int = 0
    ) : SceneSource()

    @Serializable
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
    data class CameraSource(
        override val id: String,
        override val name: String,
        override val transform: SourceTransform = SourceTransform(),
        override val visible: Boolean = true,
        override val locked: Boolean = false,
        val devicePath: String = "",
        val deviceName: String = ""
    ) : SceneSource()

    @Serializable
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
