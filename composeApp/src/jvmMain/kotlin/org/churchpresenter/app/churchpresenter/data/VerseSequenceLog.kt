package org.churchpresenter.app.churchpresenter.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

private const val PACKED_REF_LENGTH = 9
private const val BOOK_DIGITS = 3
private const val CHAPTER_END = 6

/** One learned successor: a verse that has followed another, and how often. */
data class LearnedRef(
    val bookId: Int,
    val chapter: Int,
    val verse: Int,
    val count: Int,
)

@Serializable
internal data class VerseSequenceFile(
    @SerialName("v") val version: Int = 1,
    /** Packed `BBBCCCVVV` source → (packed target → how many times it followed). */
    @SerialName("p") val pairs: Map<String, Map<String, Int>> = emptyMap(),
    /** Packed target → when it was last seen, for breaking ties between equal counts. */
    @SerialName("t") val seen: Map<String, Long> = emptyMap(),
    /** The verse the previous go-live showed, and when. */
    @SerialName("l") val last: String? = null,
    @SerialName("a") val lastAt: Long = 0L,
)

/**
 * What the operator tends to show *after* a given verse, learned from their own go-lives.
 *
 * The app already counts how often each verse is presented ([StatisticsManager]), but counts
 * cannot answer "what usually comes next" — that needs the order, which nothing kept. This does,
 * in `~/.churchpresenter/verse_sequences.json`, as counted transitions rather than an event log,
 * so the file stays small and answering a query is a map lookup.
 *
 * References are canonical (KJV numbering, books 1-66), not the display numbering of whichever
 * translation happened to be loaded, so a Synodal Psalm and a KJV Psalm land on the same key and
 * a church that switches modules keeps its history.
 *
 * @param file the store. Injected so tests get their own, rather than the singleton-with-a-mutable
 * -path shape used by the recent-files objects — a constructor parameter cannot leak between tests.
 * @param clock injected for the same reason: the session boundary below is a time rule, and a test
 * that had to wait out a real 90 minutes could not exist.
 */
