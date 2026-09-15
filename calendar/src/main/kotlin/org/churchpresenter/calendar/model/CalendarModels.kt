package org.churchpresenter.calendar.model

import kotlinx.serialization.Serializable
import org.churchpresenter.core.models.schedule.ScheduleItem

/**
 * What the planner holds, and the one thing `calendar.json` contains.
 *
 * **A run of show is a `List<ScheduleItem>` and nothing else.** That is the whole design decision
 * behind this file: the planner does not carry a parallel row type that has to be converted on the
 * way to the Schedule tab. A planned service is therefore the same shape as a saved `.schedule`
 * file plus a start time, which is what makes "load into Schedule" a copy rather than a mapping,
 * lets a section heading be an ordinary [ScheduleItem.LabelItem] with the colors it already has,
 * and will let a cue's payload be executed by the app's existing `executeProjectItem` without the
 * planner knowing what any of the item types mean.
 *
 * [version] is the file's schema version, not the app's. Nothing reads it yet; it is written from
 * the first release so a later format change has something to branch on.
 */
@Serializable
data class CalendarDocument(
    val version: Int = CURRENT_CALENDAR_VERSION,
    val services: List<PlannedService> = emptyList(),
    val preferences: CalendarPreferences = CalendarPreferences(),
) {
    /** Every service planned for [date], earliest start first. */
    fun servicesOn(date: String): List<PlannedService> =
        services.filter { it.date == date }.sortedBy { it.startTime }

    /** The dates that have at least one service, for the month grid's dots. */
    fun plannedDates(): Set<String> = services.mapTo(mutableSetOf()) { it.date }

    fun serviceById(id: String): PlannedService? = services.firstOrNull { it.id == id }

    fun withService(service: PlannedService): CalendarDocument {
        val index = services.indexOfFirst { it.id == service.id }
        return copy(
            services = if (index >= 0) {
                services.toMutableList().also { it[index] = service }
            } else {
                services + service
            }
        )
    }

    fun withoutService(id: String): CalendarDocument = copy(services = services.filterNot { it.id == id })
}

const val CURRENT_CALENDAR_VERSION: Int = 1

/**
 * One planned service on one day.
 *
 * [date] and [startTime] are stored as text rather than as `LocalDate`/`LocalTime` deliberately:
 * they are wall-clock, not instants. A service planned for 10:00 is at 10:00 whatever the machine's
 * zone was when it was typed and whatever it is on the Sunday it runs, and a stored instant would
 * shift it across a DST boundary — which in this country falls on a Sunday morning.
 */
@Serializable
data class PlannedService(
    val id: String,
    /** ISO-8601 local date, `2026-09-20`. */
    val date: String,
    val name: String,
    /** 24-hour local wall clock, `10:00`. */
    val startTime: String,
    val kind: String = ServiceKind.SUNDAY.id,
    /** The run of show, in order. Section headings are [ScheduleItem.LabelItem] rows. */
    val items: List<ScheduleItem> = emptyList(),
    /**
     * How long each row is planned to take, in seconds, keyed by [ScheduleItem.id].
     *
     * Absent means "no plan yet" rather than zero, which is why this is a map and not a field on
     * some wrapper row: a row nobody has estimated shows blank, not `0:00`, and once live durations
     * are being measured an absent entry is what a measured suggestion fills in.
     */
    val plannedSeconds: Map<String, Int> = emptyMap(),
    /**
     * Declared from the first release although nothing writes it yet.
     *
     * The cue engine is a later phase, and an empty list here costs one key in the file — whereas
     * adding the field afterwards would mean a schema migration for every calendar already saved.
     */
    val cues: List<ServiceCue> = emptyList(),
    /** Whether this service's cues may fire. False is "planned, but do not automate". */
    val armed: Boolean = true,
) {
    /** The planned length of the whole service, counting only rows that have an estimate. */
    fun plannedTotalSeconds(): Int = plannedSeconds.values.sum()

    /** Run-of-show rows that are content rather than section headings. */
    fun contentItems(): List<ScheduleItem> = items.filterNot { it is ScheduleItem.LabelItem }
}

