package engine.version

import engine.bible.Script

/**
 * One translation's rendering of a single verse — the unit version detection compares.
 *
 * [id] is the stable `<LANG>_<ABBREV>` id shared with `Config.loadedBibles` and the detection log;
 * [label] is what a human sees ("NASB").
 */
data class VersionCandidate(
    val id: String,
    val label: String,
    val script: Script,
    val text: String,
)

/**
 * Every translation the engine can *recognize* — deliberately wider than the two bibles
 * ChurchPresenter selects for detection, because the speaker is often reading from a version the
 * operator hasn't loaded. Read-only and immutable once built.
 *
 * An interface rather than a concrete class so tests inject a map-backed corpus and never touch the
 * filesystem: the scoring rules are what need covering, not the `.spb` byte layout (which
 * [SpbVersionIndex] covers separately).
 */
interface VersionCorpus {

    /** Every candidate holding the canonical verse [code] (`BXXXCXXXVXXX`). Empty when unknown. */
    fun rendering(code: String): List<VersionCandidate>

    /** Display labels of every indexed version, for the detection log's session header. */
    val labels: List<String>

    companion object {
        /** The corpus before background indexing finishes, and whenever the feature is off. */
        val EMPTY: VersionCorpus = object : VersionCorpus {
            override fun rendering(code: String): List<VersionCandidate> = emptyList()
            override val labels: List<String> = emptyList()
        }
    }
}
