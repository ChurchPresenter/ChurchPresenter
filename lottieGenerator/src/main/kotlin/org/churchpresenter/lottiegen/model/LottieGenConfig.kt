package org.churchpresenter.lottiegen.model

import kotlinx.serialization.Serializable

@Serializable
data class LottieGenConfig(
    // Canvas
    val canvasW: Int = 1920,
    val canvasH: Int = 1080,
    val scaleFactor: Float = 1f,
    // Style
    val style: String = "1",
    val align: String = "left",
    // Text
    val nameText: String = "Church Presenter",
    val infoText: String = "Software Engineer",
    // Text Style
    val fontFamily: String = "Verdana",
    val baseSize: Int = 24,
    val nameSize: Float = 1.2f,
    val infoSize: Float = 0.9f,
    val nameWeight: Int = 700,
    val infoWeight: Int = 400,
    val nameTransform: String = "uppercase",
    val infoTransform: String = "none",
    // Colors
    val nameColor: String = "#F2F2F2",
    val infoColor: String = "#CCCCCC",
    val accentColor: String = "#D54141",
    val bgColor: String = "#222222",
    val borderColor: String = "#D54141",
    val nameColorAlpha: Int = 100,
    val infoColorAlpha: Int = 100,
    val accentColorAlpha: Int = 100,
    val bgColorAlpha: Int = 100,
    val borderColorAlpha: Int = 100,
    // Shape
    val corners: Float = 0.3f,
    val borderThickness: Float = 0f,
    val bgEnabled: Boolean = true,
    val shadowEnabled: Boolean = false,
    val hideName: Boolean = false,
    val hideInfo: Boolean = false,
    // Logo
    val logoEnabled: Boolean = false,
    val logoSize: Float = 3.5f,
    val logoSelect: String = "",
    val logoData: String? = null,
    val logoW: Int = 0,
    val logoH: Int = 0,
    // Timing
    val animDuration: Float = 4f,
    val holdDuration: Float = 3f,
    // Position
    val marginH: Float = 2f,
    val marginV: Float = 2f,
    val lineSpacing: Float = 0.2f
)
