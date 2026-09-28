package org.churchpresenter.omt

/**
 * Discovery: who is sending OMT on this network right now.
 *
 * Simpler than NDI's finder, and the difference is the library's: `libomt` runs one discovery for
 * the whole process, started on first use and kept for its life, so there is no handle to create,
 * nothing to destroy, and no use-after-free to defend against when a picker closes mid-look.
 *
 * What is left is the one rule `omt_discovery_getaddresses` states — its array is valid only until
 * the next call — so every look goes through the one lock here, and the strings are copied out
 * before it is released. Two pickers asking at once then read one list each rather than one
 * another's.
 */
class OmtDiscovery(private val library: OmtLibrary) {
    private val lock = Any()

    /**
     * Every source discovery knows about so far, as the addresses a receiver connects by, sorted.
     *
     * Cumulative, as NDI's is: the first answer after start-up is usually empty and fills over the
     * next seconds, so an empty list means "not yet" rather than "nobody is sending".
     */
    fun sources(): List<String> = synchronized(lock) { library.discoveryAddresses() }
        .filter { it.isNotBlank() }
        .distinct()
        .sortedBy { it.lowercase() }
}
