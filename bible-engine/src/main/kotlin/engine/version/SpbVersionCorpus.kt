package engine.version

import engine.Config
import java.io.File

/**
 * A [VersionCorpus] backed by seek indexes over the `.spb` files in the bible root, with a small
 * cache in front so a re-emitted verse doesn't re-seek every module.
 *
 * Immutable once built; the cache is touched only from the single `ble-detection` thread.
 */
class SpbVersionCorpus(private val indexes: List<SpbVersionIndex>) : VersionCorpus {

    override val labels: List<String> = indexes.map { it.label }

    private val cache = object : LinkedHashMap<String, List<VersionCandidate>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<VersionCandidate>>) =
            size > CACHE_SIZE
    }

    override fun rendering(code: String): List<VersionCandidate> {
        cache[code]?.let { return it }
        val packed = SpbVersionIndex.packCode(code) ?: return emptyList()
        val out = indexes.mapNotNull { idx ->
            idx.text(packed)?.let { VersionCandidate(idx.id, idx.label, idx.script, it) }
        }
        cache[code] = out
        return out
    }

    private companion object {
        const val CACHE_SIZE = 64
    }
}

object VersionCorpusLoader {

    /**
     * Indexes every `.spb` under `Config.bibleRoot` (capped, deterministic) and collapses
     * near-duplicate modules of the same version. Never throws — a corpus that fails to build just
     * means no version is ever reported.
     *
     * [priorityFiles] (ChurchPresenter's selected bibles, by file name) are indexed ahead of
     * everything else so the cap can never exclude the very translations in use.
     *
     * [onSkip] is called once per `.spb` that is in the folder but not in the corpus, with the reason.
     * Silence here is how a folder of 24 modules can yield a corpus of 3 and look like a broken
     * feature instead of an unreadable share: every drop below is individually plausible, and
     * together they decide whether version detection can answer at all.
     */
    fun load(
        priorityFiles: List<String> = emptyList(),
        onSkip: (fileName: String, reason: String) -> Unit = { _, _ -> },
    ): VersionCorpus {
        val root = File(Config.bibleRoot)
        if (!root.exists()) return VersionCorpus.EMPTY
        val all = runCatching {
            root.walk().filter { it.isFile && it.name.endsWith(".spb") }.toList().sortedBy { it.name }
        }.getOrNull().orEmpty()
        // Priority entries are relative paths (`ENG/King James/kjv.spb`) or, for a file at the root
        // or from an older stored setting, bare names — accept both, same as SpbLoader.loadSelected.
        val priority = priorityFiles.map { it.replace('\\', '/') }.toSet()
        fun isPriority(f: File) =
            f.toRelativeString(root).replace('\\', '/') in priority || f.name in priority
        val files = (all.filter(::isPriority) + all.filterNot(::isPriority))
            .take(Config.versionMaxCorpusBibles)
        if (files.isEmpty()) return VersionCorpus.EMPTY

        val capped = files.toHashSet()
        all.filterNot { it in capped }.forEach {
            onSkip(it.name, "over the ${Config.versionMaxCorpusBibles}-module cap")
        }

        val seenIds = mutableMapOf<String, Int>()
        val indexes = files.mapNotNull { f ->
            val built = runCatching { SpbVersionIndex.build(f, seenIds) }
            val index = built.getOrNull()
            // A throw and a null mean different things to whoever is reading this: the first is the
            // file or the share, the second is the module's own contents. Reporting them the same
            // way would send an operator to reinstall a bible whose disk was the problem.
            when {
                built.isFailure -> onSkip(f.name, "unreadable — ${built.exceptionOrNull()?.describe()}")
                index == null -> onSkip(f.name, "not a usable module — no ##Abbreviation header, or under ten verses")
            }
            index
        }
        if (indexes.isEmpty()) return VersionCorpus.EMPTY

        val kept = collapseDuplicates(indexes)
        if (kept.size < indexes.size) {
            val survivors = kept.mapTo(HashSet()) { it.fileName }
            indexes.filterNot { it.fileName in survivors }
                .forEach { onSkip(it.fileName, "merged — another module already covers ${it.label}") }
        }
        return SpbVersionCorpus(kept).also { Config.versionCorpusLabels = it.labels }
    }

