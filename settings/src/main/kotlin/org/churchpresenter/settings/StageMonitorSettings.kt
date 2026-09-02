package org.churchpresenter.settings

import kotlinx.serialization.Serializable
import org.churchpresenter.core.models.text.TextBackdrop
import org.churchpresenter.settings.utils.Constants

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

/**
 * Where a content type is routed to on the stage monitor screen.
 *
 * A..E are the layout's slots, filled in the order [StageMonitorLayout] draws them, so which
 * position a slot occupies depends on the layout in force. FULL_SCREEN takes over the whole
 * monitor whatever the layout, and NONE is not drawn at all.
 */
@Serializable
enum class StageMonitorZone {
    A, B, C, D, E, FULL_SCREEN, NONE
}

/** The zones that are actually drawn and therefore have their own configurable style. */
@Serializable
enum class StageMonitorStyleZone {
    A, B, C, D, E, FULL_SCREEN
}

/** One cell of a layout row. [weight] is its share of the row's width. */
data class StageMonitorCell(val slot: StageMonitorStyleZone, val weight: Float = 1f)

/** One row of a layout. [weight] is its share of the monitor's height. */
data class StageMonitorRow(val weight: Float, val cells: List<StageMonitorCell>)

/**
 * How a stage monitor divides its screen, as rows of cells filled A, B, C… in drawing order.
 *
 * FULL_SCREEN is never a layout slot — it is the override that replaces the whole grid — which
 * `StageMonitorLayoutTest` holds the layouts to.
 */
@Suppress("MagicNumber")
enum class StageMonitorLayout(val rows: List<StageMonitorRow>) {
    TOP_BOTTOM(listOf(row(2f, StageMonitorStyleZone.A), row(1f, StageMonitorStyleZone.B))),
    LEFT_RIGHT(listOf(row(1f, StageMonitorStyleZone.A, StageMonitorStyleZone.B))),
    TOP_TWO_BELOW(
        listOf(
            row(2f, StageMonitorStyleZone.A),
            row(1f, StageMonitorStyleZone.B, StageMonitorStyleZone.C),
        )
    ),
    THREE_ROWS(
        listOf(
            row(2f, StageMonitorStyleZone.A),
            row(1f, StageMonitorStyleZone.B),
            row(1f, StageMonitorStyleZone.C),
        )
    ),
    QUAD(
        listOf(
            row(1f, StageMonitorStyleZone.A, StageMonitorStyleZone.B),
            row(1f, StageMonitorStyleZone.C, StageMonitorStyleZone.D),
        )
    ),
    TOP_THREE_BELOW(
        listOf(
            row(2f, StageMonitorStyleZone.A),
            row(1f, StageMonitorStyleZone.B, StageMonitorStyleZone.C, StageMonitorStyleZone.D),
        )
    ),

    /**
     * The arrangement the stage monitor has always drawn, and still the default: two zones across
     * the top over three along the bottom, the middle of the three a little narrower.
     */
    CLASSIC(
        listOf(
            row(2f, StageMonitorStyleZone.A, StageMonitorStyleZone.B),
            StageMonitorRow(
                weight = 1f,
                cells = listOf(
                    StageMonitorCell(StageMonitorStyleZone.C),
                    StageMonitorCell(StageMonitorStyleZone.D, weight = 0.8f),
                    StageMonitorCell(StageMonitorStyleZone.E),
                ),
            ),
        )
    ),
    TOP_FOUR_BELOW(
        listOf(
            row(2f, StageMonitorStyleZone.A),
            row(
                1f,
                StageMonitorStyleZone.B,
                StageMonitorStyleZone.C,
                StageMonitorStyleZone.D,
                StageMonitorStyleZone.E,
            ),
        )
    );

    /** The slots this layout draws, in drawing order. */
    val slots: List<StageMonitorStyleZone> get() = rows.flatMap { r -> r.cells.map { it.slot } }

