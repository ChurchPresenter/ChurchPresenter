package org.churchpresenter.settings

// What profile resolution remembers. Merging a section encodes and decodes it whole, so each answer
// is kept against the identity of the immutable objects it was made from.

/**
 * The last few merges of one settings section, each keyed by the identity of the document's
 * section and the profile's copy it was made from -- see [resolvedFor]. A few, not one: every
 * output follows its own profile, and they all resolve on the same change, so a single slot would
 * decode again for each one in turn.
 *
 * Shared by the output windows and the off-screen outputs, so it is read and written under a lock;
 * the merge itself runs outside it, and two threads racing on a new pair at worst both decode.
 */
internal class RecentMerges<T>(private val capacity: Int = RECENT_MERGES) {
    private class Entry<T>(val global: T, val own: T, val merged: T)

    private val entries = ArrayDeque<Entry<T>>()

    fun of(global: T, own: T, merge: (T, T) -> T): T {
        synchronized(entries) {
            entries.firstOrNull { it.global === global && it.own === own }?.let { return it.merged }
        }
        val merged = merge(global, own)
        synchronized(entries) {
            entries.removeAll { it.global === global && it.own === own }
            entries.addFirst(Entry(global, own, merged))
            while (entries.size > capacity) entries.removeLast()
        }
        return merged
    }
}

/** More profiles than a service runs at once, each with a change or two still in flight. */
private const val RECENT_MERGES = 16

/**
 * [compute]'s answer for the last [inputs] it was asked about, compared by identity. [inputs] are
 * the parts of the document the answer reads, so a change anywhere else -- a column width, a recent
 * file -- does not decode the section again.
 */
internal class LastResult<T>(
    private val inputs: (AppSettings) -> List<Any?>,
    private val compute: (AppSettings) -> T,
) {
    @Volatile private var last: Pair<List<Any?>, T>? = null

    fun of(settings: AppSettings): T {
        val now = inputs(settings)
        last?.let { (seen, value) -> if (sameInstances(seen, now)) return value }
        return compute(settings).also { last = now to it }
    }
}

private fun sameInstances(a: List<Any?>, b: List<Any?>): Boolean =
    a.size == b.size && a.indices.all { a[it] === b[it] }
