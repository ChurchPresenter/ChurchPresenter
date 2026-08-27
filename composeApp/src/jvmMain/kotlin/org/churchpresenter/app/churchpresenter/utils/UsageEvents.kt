package org.churchpresenter.app.churchpresenter.utils

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Something an operator actually did during a service, as opposed to something they configured.
 *
 * A setting says what a church *could* do — a second language selected, a tab left visible. An
 * event only exists because the thing happened, which is the difference between "bilingual songs
 * are switched on" and "bilingual songs are being sung".
 */
enum class UsageEvent(
    /**
     * The name this event is sent under. Spelled out rather than derived from the constant, so
     * renaming the constant cannot quietly change the wire contract the server counts on.
     */
    val param: String,
) {
    /** A song carrying a second language went live on an output that shows that language. */
    SONG_DUAL_LANGUAGE("songDualLanguage"),

    /** A passage went live with two or more translations on screen at once. */
    BIBLE_MULTI_TRANSLATION("bibleMultiTranslation"),

    /** A Blackmagic DeckLink output was opened on real hardware. Once per run — see [UsageEventStore.recordOncePerRun]. */
    DECKLINK_OUTPUT("decklinkOutput"),

    /**
     * An NDI receiver actually connected to one of this app's NDI sources. Once per run.
     *
     * The receiver, not the sender: NDI keeps announcing an unwatched source, so an output that is
     * merely switched on says nothing. This counts the services where something on the network took
     * the feed, which is the same thing [DECKLINK_OUTPUT] counts for SDI.
     */
    NDI_OUTPUT("ndiOutput"),

    /** A Browser Source client actually streamed frames from an output. Once per run. */
    BROWSER_SOURCE_OUTPUT("browserSourceOutput"),

    /** A phone or tablet connected to the companion server. Once per run. */
    MOBILE_APP_CONNECTED("mobileAppConnected"),

    /** A plan was imported from Planning Center. */
    PLANNING_CENTER_IMPORT("planningCenterImport"),

    /** A Companion Satellite surface reached CONNECTED. Once per run. */
    COMPANION_SATELLITE("companionSatellite"),

    /** A song carrying chords went live with the chord chart drawn on a stage monitor. */
    SONG_CHORD_CHART("songChordChart"),

    /** A passage went live with different outputs showing different translations. */
    BIBLE_SPLIT_SCREEN("bibleSplitScreen"),

    /** A song went live with different outputs showing different languages. */
    SONG_SPLIT_SCREEN("songSplitScreen"),

    /** A new lower third was saved out of the built-in Lottie generator. */
    LOWER_THIRD_GENERATED("lowerThirdGenerated"),

    /** A translation was downloaded and installed from the in-app catalogue. */
    BIBLE_INSTALLED("bibleInstalled"),

    /**
     * Content reached an audience output for the first time on this install — a second display or
     * a DeckLink device, not just the operator's preview. Recorded once ever, so its absence is the
     * signal: an install that pings week after week and never sends this never got working.
     */
    FIRST_LIVE_ON_SCREEN("firstLiveOnScreen"),
}

@Serializable
internal data class UsageEventLog(
    /** event name -> how many times it has ever happened on this install. */
    val total: Map<String, Int> = emptyMap(),
    /** event name -> how much of [total] has already been delivered to the server. */
    val reported: Map<String, Int> = emptyMap(),
    /**
     * How long the previous run lasted, in whole minutes; 0 once reported or never recorded.
     *
     * Written at shutdown and read at the next launch, because the only moment the app talks to the
     * server is startup — by the time a session's length is known, the ping for it has long gone. A
     * run that ends in a crash or a hard kill records nothing and is simply not counted.
     */
    val lastSessionMinutes: Int = 0,
)

internal fun UsageEventLog.recording(event: UsageEvent): UsageEventLog =
    copy(total = total + (event.name to (total[event.name] ?: 0) + 1))

/** What has happened but not yet been reported, per event; events with nothing new are absent. */
internal fun UsageEventLog.unreported(): Map<UsageEvent, Int> =
    UsageEvent.entries.associateWith { (total[it.name] ?: 0) - (reported[it.name] ?: 0) }
        .filterValues { it > 0 }

