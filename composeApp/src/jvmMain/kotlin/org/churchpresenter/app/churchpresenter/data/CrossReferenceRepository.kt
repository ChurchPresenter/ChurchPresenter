package org.churchpresenter.app.churchpresenter.data

import churchpresenter.composeapp.generated.resources.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi

/** One scripture reference, in canonical numbering (books 1-39 OT, 40-66 NT). */
data class CrossRef(
    val bookId: Int,
    val chapter: Int,
    val verse: Int,
    /** Last verse of a contiguous run within [chapter], or null for a single verse. */
    val endVerse: Int? = null,
)

@Serializable
internal data class CrossReferenceFile(
    /** Format version, so a future change is detected rather than silently mis-parsed. */
    @SerialName("v") val version: Int = 1,
    /** Packed `BBBCCCVVV` source verse → its targets, space separated. */
    @SerialName("r") val refs: Map<String, String> = emptyMap(),
)

/**
 * The bundled Treasury of Scripture Knowledge cross-references, keyed by canonical verse.
 *
 * The data is produced by `scripts/build_cross_references.py` and covers 27,847 of the KJV's
 * 31,102 verses — TSK genuinely has nothing to say about parts of the genealogies, so an empty
 * result is a normal answer rather than a sign the load failed.
 *
 * Loading is lazy and once-only, following [InterlinearRepository]: the file is ~3 MB and most
 * sessions never open the panel that needs it, so nothing is read until something asks.
 *
 * @param loader reads the raw bytes. Defaulted to the bundled resource and overridden in tests
 * with a fake — an injected lambda rather than a mutable singleton field, so tests need no
 * teardown and cannot leak a fixture into whatever runs next in the same JVM.
 */
class CrossReferenceRepository(
    private val loader: suspend () -> ByteArray = { defaultLoader() },
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * [refKey] → its targets, parsed at load so a click parses nothing.
     *
     * Replaced wholesale when a load finishes rather than filled in place, and read through a
     * `@Volatile` field, so [forVerse] either sees the previous contents or the complete new ones —
     * never a map another thread is still writing into.
     */
    @Volatile private var index: Map<Int, List<CrossRef>> = emptyMap()

    @Volatile private var loaded = false

    /** Serialises loads so a second caller waits for the first rather than racing it. */
    private val loadMutex = Mutex()

    /**
     * Reads the dataset once; later calls return immediately.
     *
     * A caller arriving while a load is in flight **waits for it** rather than returning early.
     * Returning early would hand that caller an index that is not there yet — the panel resolves
     * its rows the moment this returns, so it would render "no cross references" for a verse that
     * has them, and nothing would re-run to correct it until the selection changed again.
     *
     * A failed or cancelled load publishes nothing and leaves [loaded] false, so the next call
     * tries again from scratch — a half-parsed index is never visible.
     */
    suspend fun ensureLoaded() {
        if (loaded) return
        loadMutex.withLock {
            if (loaded) return
            withContext(Dispatchers.IO) {
                val file = json.decodeFromString(
                    CrossReferenceFile.serializer(), loader().decodeToString(),
                )
                val parsed = HashMap<Int, List<CrossRef>>(file.refs.size)
                for ((key, targets) in file.refs) {
                    val source = parseKey(key) ?: continue
                    parsed[source] = targets.split(' ').mapNotNull(::parseTarget)
                }
                index = parsed
                loaded = true
            }
        }
    }

    /**
     * The references for one verse, **as a copy** — the same lesson as
     * [InterlinearRepository.getVersesForEntry]: callers hold the result across recompositions,
     * so they must never get a live view of an index this class owns.
     */
    fun forVerse(bookId: Int, chapter: Int, verse: Int): List<CrossRef> {
        // The key packs three fields into three decimal digits each, so a component outside that
        // range would alias onto a different verse — 1:1001 and 2:1 would collide. No real
        // reference comes close (Psalm 119 is the longest chapter at 176 verses), but this is
        // reachable from the UI with whatever the selected translation reports, so it is refused
        // rather than answered wrongly.
        if (chapter !in 1..999 || verse !in 1..999 || bookId !in 1..999) return emptyList()
        return index[refKey(bookId, chapter, verse)]?.toList() ?: emptyList()
    }

    /** Clears the index and the load-once flag, so the next [ensureLoaded] re-reads. */
    internal fun resetForTest() {
        index = emptyMap()
        loaded = false
    }

    companion object {
        internal const val RESOURCE_PATH = "files/bible/cross_references.json"

        @OptIn(ExperimentalResourceApi::class)
        private suspend fun defaultLoader(): ByteArray = Res.readBytes(RESOURCE_PATH)
    }
}

/** The app-wide instance. The dataset is immutable, so one copy serves every caller. */
internal val sharedCrossReferences: CrossReferenceRepository by lazy { CrossReferenceRepository() }

private fun refKey(bookId: Int, chapter: Int, verse: Int) =
    bookId * 1_000_000 + chapter * 1_000 + verse

