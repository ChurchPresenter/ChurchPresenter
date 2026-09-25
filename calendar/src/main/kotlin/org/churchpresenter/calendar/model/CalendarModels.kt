package org.churchpresenter.calendar.model

import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant
import org.churchpresenter.core.models.schedule.CueAction
import org.churchpresenter.core.models.schedule.RowTiming
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
    /** Saved run-of-show templates, offered under `Start from` when a service is added. */
    val templates: List<SavedTemplate> = emptyList(),
    /**
     * Services deleted here, as id → when, so a delete survives a merge.
     *
     * Without these, syncing two copies of this file can only ever *add*: the machine that still
     * has the service sees an id the other one lacks and puts it back. The stamp is what makes a
     * deletion something that happened at a time rather than an absence — a service edited after
     * it was deleted elsewhere is kept, which is the answer somebody would expect.
     *
     * Pruned by [mergedWith] once they are older than anything a merge could still resurrect.
     * [deletedVersions] holds each one's [PlannedService.version], which is what orders it against
     * an edit; the stamp only breaks a tie.
     */
    val deletedServices: Map<String, String> = emptyMap(),
    val deletedVersions: Map<String, Long> = emptyMap(),
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

    fun withoutService(id: String, at: Instant = Instant.now()): CalendarDocument = copy(
        services = services.filterNot { it.id == id },
        // Remembered rather than simply dropped -- see [deletedServices]. The deletion is one more
        // edit to the service, so it outranks every copy of it that came before.
        deletedServices = deletedServices + (id to at.toString()),
        deletedVersions = deletedVersions + (id to (serviceById(id)?.version ?: deletedVersions[id] ?: 0L) + 1),
    )

    /** Every occurrence of the series [seriesId], in date order. Empty for a blank id. */
    fun servicesInSeries(seriesId: String): List<PlannedService> =
        if (seriesId.isEmpty()) emptyList() else services.filter { it.seriesId == seriesId }.sortedBy { it.date }

    fun templateById(id: String): SavedTemplate? = templates.firstOrNull { it.id == id }

    /** Adds a template, or replaces the one already saved under the same name. */
    fun withTemplate(template: SavedTemplate): CalendarDocument {
        val index = templates.indexOfFirst { it.id == template.id || it.name.equals(template.name, ignoreCase = true) }
        return copy(
            templates = if (index >= 0) {
                templates.toMutableList().also { it[index] = template }
            } else {
                templates + template
            }
        )
    }

    fun withoutTemplate(id: String): CalendarDocument = copy(templates = templates.filterNot { it.id == id })

    /**
     * This calendar and [other] as one, for two machines keeping the same file.
     *
     * The rule is per service rather than per file, which is what makes syncing safe: a whole-file
     * "newest wins" loses the Sunday somebody added on the laptop the moment the desktop saves
     * anything at all. Services and templates are keyed by id, so each one is decided on its own:
     *
     * - on one side only, and not deleted on the other → kept
     * - on both → the one with the higher [PlannedService.version]; on a tie, the later
     *   [PlannedService.updatedAt]
     * - deleted on one side → gone, unless the other side's copy outranks the deletion the same way
     *
     * The version decides, not the clock: a copy can only outrank another by being an edit of it,
     * so an old copy handed back -- by a relay replaying what it stored, or a machine restored from
     * a backup -- can never overwrite what came after it. The stamp only settles two edits made
     * independently from the same copy.
     *
     * [preferences] is deliberately not merged: the clock format and the default start time are
     * about the machine that set them, not about the plan.
     */
    fun mergedWith(other: CalendarDocument, now: Instant = Instant.now()): CalendarDocument {
        val tombstones = (deletedServices + other.deletedServices).mapValues { (id, stamp) ->
            maxOf(stamp, deletedServices[id] ?: stamp, other.deletedServices[id] ?: stamp)
        }
        val tombstoneVersions = tombstones.keys.associateWith { id ->
            maxOf(deletedVersions[id] ?: 0L, other.deletedVersions[id] ?: 0L)
        }
        val merged = (services + other.services)
            .groupBy { it.id }
            .mapNotNull { (id, both) ->
                val newest = both.maxWith(EDIT_ORDER)
                val deletedAt = tombstones[id] ?: return@mapNotNull newest
                val deletedVersion = tombstoneVersions[id] ?: 0L
                // An edit of the copy that was deleted, made without knowing about the deletion:
                // somebody went back to it, so it stays.
                val outranks = newest.version > deletedVersion ||
                    (newest.version == deletedVersion && newest.updatedAt > deletedAt)
                if (outranks) newest else null
            }
        val keptTombstones = tombstones.filterValues { it > storedInstant(now.minus(TOMBSTONE_LIFETIME)) }
        return copy(
            version = maxOf(version, other.version),
            // Ordered by id last, so the two machines produce *byte-identical* merges: a stable
            // sort otherwise keeps whatever order each side happened to hold, and two files that
            // differ only in order are two machines writing merges at each other for ever.
            services = merged.sortedWith(compareBy({ it.date }, { it.startTime }, { it.id })),
            templates = (templates + other.templates).distinctBy { it.id },
            deletedServices = keptTombstones,
            deletedVersions = tombstoneVersions.filterKeys { it in keptTombstones },
        )
    }
}