internal fun UsageEventLog.markingReported(counts: Map<UsageEvent, Int>): UsageEventLog =
    copy(reported = reported + counts.map { (event, n) -> event.name to (reported[event.name] ?: 0) + n })

/**
 * Counts of [UsageEvent]s, kept in a small JSON file next to the other app state.
 *
 * Two numbers per event — how many have happened, and how many have been delivered — rather than a
 * log of occurrences. That makes the difference the only thing ever sent, so the server can simply
 * add up what arrives: a launch reports what is new since the last launch that got through, and an
 * event that happened while the network was down waits rather than being lost or double counted.
 * Nothing about the occurrence itself is stored — no song, no title, no language, no timestamp —
 * only that one happened.
 *
 * @param fileProvider resolved on every call rather than once, so it always reflects the current
 * `user.home`. A file latched at construction would be captured from whichever temp directory a
 * test happened to set first and then go on writing there after it was deleted.
 */
open class UsageEventStore(private val fileProvider: () -> File) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val lock = Any()

    /**
     * Records one occurrence of [event].
     *
     * Written on the calling thread: events are human-paced — a song going live — and the file is a
     * handful of integers, so the write costs less than handing it to another thread would, and
     * callers (and their tests) can read the count back immediately afterwards.
     */
    fun record(event: UsageEvent): Unit = synchronized(lock) { save(load().recording(event)) }

    /**
     * Records [event] at most once for the lifetime of this process.
     *
     * For things that are a *connection* rather than an action: a phone reconnects whenever its
     * screen sleeps and a DeckLink output reopens whenever the display config changes, so counting
     * every occurrence would measure network flakiness. Counting one per run makes the number
     * "services where this was connected", which is the question actually being asked.
     */
    fun recordOncePerRun(event: UsageEvent): Unit = synchronized(lock) {
        if (recordedThisRun.add(event)) save(load().recording(event))
    }

    private val recordedThisRun = mutableSetOf<UsageEvent>()

    /**
     * Records [event] only if it has never been recorded on this install.
     *
     * For a milestone rather than an activity — "this install has managed to do X at all" — where
     * the count is meaningless and only the first time says anything.
     */
    fun recordOncePerInstall(event: UsageEvent): Unit = synchronized(lock) {
        val log = load()
        if ((log.total[event.name] ?: 0) == 0) save(log.recording(event))
    }

    /** Stores how long the run that is now ending lasted. Called from a shutdown hook. */
    fun recordSessionMinutes(minutes: Int): Unit = synchronized(lock) {
        if (minutes > 0) save(load().copy(lastSessionMinutes = minutes))
    }

    /** The previous run's length in minutes, or 0 if unknown or already reported. */
    fun lastSessionMinutes(): Int = synchronized(lock) { load().lastSessionMinutes }

    /** Clears the previous run's length once it has been delivered, so it is reported once. */
    fun clearSessionMinutes(): Unit = synchronized(lock) { save(load().copy(lastSessionMinutes = 0)) }

    /** What has happened since the last [markReported], per event. */
    fun unreported(): Map<UsageEvent, Int> = synchronized(lock) { load().unreported() }

    /**
     * Marks [counts] as delivered, so the next launch reports only what happened after them.
     *
     * Call only once the server has actually taken them: anything not marked is simply reported
     * again next time, which is the behaviour a failed send needs.
     */
    fun markReported(counts: Map<UsageEvent, Int>): Unit =
        synchronized(lock) { if (counts.isNotEmpty()) save(load().markingReported(counts)) }

    private fun load(): UsageEventLog = try {
        val file = fileProvider()
        if (file.exists()) json.decodeFromString(UsageEventLog.serializer(), file.readText())
        else UsageEventLog()
    } catch (_: Exception) {
        UsageEventLog()
    }

    private fun save(log: UsageEventLog) {
        try {
            val file = fileProvider()
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(UsageEventLog.serializer(), log))
        } catch (_: Exception) {
            // Usage counting must never break a live service — a read-only home directory or a
            // full disk simply means this install reports nothing.
        }
    }
}

/** The app-wide store, in `~/.churchpresenter/` alongside the crash reports and Q&A state. */
object UsageEvents : UsageEventStore({
    File(System.getProperty("user.home"), ".churchpresenter/usage-events.json")
})