/**
 * A timed action attached to a service.
 *
 * Nothing constructs one yet — see [PlannedService.cues]. The shape is fixed now because it is what
 * the rest of the design rests on: [payload] is an ordinary [ScheduleItem], so every content cue is
 * something the app can already put on screen, and [action] covers only the few things that are not
 * content at all.
 */
@Serializable
data class ServiceCue(
    val id: String,
    /** Minutes relative to the service start; negative is before it. Ignored when [absoluteTime] is set. */
    val offsetMinutes: Int = 0,
    /** `09:45` to pin the cue to the wall clock instead of to the service start. Empty to use [offsetMinutes]. */
    val absoluteTime: String = "",
    val label: String = "",
    /** What to put on screen, for [CueAction.PROJECT]. Null for every other action. */
    val payload: ScheduleItem? = null,
    val action: String = CueAction.PROJECT,
    val enabled: Boolean = true,
)

/** The few cue actions that are not "put this [ScheduleItem] on screen". */
object CueAction {
    const val PROJECT = "project"
    const val BLANK = "blank"
    const val OBS_SCENE = "obsScene"
    const val ATEM_KEY = "atemKey"
}

/**
 * The kinds of service the grid colors and filters by.
 *
 * An enum rather than free text because the month grid's legend counts by it, and a typo would
 * silently produce a second category. The label is a string resource, resolved at the call site —
 * this module's model layer deliberately knows no Compose.
 */
enum class ServiceKind(val id: String, val colorHex: String) {
    SUNDAY("sunday", "#5B9DF5"),
    MIDWEEK("midweek", "#C9A2F0"),
    SPECIAL("special", "#E8A33D");

    companion object {
        /** Unknown ids fall back to [SUNDAY] rather than throwing — a hand-edited file still opens. */
        fun from(id: String): ServiceKind = entries.firstOrNull { it.id == id } ?: SUNDAY
    }
}

/** Calendar-wide preferences, saved beside the services rather than in `settings.json`. */
@Serializable
data class CalendarPreferences(
    /** The start time a newly added service is created with. */
    val defaultStartTime: String = "10:00",
    /** Whether a newly added service starts armed for automation. */
    val armByDefault: Boolean = true,
    /**
     * The section headings available in every run of show, matched by name.
     *
     * Names rather than ids, which is what makes a section defined here apply to services that
     * already use it: a run of show stores its heading as an ordinary
     * [ScheduleItem.LabelItem], so recoloring `Worship` here recolors every `Worship` heading
     * already planned, without rewriting any of them.
     */
    val sections: List<SectionStyle> = defaultSections(),
    /** Offered as a song's length when nothing better is known. */
    val defaultItemSeconds: Int = DEFAULT_ITEM_SECONDS,
    /** Offered as a presentation's length when nothing better is known. */
    val defaultSermonSeconds: Int = DEFAULT_SERMON_SECONDS,
)

/** 4:30 — the length of a fairly ordinary worship song. */
private const val DEFAULT_ITEM_SECONDS = 270

/** 32:00 — the design's own default, and close enough to most sermons to be a useful start. */
private const val DEFAULT_SERMON_SECONDS = 1920

/** A named section heading and the color it is drawn in. */
@Serializable
data class SectionStyle(val name: String, val colorHex: String)

/** The sections a new calendar starts with — the ones nearly every order of service has. */
fun defaultSections(): List<SectionStyle> = listOf(
    SectionStyle("Pre-Service", "#4FD3E8"),
    SectionStyle("Worship", "#5B9DF5"),
    SectionStyle("Word", "#E8A33D"),
    SectionStyle("Response", "#6FD8A8"),
    SectionStyle("Communion", "#C9A2F0"),
    SectionStyle("Closing", "#E0757F"),
)

/** The colors a section can be given, as the design's swatch row. */
val SECTION_SWATCHES: List<String> = listOf(
    "#E8A33D", "#5B9DF5", "#6FD8A8", "#C9A2F0",
    "#E0757F", "#4FD3E8", "#D4C25A", "#8B9099",
)