/** Adds every service in [added] at once, so a whole series is one write rather than one per week. */
fun CalendarDocument.withServices(added: List<PlannedService>): CalendarDocument =
    added.fold(this) { document, service -> document.withService(service) }

/**
 * How long a deletion is remembered.
 *
 * Long enough that a machine which has been switched off for a month still learns about it, short
 * enough that the list does not grow for ever. A machine away for longer than this brings the
 * service back, which is the failure worth having: something reappearing is noticed, something
 * vanishing is not.
 */
internal val TOMBSTONE_LIFETIME: Duration = Duration.ofDays(90)

/** Which of two copies of one service is newer: more edits first, then the later edit. */
private val EDIT_ORDER: Comparator<PlannedService> = compareBy({ it.version }, { it.updatedAt })

/** An instant as the file stores it. */
fun storedInstant(at: Instant): String = at.toString()

/**
 * This document with [at] stamped on every service that differs from [previous].
 *
 * Stamped here rather than at each mutation for the same reason the save is: there are two dozen
 * ways to change a service and every one of them would otherwise have to remember. Comparing
 * against the previous document is what keeps a stamp meaning "changed" — re-saving an untouched
 * service must not make it look newer than the copy on the other machine.
 */
fun CalendarDocument.stampingChanged(previous: CalendarDocument, at: Instant): CalendarDocument {
    val before = previous.services.associateBy { it.id }
    val stamped = services.map { service ->
        val old = before[service.id]
        if (old != null && old == service) {
            service
        } else {
            // One more edit than the copy it was made from -- see [CalendarDocument.mergedWith].
            service.copy(updatedAt = storedInstant(at), version = maxOf(service.version, old?.version ?: 0L) + 1)
        }
    }
    return if (stamped == services) this else copy(services = stamped)
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
     * How each row runs -- its own start time, repeats, what happens when it ends -- keyed by
     * [ScheduleItem.id] like [plannedSeconds], and absent for a row that is simply cued by hand.
     */
    val timing: Map<String, RowTiming> = emptyMap(),
    /**
     * Cues as an older file stored them, beside the rows rather than among them. Empty in anything
     * this version writes: they are folded into [items] on load. See [ServiceCue].
     */
    val cues: List<ServiceCue> = emptyList(),
    /** Whether this service's cues may fire. False is "planned, but do not automate". */
    val armed: Boolean = true,
    /**
     * Shared by every occurrence of a repeating service; empty for a one-off.
     *
     * The occurrences are ordinary services — each has its own run of show and can be edited on its
     * own — and the id is only what lets "all in series" find the others. There is no series record
     * to keep in step with them.
     */
    val seriesId: String = "",
    /** How the series repeats, a [ServiceRepeat] id. Blank on a one-off. */
    val repeat: String = "",
    /**
     * When this service was last changed, as an ISO-8601 instant — `2026-09-20T09:14:02Z`.
     *
     * An instant, unlike [date] and [startTime], because this one really is a point in time: it is
     * only ever compared against the same field on another machine's copy, and comparing those as
     * wall clocks would make the answer depend on whose zone they were typed in. Empty on a service
     * written before this field existed, which sorts before every real stamp — so anything edited
     * since wins over it, which is the right way round.
     */
    val updatedAt: String = "",
    val version: Long = 0L,
    /**
     * Rows that arrived from a phone and could not be matched here -- a song title not in the
     * library, a preset since deleted -- as row id to reason. Beside the list, like
     * [plannedSeconds], so a row stays a row; the run of show draws these flagged.
     */
    val unresolvedRows: Map<String, String> = emptyMap(),
) {
    fun isInSeries(): Boolean = seriesId.isNotEmpty()

    /**
     * The planned length of the whole service, counting only rows that have an estimate; a row
     * played N times counts N times.
     */
    fun plannedTotalSeconds(): Int =
        plannedSeconds.entries.sumOf { (id, seconds) -> seconds * timingOf(id).repeats.coerceAtLeast(1) }

    /** Run-of-show rows that are content rather than section headings or cues. */
    fun contentItems(): List<ScheduleItem> =
        items.filterNot { it is ScheduleItem.LabelItem || it is ScheduleItem.CueItem }

    /** The service's cues, in list order -- which `withCue` keeps as firing order. */
    fun cueRows(): List<ScheduleItem.CueItem> = items.filterIsInstance<ScheduleItem.CueItem>()

    fun timingOf(itemId: String): RowTiming = timing[itemId] ?: RowTiming.DEFAULT

    /** How many rows start on their own, cues included. */
    fun autoStartCount(): Int =
        timing.values.count { it.startsOnItsOwn() } + cueRows().size
}

