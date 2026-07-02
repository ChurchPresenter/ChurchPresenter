package org.churchpresenter.app.churchpresenter.data.settings

import kotlinx.serialization.Serializable
import org.churchpresenter.app.churchpresenter.utils.Constants

/** A type of content that can be routed to a zone on the stage monitor screen. */
@Serializable
enum class StageMonitorContentType {
    BIBLE, SONGS, PRESENTATION, PRESENTATION_NOTES, PICTURES, MEDIA, LOWER_THIRD, WEB, STT, CANVAS, QA, DICTIONARY,
    CLOCK, DURATION_TIMER, COUNTDOWN_TIMER, SPECIFIC_TIME
}

/** Where a content type is routed to on the stage monitor screen. */
@Serializable
enum class StageMonitorZone {
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_MIDDLE, BOTTOM_RIGHT, FULL_SCREEN, NONE
}

/** The zones that are actually drawn and therefore have their own configurable style. */
@Serializable
enum class StageMonitorStyleZone {
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_MIDDLE, BOTTOM_RIGHT, FULL_SCREEN
}

fun StageMonitorZone.toStyleZone(): StageMonitorStyleZone? = when (this) {
    StageMonitorZone.TOP_LEFT -> StageMonitorStyleZone.TOP_LEFT
    StageMonitorZone.TOP_RIGHT -> StageMonitorStyleZone.TOP_RIGHT
    StageMonitorZone.BOTTOM_LEFT -> StageMonitorStyleZone.BOTTOM_LEFT
    StageMonitorZone.BOTTOM_MIDDLE -> StageMonitorStyleZone.BOTTOM_MIDDLE
    StageMonitorZone.BOTTOM_RIGHT -> StageMonitorStyleZone.BOTTOM_RIGHT
    StageMonitorZone.FULL_SCREEN -> StageMonitorStyleZone.FULL_SCREEN
    StageMonitorZone.NONE -> null
}

@Serializable
data class StageMonitorZoneStyle(
    val fontType: String = "Arial",
    val fontSize: Int = 40,
    val color: String = "#FFFFFF",
    val bgColor: String = "#1A1A2E",
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val shadow: Boolean = false,
    val shadowColor: String = "#000000",
    val shadowSize: Int = 100,
    val shadowOpacity: Int = 80,
    val verticalAlignment: String = Constants.TOP,
    val horizontalAlignment: String = Constants.LEFT
)

@Serializable
data class StageMonitorSettings(
    // Which zone each content type is routed to; every type defaults to Full Screen.
    val contentZones: Map<StageMonitorContentType, StageMonitorZone> = defaultContentZones(),

    // Font/color/style/alignment for each of the 6 drawable zones.
    val zoneStyles: Map<StageMonitorStyleZone, StageMonitorZoneStyle> = defaultZoneStyles()
) {
    /** Safe lookup that falls back to Full Screen for content types missing from older saved settings. */
    fun zoneFor(type: StageMonitorContentType): StageMonitorZone =
        contentZones[type] ?: StageMonitorZone.FULL_SCREEN

    /** Safe lookup that falls back to the built-in default style for zones missing from older saved settings. */
    fun styleFor(zone: StageMonitorStyleZone): StageMonitorZoneStyle =
        zoneStyles[zone] ?: defaultZoneStyles().getValue(zone)

    companion object {
        fun defaultContentZones(): Map<StageMonitorContentType, StageMonitorZone> =
            StageMonitorContentType.entries.associateWith { StageMonitorZone.FULL_SCREEN }

        fun defaultZoneStyles(): Map<StageMonitorStyleZone, StageMonitorZoneStyle> = mapOf(
            StageMonitorStyleZone.TOP_LEFT to StageMonitorZoneStyle(
                fontSize = 60, color = "#FFFFFF", bgColor = "#1A1A2E", shadow = true
            ),
            StageMonitorStyleZone.TOP_RIGHT to StageMonitorZoneStyle(
                fontSize = 80, color = "#00FF88", bgColor = "#0D1A0D", bold = true,
                verticalAlignment = Constants.MIDDLE, horizontalAlignment = Constants.CENTER
            ),
            StageMonitorStyleZone.BOTTOM_LEFT to StageMonitorZoneStyle(
                fontSize = 40, color = "#AAAAAA", bgColor = "#0D0D1A", italic = true
            ),
            StageMonitorStyleZone.BOTTOM_MIDDLE to StageMonitorZoneStyle(
                fontSize = 72, color = "#FFFFFF", bgColor = "#000000",
                verticalAlignment = Constants.MIDDLE, horizontalAlignment = Constants.CENTER
            ),
            StageMonitorStyleZone.BOTTOM_RIGHT to StageMonitorZoneStyle(
                fontSize = 36, color = "#DDDDDD", bgColor = "#111111"
            ),
            StageMonitorStyleZone.FULL_SCREEN to StageMonitorZoneStyle(
                fontSize = 90, color = "#FFFFFF", bgColor = "#000000",
                verticalAlignment = Constants.MIDDLE, horizontalAlignment = Constants.CENTER
            )
        )
    }
}