/** `"043003016"` → its index key, or null if the key is not the expected nine digits. */
private fun parseKey(key: String): Int? {
    if (key.length != 9 || !key.all { it.isDigit() }) return null
    return refKey(key.substring(0, 3).toInt(), key.substring(3, 6).toInt(), key.substring(6).toInt())
}

/** `"019033006-009"` → `CrossRef(19, 33, 6, 9)`; `"045005008"` → a single verse. */
private fun parseTarget(target: String): CrossRef? {
    val start = target.substringBefore('-')
    if (start.length != 9 || !start.all { it.isDigit() }) return null
    val end = target.substringAfter('-', "").takeIf { it.isNotEmpty() }?.toIntOrNull()
    return CrossRef(
        bookId = start.substring(0, 3).toInt(),
        chapter = start.substring(3, 6).toInt(),
        verse = start.substring(6).toInt(),
        endVerse = end,
    )
}

/**
 * A reference as the panel shows it: `"Rom 5:8"`, or `"Ps 33:6-9"` for a run.
 *
 * Takes the already-resolved [abbrev] rather than a book id, so it stays a pure function that
 * tests can exercise without a Compose string-resource lookup (which throws headless).
 */
internal fun formatCrossRefLabel(abbrev: String, chapter: Int, verse: Int, endVerse: Int?): String =
    if (endVerse != null) "$abbrev $chapter:$verse-$endVerse" else "$abbrev $chapter:$verse"

/**
 * Flattens the per-verse references of a selected range into one list, in order, deduped.
 *
 * A multi-verse selection has a reference list per verse and TSK gives each of them up to 16, so
 * a long passage would produce a scroll of near-duplicates. Interleaving by position instead of
 * concatenating means a 3-verse selection shows each verse's strongest references before any of
 * their weaker ones, so the head of the list stays useful whatever the range length.
 */
internal fun mergeCrossRefs(perVerse: List<List<CrossRef>>, limit: Int): List<CrossRef> {
    val merged = ArrayList<CrossRef>(limit)
    val seen = HashSet<CrossRef>()
    val deepest = perVerse.maxOfOrNull { it.size } ?: 0
    for (position in 0 until deepest) {
        for (refs in perVerse) {
            val ref = refs.getOrNull(position) ?: continue
            if (seen.add(ref)) {
                merged.add(ref)
                if (merged.size == limit) return merged
            }
        }
    }
    return merged
}

/** A passage that several verses of a reading point at, and how many of them do. */
data class PassageRef(
    val bookId: Int,
    val chapter: Int,
    val startVerse: Int,
    val endVerse: Int?,
    /** How many of the read verses reference this passage — the reason it is ranked where it is. */
    val sourceCount: Int,
)

/**
 * The passages a whole reading points at, strongest first.
 *
 * A preacher reading Matthew 1:1-10 and then moving to another gospel does not continue from the
 * *last verse* — they go wherever the same ground is covered. Listing each verse's references
 * separately answers the wrong question at that moment: what is wanted is where the passage as a
 * whole points, with whatever several of its verses agree on at the top.
 *
 * So references are grouped by target book **and chapter** — that grouping is what turns thirty
 * scattered verse references into "Luke 3" as somewhere to go — and each group is labelled with
 * the span from its lowest target verse to its highest.
 *
 * [PassageRef.sourceCount] counts the *read* verses that point into a chapter, not the references
 * landing there, so one verse citing six verses of Luke 3 does not outrank six verses that each
 * cite it once. Agreement across the passage is the signal; a single verse's enthusiasm is not.
 *
 * @param perVerse one list of references per verse of the reading, in reading order.
 */
internal fun aggregateCrossRefs(perVerse: List<List<CrossRef>>, limit: Int): List<PassageRef> {
    class Group {
        var start = Int.MAX_VALUE
        var end = 0
        val sources = HashSet<Int>()
    }

    val groups = LinkedHashMap<Long, Group>()
    perVerse.forEachIndexed { sourceIndex, refs ->
        for (ref in refs) {
            val group = groups.getOrPut(ref.bookId.toLong() * 1_000 + ref.chapter) { Group() }
            group.start = minOf(group.start, ref.verse)
            group.end = maxOf(group.end, ref.endVerse ?: ref.verse)
            group.sources.add(sourceIndex)
        }
    }

    return groups.entries
        .map { (key, group) ->
            val bookId = (key / 1_000).toInt()
            val chapter = (key % 1_000).toInt()
            PassageRef(
                bookId = bookId,
                chapter = chapter,
                startVerse = group.start,
                // A single verse keeps a null end, so it labels as "Luke 3:23" not "Luke 3:23-23".
                endVerse = group.end.takeIf { it > group.start },
                sourceCount = group.sources.size,
            )
        }
        .sortedWith(
            compareByDescending<PassageRef> { it.sourceCount }
                .thenBy { it.bookId }
                .thenBy { it.chapter }
        )
        .take(limit)
}
