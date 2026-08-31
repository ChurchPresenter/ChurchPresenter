package org.churchpresenter.ndi

/**
 * Discovery: who is sending on this network right now.
 *
 * A long-lived object rather than a call. The SDK's finder builds its picture up over time — mDNS
 * answers arrive over the first seconds and sources come and go afterwards — so a finder created
 * per query would report an empty network every time it was asked. One is opened when the operator
 * first needs a source list and kept until nothing is looking at it, and [sources] asks it what it
 * knows so far.
 *
 * **Not thread-safe**, for the same reason [NdiSender] is not: one owner drives it.
 */
class NdiFinder(
    private val library: NdiLibrary,
    private val showLocalSources: Boolean = true,
    private val groups: String = "",
) {
    private var handle = 0L

    /** True between a successful [open] and a [close]. */
    val isOpen: Boolean get() = handle != 0L

    /** Starts discovery. False means the runtime refused, after which [sources] is always empty. */
    fun open(): Boolean {
        if (handle != 0L) return true
        handle = library.findCreate(showLocalSources, groups)
        return handle != 0L
    }

    /**
     * The sources discovery knows about, waiting up to [timeoutMs] for the list to change first.
     *
     * A timeout of 0 answers immediately with what is already known, which is what a UI redraw
     * wants; a non-zero one lets a first query block briefly rather than returning the empty list
     * discovery inevitably starts from.
     */
    fun sources(timeoutMs: Int = 0): List<NdiSourceInfo> =
        if (handle == 0L) emptyList() else library.findSources(handle, timeoutMs)

    /** Stops discovery. After this the finder can be [open]ed again. */
    fun close() {
        if (handle == 0L) return
        library.findDestroy(handle)
        handle = 0L
    }
}