    /** True when [zone] is drawn by this layout — FULL_SCREEN always is, NONE never. */
    fun draws(zone: StageMonitorZone): Boolean = when (zone) {
        StageMonitorZone.FULL_SCREEN -> true
        StageMonitorZone.NONE -> false
        else -> zone.toStyleZone() in slots
    }

    companion object {
        /** The layouts offering [count] zones, in catalog order. */
        fun withZoneCount(count: Int): List<StageMonitorLayout> = entries.filter { it.slots.size == count }

        /** The zone counts the catalog offers, ascending. */
        fun zoneCounts(): List<Int> = entries.map { it.slots.size }.distinct().sorted()
    }
}

private fun row(weight: Float, vararg slots: StageMonitorStyleZone) =
    StageMonitorRow(weight, slots.map { StageMonitorCell(it) })

/**
 * Where the metronome flash dot is anchored on the stage monitor screen — a free 3x3 grid,
 * independent of the content zones above (no full-screen option since it's a small overlay).
 */
@Serializable
enum class MetronomePosition {
    NONE,
    TOP_LEFT, TOP_CENTER, TOP_RIGHT,
    MIDDLE_LEFT, CENTER, MIDDLE_RIGHT,
    BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT
}

fun StageMonitorZone.toStyleZone(): StageMonitorStyleZone? = when (this) {
    StageMonitorZone.A -> StageMonitorStyleZone.A
    StageMonitorZone.B -> StageMonitorStyleZone.B
    StageMonitorZone.C -> StageMonitorStyleZone.C
    StageMonitorZone.D -> StageMonitorStyleZone.D
    StageMonitorZone.E -> StageMonitorStyleZone.E
    StageMonitorZone.FULL_SCREEN -> StageMonitorStyleZone.FULL_SCREEN
    StageMonitorZone.NONE -> null
}

/** The routing target that draws this style zone. */
fun StageMonitorStyleZone.toZone(): StageMonitorZone = when (this) {
    StageMonitorStyleZone.A -> StageMonitorZone.A
    StageMonitorStyleZone.B -> StageMonitorZone.B
    StageMonitorStyleZone.C -> StageMonitorZone.C
    StageMonitorStyleZone.D -> StageMonitorZone.D
    StageMonitorStyleZone.E -> StageMonitorZone.E
    StageMonitorStyleZone.FULL_SCREEN -> StageMonitorZone.FULL_SCREEN
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
    /** The band behind each line and the box around the block, for this zone's text. */
    val backdrop: TextBackdrop = TextBackdrop(),
    val verticalAlignment: String = Constants.TOP,
    val horizontalAlignment: String = Constants.LEFT,
    /**
     * Colour the chords are drawn in when this zone shows a song's chart. Everything else about
     * them — face, size, weight — follows [color] and [fontType], so a chart reads as this zone's
     * own words with the chords lifted above them; only the colour separates the two.
     */
    val chordColor: String = "#4FD3E8"
)

private const val DEFAULT_TRANSITION_MS = 500f