/**
 * A cue as `calendar.json` stored it before cues became run-of-show rows.
 *
 * Read only. A file that still carries these has them turned into [ScheduleItem.CueItem] rows on
 * load -- see `withCuesAsRows` -- and is written back without them. Kept so an older file opens;
 * nothing new is ever written in this shape.
 */
@Serializable
data class ServiceCue(
    val id: String,
    val offsetMinutes: Int = 0,
    val absoluteTime: String = "",
    val label: String = "",
    val payload: ScheduleItem? = null,
    val action: String = CueAction.PROJECT,
    val enabled: Boolean = true,
    val plays: Int = 1,
) {
    /** The same cue as a run-of-show row. */
    fun asRow(): ScheduleItem.CueItem = ScheduleItem.CueItem(
        id = id,
        action = action,
        label = label,
        offsetMinutes = offsetMinutes,
        absoluteTime = absoluteTime,
        payload = payload,
        plays = plays,
        enabled = enabled,
    )
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

/**
 * How a repeating service recurs.
 *
 * Monthly keeps the weekday ordinal — a service on the third Sunday stays on the third Sunday —
 * because that is how churches plan; "the 20th of every month" lands on a weekday most months.
 */
enum class ServiceRepeat(val id: String) {
    NONE(""),
    WEEKLY("weekly"),
    BIWEEKLY("biweekly"),
    MONTHLY("monthly");

    companion object {
        fun from(id: String): ServiceRepeat = entries.firstOrNull { it.id == id } ?: NONE
    }
}

/**
 * A run of show saved to start new services from — the design's `Sunday Morning · Template`.
 *
 * Its rows are copied and re-keyed on every use (see `ServiceTemplate.Saved`), never shared, so
 * editing a service made from it leaves the template as it was.
 */
@Serializable
data class SavedTemplate(
    val id: String,
    val name: String,
    val startTime: String,
    val kind: String = ServiceKind.SUNDAY.id,
    val items: List<ScheduleItem> = emptyList(),
    val plannedSeconds: Map<String, Int> = emptyMap(),
    val timing: Map<String, RowTiming> = emptyMap(),
    /** As on [PlannedService]: an older file's cues, folded into [items] on load. */
    val cues: List<ServiceCue> = emptyList(),
) {
    fun contentItems(): List<ScheduleItem> =
        items.filterNot { it is ScheduleItem.LabelItem || it is ScheduleItem.CueItem }

    fun cueRows(): List<ScheduleItem.CueItem> = items.filterIsInstance<ScheduleItem.CueItem>()
}

/** Calendar-wide preferences, saved beside the services rather than in `settings.json`. */
@Serializable
data class CalendarPreferences(
    /** The start time a newly added service is created with. */
    val defaultStartTime: String = "10:00",
    /** Whether a newly added service starts armed for automation. */
    val armByDefault: Boolean = true,
    /**
     * Whether times are shown as `18:30` rather than `6:30 PM` — everywhere one is shown, and in
     * what a time field starts out holding. Starts as whatever the locale does; times are stored
     * as `HH:mm` regardless.
     */
    val use24HourClock: Boolean = localeUses24HourClock(),
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
    /**
     * Whether a service about to start loads itself into the Schedule tab -- see [ServiceAutoLoader].
     *
     * Off by default: a planner that puts things into the live Schedule on its own is not what
     * somebody expects the first time they open it.
     */
    val autoLoadService: Boolean = false,
    /**
     * How long before its first item a service loads itself, in minutes. Read only while
     * [autoLoadService] is on, and clamped to [AUTO_LOAD_LEAD_MIN]..[AUTO_LOAD_LEAD_MAX] where it
     * is used -- a file is hand-editable and a lead of 0 or 10,000 is not a setting anybody meant.
     */
    val autoLoadLeadMinutes: Int = AUTO_LOAD_LEAD_MINUTES,
    val pdfExport: PdfExportSettings = PdfExportSettings(),
) {
    /** The lead as the loader uses it, whatever the file says. */
    fun autoLoadLead(): Int = autoLoadLeadMinutes.coerceIn(AUTO_LOAD_LEAD_MIN, AUTO_LOAD_LEAD_MAX)

    /**
     * The length a newly added [item] is planned at when nothing better is known -- no measured
     * run, no clip header, nothing typed. A song takes [defaultItemSeconds] and a deck
     * [defaultSermonSeconds]; anything else starts blank, since a guess at a verse or a website
     * would be no better than no plan.
     */
    fun defaultLengthFor(item: ScheduleItem): Int? = when (item) {
        is ScheduleItem.SongItem -> defaultItemSeconds
        is ScheduleItem.PresentationItem -> defaultSermonSeconds
        else -> null
    }
}

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
