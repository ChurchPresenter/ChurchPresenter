package org.churchpresenter.ndi

/**
 * One NDI source seen on the network — what a finder returns and what a receiver connects to.
 *
 * [name] is the full `MACHINE (Source)` name the sender advertises and is what an operator picks
 * from a list; [address] is the `url`/`ip` the SDK reports beside it, carried so a receiver can be
 * pointed at a source on another subnet that discovery cannot see. Blank when discovery found the
 * source by name alone, which is the ordinary case on one LAN.
 */
data class NdiSourceInfo(val name: String, val address: String = "") {
    /** Whether this is a real source rather than the empty placeholder a blank setting produces. */
    val isValid: Boolean get() = name.isNotBlank() || address.isNotBlank()
}
