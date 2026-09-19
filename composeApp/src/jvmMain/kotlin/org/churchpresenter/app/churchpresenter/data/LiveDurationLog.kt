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

    /** The median of what [item] has taken, in seconds, or null until it has been seen enough. */
    fun median(item: ScheduleItem): Int? = medianOf(readings[durationKey(item) ?: return null])

    /**
     * [item] is on screen as of [at]; whatever was on screen before it stops being timed.
     *
     * Called from every path that puts a row live -- the operator's click, a cue, a remote -- so
     * what is measured is what the congregation saw, not what any one of them did.
     */
    fun wentLive(item: ScheduleItem, at: Instant = Instant.now()) {
        close(at)
        liveKey = durationKey(item)
        liveSince = at
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
         * Identity rather than row id, and deliberately not [ScheduleItem.displayText]: a song
         * renamed or a folder with another picture in it is still the same thing. Null for what
         * has no stable identity to learn about -- a heading, a cue, a one-off verse range.
         */
        fun durationKey(item: ScheduleItem): String? = when (item) {
            is ScheduleItem.SongItem -> "song:${item.songbook}:${item.songNumber}:${item.songId}"
            is ScheduleItem.MediaItem -> "media:${item.mediaUrl}"
            is ScheduleItem.PictureItem -> "pictures:${item.folderPath}"
            is ScheduleItem.PresentationItem -> "deck:${item.filePath}"
            is ScheduleItem.SceneItem -> "scene:${item.sceneId}"
            is ScheduleItem.WebsiteItem -> "web:${item.url}"
            is ScheduleItem.LowerThirdItem -> "lower:${item.presetId}"
            else -> null
        }

        /** The middle reading, or the lower of the middle two. Null until there is one to give. */
        fun medianOf(readings: List<Int>?): Int? {
            if (readings.isNullOrEmpty()) return null
            val sorted = readings.sorted()
            return sorted[(sorted.size - 1) / 2]
        }
    }
}