class VerseSequenceLog(
    private val file: File = File(
        System.getProperty("user.home"), ".churchpresenter/verse_sequences.json",
    ),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val lock = Any()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private var state: VerseSequenceFile = load()

    private fun load(): VerseSequenceFile = try {
        if (file.exists()) json.decodeFromString(VerseSequenceFile.serializer(), file.readText())
        else VerseSequenceFile()
    } catch (_: Exception) {
        // A truncated or hand-edited file costs the learned suggestions, nothing else. Starting
        // empty is strictly better than refusing to record from here on.
        VerseSequenceFile()
    }

    private fun save() {
        try {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(VerseSequenceFile.serializer(), state))
        } catch (_: Exception) {
            // Learning is a convenience; a read-only home directory must not break a go-live.
        }
    }

    /**
     * Records that this verse has just gone live, linking it to the previous one.
     *
     * Several go-lives are deliberately *not* linked — see [SESSION_GAP_MS] and [ADJACENT_SKIP].
     * In each case the verse still becomes the anchor for the next one; only the pair is dropped.
     */
    fun recordGoLive(bookId: Int, chapter: Int, verse: Int) {
        val target = packRef(bookId, chapter, verse) ?: return
        synchronized(lock) {
            val now = clock()
            val previous = state.last
            var pairs = state.pairs

            val seen = state.seen + (target to now)
            if (previous != null && shouldLink(previous, target, now)) {
                val successors = pairs[previous].orEmpty()
                pairs = pairs + (previous to capSuccessors(
                    successors + (target to (successors[target] ?: 0) + 1), seen,
                ))
            }

            val capped = capKeys(pairs)
            state = state.copy(
                pairs = capped,
                seen = capSeen(capped, seen, target),
                last = target,
                lastAt = now,
            )
            save()
        }
    }

    /**
     * The verses that usually follow this one, strongest first, or empty when nothing qualifies.
     *
     * A transition seen only once is withheld: on a first sighting there is no way to tell a habit
     * from the one time someone clicked the wrong row, and a wrong suggestion during a service
     * costs more than a missing one.
     */
    fun successors(bookId: Int, chapter: Int, verse: Int, limit: Int = MAX_SUGGESTIONS): List<LearnedRef> {
        val key = packRef(bookId, chapter, verse) ?: return emptyList()
        val (pairs, seen) = synchronized(lock) { state.pairs[key].orEmpty() to state.seen }
        return pairs.asSequence()
            .filter { it.value >= MIN_CONFIDENCE }
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }
                .thenByDescending { seen[it.key] ?: 0L }
                .thenBy { it.key })
            .take(limit)
            .mapNotNull { (
                target,
                count
            ) -> unpackRef(target)?.let { LearnedRef(it.first, it.second, it.third, count) } }
            .toList()
    }

    /** The persisted state, for tests to assert against without re-reading the file. */
    internal fun snapshot(): VerseSequenceFile = synchronized(lock) { state }

    /**
     * Whether the move from [previous] to [target] says anything about what follows what.
     *
     * Two kinds of transition are noise rather than habit:
     * - A gap longer than [SESSION_GAP_MS] means these are different services, so the last verse
     *   of one would otherwise be linked to the first of the next. The timestamp is persisted, so
     *   this holds across an app restart too — a restart is not, by itself, a new service.
     * - Reading straight on through a passage. Every sequential read would otherwise bury the
     *   panel in "the next verse", which the arrow keys already reach faster than a click.
     */
    private fun shouldLink(previous: String, target: String, now: Long): Boolean {
        if (previous == target) return false
        if (now - state.lastAt > SESSION_GAP_MS) return false
        val from = unpackRef(previous) ?: return false
        val to = unpackRef(target) ?: return false
        val sameChapter = from.first == to.first && from.second == to.second
        return !(sameChapter && kotlin.math.abs(to.third - from.third) <= ADJACENT_SKIP)
    }

    /**
     * Bounds the fan-out of one verse by dropping the successor used longest ago.
     *
     * Deliberately *not* by count, even though count is what ranks suggestions. A newcomer always
     * arrives on a count of one, so evicting the lowest count would throw it back out on every
     * sighting: it could never accumulate, and once a verse had eight established successors it
     * would be frozen for ever — a verse the operator had since started showing every single week
     * would stay invisible behind habits they had abandoned years earlier.
     *
     * Least-recently-used has no such floor. Anything still in use is refreshed each time it goes
     * live, so what falls out is what has genuinely stopped being used, which is the same thing
     * "their habits changed" means.
     *
     * Recency comes from [VerseSequenceFile.seen], which is per target rather than per pair: a
     * verse shown in any context counts as used. That is the intended reading — a verse still in
     * the rotation should not be evicted from one source's list because it is usually reached
     * from another.
     */
    private fun capSuccessors(successors: Map<String, Int>, seen: Map<String, Long>): Map<String, Int> =
        if (successors.size <= MAX_SUCCESSORS) successors
        else successors.entries
            .sortedWith(
                compareByDescending<Map.Entry<String, Int>> { seen[it.key] ?: 0L }
                    .thenByDescending { it.value }
                    .thenBy { it.key }
            )
            .take(MAX_SUCCESSORS)
            .associate { it.key to it.value }

    /**
     * Drops recency stamps for verses nothing can ask about any more.
     *
     * [VerseSequenceFile.seen] is only ever read for a *successor* — by [successors] to break ties
     * between equal counts, and by [capSuccessors] to decide what to evict. Without this it gained
     * an entry for every distinct verse ever shown and never lost one, so the one part of the file
     * [capKeys] and [capSuccessors] do not bound would grow to every verse of the Bible and be
     * re-serialised and rewritten on every single go-live.
     *
     * [current] is kept even when it is nobody's successor yet, so the verse just shown does not
     * lose the stamp it was given a line earlier.
     */
    private fun capSeen(
        pairs: Map<String, Map<String, Int>>,
        seen: Map<String, Long>,
        current: String,
    ): Map<String, Long> {
        val reachable = HashSet<String>()
        reachable.add(current)
        pairs.values.forEach { reachable.addAll(it.keys) }
        return seen.filterKeys(reachable::contains)
    }

    /** Bounds the file over years of use by dropping the least-used sources first. */
    private fun capKeys(pairs: Map<String, Map<String, Int>>): Map<String, Map<String, Int>> =
        if (pairs.size <= MAX_KEYS) pairs
        else pairs.entries.sortedWith(
            compareByDescending<Map.Entry<String, Map<String, Int>>> { it.value.values.sum() }
                .thenBy { it.key })
            .take(MAX_KEYS)
            .associate { it.key to it.value }

    companion object {
        /** Longer than any gap within a service, shorter than the gap between two of them. */
        internal const val SESSION_GAP_MS = 90L * 60L * 1000L

        /** Verses this close in the same chapter are sequential reading, not a jump. */
        internal const val ADJACENT_SKIP = 2

        /** How often a transition must have happened before it is offered as a suggestion. */
        internal const val MIN_CONFIDENCE = 2

        internal const val MAX_SUCCESSORS = 8
        internal const val MAX_KEYS = 2000
        internal const val MAX_SUGGESTIONS = 5
    }
}

/** `(43, 3, 16)` → `"043003016"`, or null when a component will not fit three digits. */
internal fun packRef(bookId: Int, chapter: Int, verse: Int): String? {
    if (bookId !in 1..999 || chapter !in 1..999 || verse !in 1..999) return null
    return "%03d%03d%03d".format(bookId, chapter, verse)
}

/** The inverse of [packRef]; null for anything that is not nine digits. */
internal fun unpackRef(packed: String): Triple<Int, Int, Int>? {
    if (packed.length != PACKED_REF_LENGTH || !packed.all { it.isDigit() }) return null
    return Triple(
        packed.substring(0, BOOK_DIGITS).toInt(),
        packed.substring(BOOK_DIGITS, CHAPTER_END).toInt(),
        packed.substring(CHAPTER_END).toInt(),
    )
}
