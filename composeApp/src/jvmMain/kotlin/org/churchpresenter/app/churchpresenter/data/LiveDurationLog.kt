package org.churchpresenter.app.churchpresenter.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.churchpresenter.core.models.io.writeTextAtomically
import org.churchpresenter.core.models.schedule.ScheduleItem
import java.io.File
import java.time.Instant

/**
 * How long each thing actually stays on screen, so a plan can be timed from what happened rather
 * than from a guess.
 *
 * Nobody is asked to run a stopwatch: every row that goes live is timed until the next one does or
 * the outputs blank, and the **median** of what has been seen is offered as that item's length.
 * The median rather than the mean because one Sunday where the song was left up through the
 * offering should not move the number much.
 *
 * Two rules keep the numbers honest:
 * - Anything under [MIN_SECONDS] is thrown away. Stepping through a service to check it, or
 *   clicking the wrong row and correcting it, are not measurements of anything.
 * - Only [KEEP_PER_ITEM] most recent readings are kept per item, so a song sung differently this
 *   year is not held to how it went two years ago.
 *
 * Keyed by what the item *is* -- a song's number and book, a file's path -- never by row id: the
 * point is that this week's row inherits what last week's row of the same thing took.
 */
class LiveDurationLog(private val file: File) {

    @Serializable
    private data class Stored(val seconds: Map<String, List<Int>> = emptyMap())

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val readings: MutableMap<String, MutableList<Int>> = load()

    private var liveKey: String? = null
    private var liveSince: Instant? = null

    /**
     * What is on screen, for telling one thing from another -- see [showing]. The duration key
     * where there is one, else the row's own text, so a verse or a timer is still distinguishable
     * from the picture folder that was up before it.
     */
    private var liveIdentity: String? = null

    /**
     * Whether [item] is what is on screen now, whichever path put it there.
     *
     * The automation engine asks this to know whether the operator has taken over: it remembers
     * what it last projected, and if the screen now shows something else, a hand did that. Judged
     * by identity rather than row id because the engine's song cue is presented by the Songs tab
     * as a library song, not as the schedule row it came from.
     */
    fun showing(item: ScheduleItem): Boolean = liveIdentity != null && liveIdentity == identityOf(item)

    /** The median of what [item] has taken, in seconds, or null until it has been seen enough. */
    fun median(item: ScheduleItem): Int? = medianOf(readings[durationKey(item) ?: return null])

    /**
     * [item] is on screen as of [at]; whatever was on screen before it stops being timed.
     *
     * Called from every path that puts a row live -- the operator's click, a cue, a remote -- so
     * what is measured is what the congregation saw, not what any one of them did.
     */
    fun wentLive(item: ScheduleItem, at: Instant = Instant.now()) {
        val key = durationKey(item)
        // The same thing again -- a section clicked, a row re-sent -- is still the same thing on
        // screen: the reading keeps running rather than being cut into pieces too short to keep.
        if (key != null && key == liveKey) return
        close(at)
        liveKey = key
        liveSince = at
        liveIdentity = identityOf(item)
    }

    /** The outputs went black, or the live content stopped being a schedule row. */
    fun wentBlank(at: Instant = Instant.now()) {
        close(at)
    }

    private fun close(at: Instant) {
        val key = liveKey
        val since = liveSince
        liveKey = null
        liveSince = null
        liveIdentity = null
        if (key == null || since == null) return
        val seconds = (at.epochSecond - since.epochSecond).toInt()
        if (seconds < MIN_SECONDS || seconds > MAX_SECONDS) return
        val list = readings.getOrPut(key) { mutableListOf() }
        list += seconds
        while (list.size > KEEP_PER_ITEM) list.removeAt(0)
        save()
    }

    private fun load(): MutableMap<String, MutableList<Int>> {
        if (!file.isFile) return mutableMapOf()
        val stored = runCatching { json.decodeFromString(Stored.serializer(), file.readText()) }.getOrNull()
        return stored?.seconds.orEmpty().mapValues { it.value.toMutableList() }.toMutableMap()
    }

    private fun save() {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeTextAtomically(json.encodeToString(Stored.serializer(), Stored(readings)))
        }
    }

    internal companion object {
        /** Under half a minute is somebody checking the service, not the service. */
        const val MIN_SECONDS = 30

        /** Past an hour the row was almost certainly left up after the service ended. */
        const val MAX_SECONDS = 3600

        const val KEEP_PER_ITEM = 12

        /**
         * What an item is, for the purpose of "how long does this usually take".
         *
         * Identity rather than row id, and deliberately not [ScheduleItem.displayText]: a folder
         * with another picture in it is still the same thing. Null for what has no stable
         * identity to learn about -- a heading, a cue, a one-off verse range.
         *
         * A song is its book, its number *and its title*. The number alone is not an identity:
         * a real library repeats a number within one book (this developer's does, six times),
         * and `songId` is built from the number, so without the title three different songs
         * shared one reading. The cost is that renaming a song starts its history over, which
         * is rarer than the collision and recovers by itself after a few Sundays.
         */
        fun durationKey(item: ScheduleItem): String? = when (item) {
            is ScheduleItem.SongItem ->
                "song:${item.songbook}:${item.songNumber}:${item.songId}:${item.title.trim().lowercase()}"
            is ScheduleItem.MediaItem -> "media:${item.mediaUrl}"
            is ScheduleItem.PictureItem -> "pictures:${item.folderPath}"
            is ScheduleItem.PresentationItem -> "deck:${item.filePath}"
            is ScheduleItem.SceneItem -> "scene:${item.sceneId}"
            is ScheduleItem.WebsiteItem -> "web:${item.url}"
            is ScheduleItem.LowerThirdItem -> "lower:${item.presetId}"
            else -> null
        }

        /** [durationKey] where there is one, else the row's text -- see [LiveDurationLog.showing]. */
        fun identityOf(item: ScheduleItem): String = durationKey(item) ?: "text:${item.displayText}"

        /** The middle reading, or the lower of the middle two. Null until there is one to give. */
        fun medianOf(readings: List<Int>?): Int? {
            if (readings.isNullOrEmpty()) return null
            val sorted = readings.sorted()
            return sorted[(sorted.size - 1) / 2]
        }
    }
}

/**
 * A library song as the row that identifies it, so what is measured in the Songs tab and what is
 * measured in the Schedule are the same thing.
 *
 * Only the identifying fields matter here -- [LiveDurationLog.durationKey] reads the songbook and
 * number, never the row id.
 */
fun org.churchpresenter.core.models.songs.SongItem.asDurationRow(): ScheduleItem.SongItem =
    ScheduleItem.SongItem(
        id = songId,
        songNumber = number.toIntOrNull() ?: 0,
        title = title,
        songbook = songbook,
        songId = songId,
    )
