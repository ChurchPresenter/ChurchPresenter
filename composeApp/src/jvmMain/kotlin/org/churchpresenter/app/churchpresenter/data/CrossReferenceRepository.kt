package org.churchpresenter.app.churchpresenter.data

import churchpresenter.composeapp.generated.resources.Res
import kotlinx.coroutines.Dispatchers
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

    /** [refKey] → its targets, parsed at load so a click parses nothing. */
    private val index = HashMap<Int, List<CrossRef>>()

    @Volatile private var loaded = false
    @Volatile private var loading = false

    /**
     * Reads the dataset once. Subsequent calls return immediately, including while the first is
     * still in flight — the panel re-runs this on every verse selection.
     */
    suspend fun ensureLoaded() {
        if (loaded || loading) return
        loading = true
        withContext(Dispatchers.IO) {
            try {
                val file = json.decodeFromString(
                    CrossReferenceFile.serializer(), loader().decodeToString(),
                )
                for ((key, targets) in file.refs) {
                    val source = parseKey(key) ?: continue
                    index[source] = targets.split(' ').mapNotNull(::parseTarget)
                }
                loaded = true
            } finally {
                // Cleared either way: a failed read must not leave the repository permanently
                // "loading" and so permanently empty for the rest of the run.
                loading = false
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

    /** Clears the index and the load-once flags, so the next [ensureLoaded] re-reads. */
    internal fun resetForTest() {
        index.clear()
        loaded = false
        loading = false
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
