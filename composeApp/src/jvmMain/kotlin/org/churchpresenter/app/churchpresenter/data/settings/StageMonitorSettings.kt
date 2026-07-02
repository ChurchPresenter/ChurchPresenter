package org.churchpresenter.app.churchpresenter.data.settings

import kotlinx.serialization.Serializable
import org.churchpresenter.app.churchpresenter.utils.Constants

/** A type of content that can be routed to a zone on the stage monitor screen. */
@Serializable
enum class StageMonitorContentType {
    BIBLE, SONGS, PRESENTATION, PRESENTATION_NOTES, PICTURES, MEDIA, LOWER_THIRD, WEB, STT, CANVAS, QA, DICTIONARY,
    CLOCK,
    /**
     * Announcements text and all timer variants (Duration/Countdown/Specific Time) share one
     * pre-formatted string with no way to tell them apart, so they're a single content type —
     * having separate entries that all show identical text just duplicated it across zones.
     */
    ANNOUNCEMENT_TEXT,
    /** Next Bible verse (when presenting Bible) or next song line/section (when presenting Songs). */
    NEXT
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
    // Which zone each content type is routed to; see defaultContentZones() for per-type defaults.
    val contentZones: Map<StageMonitorContentType, StageMonitorZone> = defaultContentZones(),

    // Font/color/style/alignment for each of the 6 drawable zones.
    val zoneStyles: Map<StageMonitorStyleZone, StageMonitorZoneStyle> = defaultZoneStyles()
) {
    /** Safe lookup that falls back to the built-in default zone for content types missing from older saved settings. */
    fun zoneFor(type: StageMonitorContentType): StageMonitorZone =
        contentZones[type] ?: defaultContentZones().getValue(type)

    /** Safe lookup that falls back to the built-in default style for zones missing from older saved settings. */
    fun styleFor(zone: StageMonitorStyleZone): StageMonitorZoneStyle =
        zoneStyles[zone] ?: defaultZoneStyles().getValue(zone)

    companion object {
        fun defaultContentZones(): Map<StageMonitorContentType, StageMonitorZone> =
            StageMonitorContentType.entries.associateWith { StageMonitorZone.FULL_SCREEN } + mapOf(
                StageMonitorContentType.BIBLE to StageMonitorZone.TOP_LEFT,
                StageMonitorContentType.SONGS to StageMonitorZone.TOP_LEFT,
                StageMonitorContentType.NEXT to StageMonitorZone.TOP_RIGHT,
                StageMonitorContentType.CLOCK to StageMonitorZone.BOTTOM_MIDDLE,
                StageMonitorContentType.ANNOUNCEMENT_TEXT to StageMonitorZone.BOTTOM_LEFT
            )

        fun defaultZoneStyles(): Map<StageMonitorStyleZone, StageMonitorZoneStyle> = mapOf(
            StageMonitorStyleZone.TOP_LEFT to StageMonitorZoneStyle(
                fontSize = 35, color = "#FFFFFF", bgColor = "#000000", shadow = true
            ),
            StageMonitorStyleZone.TOP_RIGHT to StageMonitorZoneStyle(
                fontSize = 35, color = "#FFFFFF", bgColor = "#000000", bold = true,
                verticalAlignment = Constants.MIDDLE, horizontalAlignment = Constants.CENTER
            ),
            StageMonitorStyleZone.BOTTOM_LEFT to StageMonitorZoneStyle(
                fontSize = 35, color = "#FFFFFF", bgColor = "#000000", italic = true
            ),
            StageMonitorStyleZone.BOTTOM_MIDDLE to StageMonitorZoneStyle(
                fontSize = 35, color = "#FFFFFF", bgColor = "#000000",
                verticalAlignment = Constants.MIDDLE, horizontalAlignment = Constants.CENTER
            ),
            StageMonitorStyleZone.BOTTOM_RIGHT to StageMonitorZoneStyle(
                fontSize = 35, color = "#FFFFFF", bgColor = "#000000"
            ),
            StageMonitorStyleZone.FULL_SCREEN to StageMonitorZoneStyle(
                fontSize = 80, color = "#FFFFFF", bgColor = "#000000",
                verticalAlignment = Constants.MIDDLE, horizontalAlignment = Constants.CENTER
            )
        )
    }
}
