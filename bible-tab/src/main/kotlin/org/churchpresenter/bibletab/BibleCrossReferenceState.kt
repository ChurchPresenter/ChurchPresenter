package org.churchpresenter.bibletab

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

internal const val CROSS_REF_RANGE_ANCHORS = 3

internal const val CROSS_REF_STATIC_LIMIT = 8

/**
 * Everything the cross-reference column and popover are looking at, in one object.
 *
 * The column keeps more state than it looks: which verses it is describing, whether those came
 * from a go-live or from browsing, the run of verses read so far, and a pin recording where a
 * click in the column has just sent the selection. Held together here so `BibleTab` passes one
 * object rather than eleven, and so the effects that maintain them sit beside the fields.
 */
internal class BibleCrossReferenceState {

    /** The docked column's rows: what this operator usually shows next, then what TSK points at. */
    var rows by mutableStateOf<List<CrossRefRow>>(emptyList())
    var selectedIndex by mutableStateOf(-1)

    /**
     * How many references each verse of the open chapter has, by its number in this module.
     *
     * Drives the link chip at the end of a verse: a verse absent from this map has nothing to
     * offer and gets no chip, which is a normal answer — TSK has nothing to say about parts of the
     * genealogies.
     */
    var counts by mutableStateOf<Map<Int, Int>>(emptyMap())

    /** Which row of the verse list has its popover open, that verse, and how it heads itself. */
    var popoverIndex by mutableStateOf(-1)
    var popoverAnchor by mutableStateOf<Triple<Int, Int, Int>?>(null)
    var popoverLabel by mutableStateOf("")
    var popoverRows by mutableStateOf<List<CrossRefRow>>(emptyList())

    /**
     * Where a click in this column has just sent the selection.
     *
     * While the selection is there the column keeps showing the passage it was describing, rather
     * than re-resolving around the verse it just sent you to. Two reasons, and the second is not
     * optional: exploring several of a verse's references in turn is the point of the column, and
     * a list that rebuilt on the first click would destroy the row under the pointer — making the
     * second click of a double-click land on whatever row replaced it, so "double-click to go
     * live" could never work here at all.
     */
    var navigatedTo by mutableStateOf<Triple<Int, Int, Int>?>(null)

    /**
     * Bumped when the operator picks a starting point themselves, to re-resolve the column even
     * though nothing in the selection changed — clicking the very verse the column just sent you
     * to has to bring back that verse's own references rather than leave the previous list up.
     */
    var anchorEpoch by mutableStateOf(0)

    /** The canonical verses the column is describing. */
    var anchors by mutableStateOf<List<Triple<Int, Int, Int>>>(emptyList())

    /** Whether those came from going live, as opposed to from browsing. */
    var anchorIsLive by mutableStateOf(false)

    /**
     * The consecutive verses taken live in one chapter — the passage currently being read.
     *
     * A preacher reads down a passage and then moves to another book, and will not continue from
     * the verse they stopped on. Once two verses have been read in sequence the column pools their
     * references instead of describing the last one alone.
     */
    var run by mutableStateOf<List<Triple<Int, Int, Int>>>(emptyList())

    /**
     * Whether the column is describing a passage being read rather than a single verse.
     *
     * Both conditions matter: a run only means something while the anchor is still the live
     * reading, so browsing away shows that verse's own references without discarding the run.
     */
    val passageMode: Boolean get() = anchorIsLive && run.size > 1

    /** The span of the passage being read, e.g. "1:1-10", or null when describing one verse. */
    val passageSpan: String?
        get() = if (passageMode) "${run.first().second}:${run.first().third}-${run.last().third}" else null

    /**
     * Points the column at a verse that has just gone live, extending the passage being read.
     *
     * The run continues while the reading moves forward through one chapter, and starts over on
     * any jump — another book, another chapter, or back up this one — which is the moment the
     * passage has been left behind.
     */
    fun anchorLiveVerse(ref: Triple<Int, Int, Int>) {
        val previous = run.lastOrNull()
        val continues = previous != null &&
            previous.first == ref.first && previous.second == ref.second && ref.third > previous.third
        run = if (continues) run + ref else listOf(ref)
        anchors = listOf(ref)
        anchorIsLive = true
        navigatedTo = null
    }

    /** Picking a verse is a new starting point, so the column follows again even if it is this one. */
    fun restartFrom() {
        navigatedTo = null
        anchorEpoch++
    }

    fun closePopover() {
        popoverIndex = -1
        popoverAnchor = null
    }

    /** Following a reference leaves the verse the popover was opened from, so it goes with it. */
    fun followed(row: CrossRefRow) {
        navigatedTo = Triple(row.bookId, row.chapter, row.verse)
        closePopover()
    }
}

/**
 * Builds the state above and keeps it current.
 *
 * Takes the three lookups it needs as functions rather than the view model itself, so nothing here
 * holds one and the whole thing can be driven from plain values.
 */