@Serializable
data class StageMonitorSettings(
    // How the screen is divided; the zones each content type can be routed to follow from it.
    val layout: StageMonitorLayout = StageMonitorLayout.CLASSIC,

    // Which zone each content type is routed to; see defaultContentZones() for per-type defaults.
    val contentZones: Map<StageMonitorContentType, StageMonitorZone> = defaultContentZones(),

    // Font/color/style/alignment for each of the drawable zones.
    val zoneStyles: Map<StageMonitorStyleZone, StageMonitorZoneStyle> = defaultZoneStyles(),

    // Where the metronome flash dot is anchored; NONE = disabled (default).
    val metronomePosition: MetronomePosition = MetronomePosition.NONE,

    // How text changing in a zone is animated. One setting for the whole monitor: a zone fading on
    // its own schedule while the one beside it cuts would read as a fault rather than a choice.
    val fadeIn: Boolean = true,
    val fadeOut: Boolean = true,
    val crossfade: Boolean = false,
    val transitionDuration: Float = DEFAULT_TRANSITION_MS
) {
    /** Safe lookup that falls back to the built-in default zone for content types missing from older saved settings. */
    fun zoneFor(type: StageMonitorContentType): StageMonitorZone =
        contentZones[type] ?: defaultContentZones().getValue(type)

    /** Safe lookup that falls back to the built-in default style for zones missing from older saved settings. */
    fun styleFor(zone: StageMonitorStyleZone): StageMonitorZoneStyle =
        zoneStyles[zone] ?: defaultZoneStyles().getValue(zone)

    /** The content types routed to [zone], in enum order. */
    fun typesIn(zone: StageMonitorZone): List<StageMonitorContentType> =
        StageMonitorContentType.entries.filter { zoneFor(it) == zone }

    /**
     * These settings on [layout], with anything routed to a zone it does not draw sent to None.
     *
     * A smaller layout really does take zones away, and a routing pointing at one of them is a
     * setting that silently does nothing. Saying None is the honest version of that, and it is what
     * the dropdown then shows.
     */
    fun withLayout(layout: StageMonitorLayout): StageMonitorSettings {
        val rerouted = StageMonitorContentType.entries
            .filter { !layout.draws(zoneFor(it)) }
            .associateWith { StageMonitorZone.NONE }
        return copy(layout = layout, contentZones = contentZones + rerouted)
    }

    /**
     * The types routed to a zone this layout does not draw, so nothing puts them on screen.
     *
     * [withLayout] keeps this empty for anything routed through the settings UI. It is here for a
     * document that arrived some other way — an import, or a file written by a build with layouts
     * this one does not have — where the routing has to be said out loud rather than hidden.
     */
    fun strandedTypes(): List<StageMonitorContentType> =
        StageMonitorContentType.entries.filter { !layout.draws(zoneFor(it)) && zoneFor(it) != StageMonitorZone.NONE }

    companion object {
        fun defaultContentZones(): Map<StageMonitorContentType, StageMonitorZone> =
            StageMonitorContentType.entries.associateWith { StageMonitorZone.FULL_SCREEN } + mapOf(
                StageMonitorContentType.BIBLE to StageMonitorZone.A,
                StageMonitorContentType.SONGS to StageMonitorZone.A,
                StageMonitorContentType.NEXT to StageMonitorZone.B,
                StageMonitorContentType.CLOCK to StageMonitorZone.D,
                StageMonitorContentType.ANNOUNCEMENT_TEXT to StageMonitorZone.C
            )

        @Suppress("MagicNumber")
        fun defaultZoneStyles(): Map<StageMonitorStyleZone, StageMonitorZoneStyle> = mapOf(
            StageMonitorStyleZone.A to StageMonitorZoneStyle(
                fontSize = 35, color = "#FFFFFF", bgColor = "#000000", shadow = true
            ),
            StageMonitorStyleZone.B to StageMonitorZoneStyle(
                fontSize = 35, color = "#FFFFFF", bgColor = "#000000", bold = true,
                verticalAlignment = Constants.MIDDLE, horizontalAlignment = Constants.CENTER
            ),
            StageMonitorStyleZone.C to StageMonitorZoneStyle(
                fontSize = 35, color = "#FFFFFF", bgColor = "#000000", italic = true
            ),
            StageMonitorStyleZone.D to StageMonitorZoneStyle(
                fontSize = 35, color = "#FFFFFF", bgColor = "#000000",
                verticalAlignment = Constants.MIDDLE, horizontalAlignment = Constants.CENTER
            ),
            StageMonitorStyleZone.E to StageMonitorZoneStyle(
                fontSize = 35, color = "#FFFFFF", bgColor = "#000000"
            ),
            StageMonitorStyleZone.FULL_SCREEN to StageMonitorZoneStyle(
                fontSize = 80, color = "#FFFFFF", bgColor = "#000000",
                verticalAlignment = Constants.MIDDLE, horizontalAlignment = Constants.CENTER
            )
        )
    }
}