    /**
     * Merges modules that are really the same translation, keeping one representative each.
     *
     * Without this the feature silently fails on any folder holding two exports of one version:
     * they score identically forever, so the runner-up margin is never cleared and nothing is ever
     * reported. Distinct members of a family (KJV vs NKJV, ~0.7-0.85 similar) stay separate — they
     * are supposed to compete, and to report nothing when the reading doesn't separate them.
     */
    internal fun collapseDuplicates(indexes: List<SpbVersionIndex>): List<SpbVersionIndex> {
        if (indexes.size < 2) return indexes
        // First on the modules' own declared abbreviation. Two files both calling themselves "RST"
        // are the same version by the publisher's word, and no similarity threshold can be trusted to
        // say so: measured across a real 71-module folder the pairwise similarities run continuously
        // from 1.00 down through 0.90 with no valley — the two Synodal exports sit at 0.929, in among
        // genuinely distinct pairs like NHEB vs WEB (0.923), so any cut that merges the first
        // over-merges the second. Reporting two candidates under one visible label is incoherent
        // anyway. Input is filename-sorted, so the survivor is deterministic.
        val byLabel = indexes.distinctBy { it.label }
        if (byLabel.size < 2) return byLabel
        return collapseBySimilarity(byLabel)
    }

    private fun collapseBySimilarity(indexes: List<SpbVersionIndex>): List<SpbVersionIndex> {
        val sample = sampleCodes(indexes)
        if (sample.isEmpty()) return indexes
        val fingerprints = indexes.map { it.sampleTokens(sample) }
        val keep = ArrayList<SpbVersionIndex>(indexes.size)
        val keptPrints = ArrayList<Set<String>>(indexes.size)
        // Input is filename-sorted, so the representative of each group is deterministic.
        for (i in indexes.indices) {
            // A module too thin on the sample (a New-Testament-only edition against an OT-heavy
            // sample) can't be judged either way — keep it rather than collapse on no evidence.
            val print = fingerprints[i]
            if (print.size < MIN_FINGERPRINT_TOKENS) { keep.add(indexes[i]); keptPrints.add(print); continue }
            val dup = keptPrints.indices.any { j ->
                keptPrints[j].size >= MIN_FINGERPRINT_TOKENS &&
                    jaccard(print, keptPrints[j]) >= Config.versionDuplicateJaccard
            }
            if (!dup) { keep.add(indexes[i]); keptPrints.add(print) }
        }
        return keep
    }

    /**
     * A fixed set of canonical verse codes every module is asked about, spread across whatever the
     * corpus actually covers.
     *
     * It must be ONE list shared by all modules: drawing a sample per module and intersecting sounds
     * equivalent but is not, because modules have different verse counts and so different strides —
     * the sampled sets barely overlap, every fingerprint comes out near-empty, and nothing collapses
     * (observed against a real 71-module folder, where two byte-identical exports both survived).
     * Codes present in the most modules are preferred so New-Testament-only editions still get a
     * usable fingerprint.
     */
    private fun sampleCodes(indexes: List<SpbVersionIndex>): IntArray {
        val counts = HashMap<Int, Int>()
        for (idx in indexes) for (c in idx.codes()) counts.merge(c, 1, Int::plus)
        if (counts.isEmpty()) return IntArray(0)
        val threshold = indexes.size / 2 + 1
        val shared = counts.entries.filter { it.value >= threshold }.map { it.key }.sorted()
            .ifEmpty { counts.keys.sorted() }
        val step = (shared.size / SAMPLE_SIZE).coerceAtLeast(1)
        return shared.filterIndexed { i, _ -> i % step == 0 }.take(SAMPLE_SIZE).toIntArray()
    }

    /** Type plus message: an `AccessDeniedException` whose message is only a path says nothing alone. */
    private fun Throwable.describe(): String =
        listOfNotNull(this::class.simpleName, message).joinToString(": ")

    private fun jaccard(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val inter = a.count { it in b }
        val union = a.size + b.size - inter
        return if (union == 0) 0.0 else inter.toDouble() / union
    }

    private const val SAMPLE_SIZE = 150
    // Below this a fingerprint is too sparse for a similarity ratio to mean anything.
    private const val MIN_FINGERPRINT_TOKENS = 50
}