@Composable
internal fun rememberBibleCrossReferenceState(
    available: Boolean,
    panelDocked: Boolean,
    repository: CrossReferenceRepository,
    fallbackAbbreviations: List<String>,
    selectedBookIndex: Int,
    selectedChapter: Int,
    selectedVerseIndex: Int,
    verses: List<String>,
    verseSelectionToken: Int,
    /** The module every label and preview is resolved against — the instance, not the book list. */
    loadedModule: Any?,
    moduleRefFor: (bookId: Int, chapter: Int, verse: Int) -> BibleViewModel.ModuleRef?,
    canonicalRefForDisplay: (bookIndex: Int, chapter: Int, verse: Int) -> Triple<Int, Int, Int?>?,
    selectedVerseNumbers: () -> List<Int>,
    successors: (bookId: Int, chapter: Int, verse: Int) -> List<LearnedRef>,
): BibleCrossReferenceState {
    val state = remember { BibleCrossReferenceState() }

    fun row(bookId: Int, chapter: Int, verse: Int, endVerse: Int?, learned: Boolean, count: Int = 0) =
        crossRefRow(
            CrossRefRowSpec(moduleRefFor, fallbackAbbreviations, bookId, chapter, verse, endVerse, learned, count),
        )

    // Follow the browse selection, for every path that moves it — the verse list, the schedule,
    // the Companion API, auto-follow. This does NOT clear the run: looking ahead in the verse list
    // while a passage is being read should not throw away what has been read.
    //
    // [verses] is a key because at first composition the module has not loaded: the opening
    // selection is already Genesis 1:1 but there is no verse text to read a number off and no
    // index to map it to a canonical reference, so the anchor comes out empty.
    LaunchedEffect(
        selectedBookIndex, selectedChapter, selectedVerseIndex, verses,
        verseSelectionToken, state.anchorEpoch, loadedModule,
    ) {
        val selectedNumbers = selectedVerseNumbers().ifEmpty {
            listOfNotNull(verses.getOrNull(selectedVerseIndex)?.let(::verseNumberOf))
        }
        // TSK is per verse, so a long passage would produce a scroll of near-duplicates. Three
        // verses is enough for the head of the list to stay useful without the panel churning on
        // every shift-click.
        state.anchors = selectedNumbers.take(CROSS_REF_RANGE_ANCHORS).mapNotNull { number ->
            canonicalRefForDisplay(selectedBookIndex, selectedChapter, number)
                ?.let { (book, chapter, verse) -> verse?.let { Triple(book, chapter, it) } }
        }
        state.anchorIsLive = false
    }

    // Resolve the column's contents. Keyed on the anchor, so a fast arrow-key scroll cancels the
    // in-flight resolution rather than queueing one per verse.
    LaunchedEffect(
        available, panelDocked, state.anchors, state.passageMode, state.run,
        state.anchorEpoch, loadedModule, fallbackAbbreviations,
    ) {
        if (!available || !panelDocked || state.anchors.isEmpty()) {
            state.rows = emptyList()
            state.navigatedTo = null
            return@LaunchedEffect
        }
        // Sitting on the verse this column just sent us to: leave the list, and the highlight, be.
        if (state.anchors.size == 1 && state.anchors.first() == state.navigatedTo) return@LaunchedEffect
        state.navigatedTo = null

        // Anchored on the verse most recently reached, matching what a go-live records, so what is
        // asked for and what was written use the same key.
        val learned = state.anchors.first()
            .let { (book, chapter, verse) -> successors(book, chapter, verse) }
            .map { row(it.bookId, it.chapter, it.verse, null, learned = true) }

        repository.ensureLoaded()
        val sources = if (state.passageMode) state.run else state.anchors
        val perVerse = sources.map { (book, chapter, verse) -> repository.forVerse(book, chapter, verse) }
        val references = if (state.passageMode) {
            aggregateCrossRefs(perVerse, limit = CROSS_REF_STATIC_LIMIT).map {
                row(it.bookId, it.chapter, it.startVerse, it.endVerse, learned = false, count = it.sourceCount)
            }
        } else {
            mergeCrossRefs(perVerse, limit = CROSS_REF_STATIC_LIMIT).map {
                row(it.bookId, it.chapter, it.verse, it.endVerse, learned = false)
            }
        }

        // A reference already offered as a habit is not repeated as a bare cross-reference.
        val learnedKeys = learned.map { Triple(it.bookId, it.chapter, it.verse) }.toSet()
        state.rows = learned + references.filter { Triple(it.bookId, it.chapter, it.verse) !in learnedKeys }
        state.selectedIndex = -1
    }

    // How many references each verse of the open chapter carries. One indexed lookup per verse of
    // one chapter, redone only when the chapter or the module changes — cheap enough to run for
    // every chapter that is opened, which is what lets the chip say how much is there before
    // anything is clicked.
    LaunchedEffect(available, selectedBookIndex, selectedChapter, verses, loadedModule, repository) {
        if (!available) {
            state.counts = emptyMap()
            return@LaunchedEffect
        }
        repository.ensureLoaded()
        state.counts = buildMap {
            verses.forEach { line ->
                val number = verseNumberOf(line) ?: return@forEach
                val canonical = canonicalRefForDisplay(selectedBookIndex, selectedChapter, number)
                val verse = canonical?.third ?: return@forEach
                val count = repository.forVerse(canonical.first, canonical.second, verse).size
                if (count > 0) put(number, count)
            }
        }
    }

    // The popover's own list. Separate from the column's because it describes the one verse whose
    // chip was clicked — never a passage, never what was learned — and because opening it must not
    // disturb the column's anchor.
    LaunchedEffect(state.popoverAnchor, loadedModule, fallbackAbbreviations) {
        val anchor = state.popoverAnchor
        if (anchor == null) {
            state.popoverRows = emptyList()
            return@LaunchedEffect
        }
        repository.ensureLoaded()
        state.popoverRows = repository.forVerse(anchor.first, anchor.second, anchor.third)
            .map { row(it.bookId, it.chapter, it.verse, it.endVerse, learned = false) }
    }

    return state
}
