package org.churchpresenter.app.churchpresenter.data.settings

import kotlinx.serialization.Serializable
import org.churchpresenter.app.churchpresenter.utils.Constants

/** What content is displayed in a given stage monitor screen zone. */
@Serializable
enum class StageMonitorContent {
    CLOCK, TIMER, NOTES, CURRENT_SLIDE, NEXT_SLIDE
}

@Serializable
data class StageMonitorSettings(
    // Which content type appears in each screen zone
    val topLeftContent: StageMonitorContent = StageMonitorContent.CURRENT_SLIDE,
    val topRightContent: StageMonitorContent = StageMonitorContent.TIMER,
    val bottomLeftContent: StageMonitorContent = StageMonitorContent.NEXT_SLIDE,
    val bottomCenterContent: StageMonitorContent = StageMonitorContent.CLOCK,
    val bottomRightContent: StageMonitorContent = StageMonitorContent.NOTES,

    // Top-Left: Current slide
    val currentFontSize: Int = 60,
    val currentFontType: String = "Arial",
    val currentColor: String = "#FFFFFF",
    val currentBgColor: String = "#1A1A2E",
    val currentBold: Boolean = false,
    val currentItalic: Boolean = false,
    val currentUnderline: Boolean = false,
    val currentShadow: Boolean = true,
    val currentShadowColor: String = "#000000",
    val currentShadowSize: Int = 100,
    val currentShadowOpacity: Int = 80,
    val currentVerticalAlignment: String = Constants.TOP,
    val currentHorizontalAlignment: String = Constants.LEFT,

    // Bottom-Left: Next slide
    val nextFontSize: Int = 40,
    val nextFontType: String = "Arial",
    val nextColor: String = "#AAAAAA",
    val nextBgColor: String = "#0D0D1A",
    val nextBold: Boolean = false,
    val nextItalic: Boolean = true,
    val nextUnderline: Boolean = false,
    val nextShadow: Boolean = false,
    val nextShadowColor: String = "#000000",
    val nextShadowSize: Int = 100,
    val nextShadowOpacity: Int = 80,
    val nextVerticalAlignment: String = Constants.TOP,
    val nextHorizontalAlignment: String = Constants.LEFT,

    // Top-Right: Timer
    val showTimer: Boolean = true,
    val timerFontSize: Int = 80,
    val timerFontType: String = "Arial",
    val timerColor: String = "#00FF88",
    val timerBgColor: String = "#0D1A0D",
    val timerBold: Boolean = true,
    val timerItalic: Boolean = false,
    val timerUnderline: Boolean = false,
    val timerShadow: Boolean = false,
    val timerShadowColor: String = "#000000",
    val timerShadowSize: Int = 100,
    val timerShadowOpacity: Int = 80,
    // Song/Bible label shown in top-right corner of current/next panels
    val showSongBibleLabel: Boolean = true,
    val labelFontSize: Int = 22,
    val labelFontType: String = "Arial",
    val labelColor: String = "#888888",
    val labelBold: Boolean = false,
    val labelItalic: Boolean = false,

    // Bottom-Center: Clock
    val showClock: Boolean = true,
    val clockFontSize: Int = 72,
    val clockFontType: String = "Arial",
    val clockColor: String = "#FFFFFF",
    val clockBgColor: String = "#000000",
    val clockBold: Boolean = false,
    val clockItalic: Boolean = false,
    val clockUnderline: Boolean = false,
    val clockShadow: Boolean = false,
    val clockShadowColor: String = "#000000",
    val clockShadowSize: Int = 100,
    val clockShadowOpacity: Int = 80,
    val clockShowSeconds: Boolean = true,
    val clockFormat24h: Boolean = true,

    // Bottom-Right: Presenter Notes
    val notesFontSize: Int = 36,
    val notesFontType: String = "Arial",
    val notesColor: String = "#DDDDDD",
    val notesBgColor: String = "#111111",
    val notesBold: Boolean = false,
    val notesItalic: Boolean = false,
    val notesUnderline: Boolean = false,
    val notesShadow: Boolean = false,
    val notesShadowColor: String = "#000000",
    val notesShadowSize: Int = 100,
    val notesShadowOpacity: Int = 80
)
