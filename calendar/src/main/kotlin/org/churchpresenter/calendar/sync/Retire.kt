package org.churchpresenter.calendar.sync

/** Pages of changes read to find everything an instance holds before it is emptied. */
private const val RETIRE_PAGES = 50

/** Tries at emptying an instance a phone keeps writing to before giving up. */
private const val RETIRE_ATTEMPTS = 3

/**
 * Empties this instance at the relay before the desktop forgets it: every service, deletion and
 * songbook record, and every enrolled phone. Forgetting alone left all of it there, readable by any
 * phone that still held the key -- the reason "Start over" exists -- and served to it on a token
 * the relay still accepted. Afterwards no phone's token is accepted and there is nothing left for
 * the old key to open. Throws [RelayFailure] when the relay cannot be reached, so the caller only
 * forgets the pairing once this has worked.
 */
fun RelayClient.retire(token: String) {
    val songbooks = HashSet<String>()
    val phones = HashSet<String>()
    var rev = 0L
    var pages = 0
    do {
        val page = changes(token, rev)
        page.records.map { it.id }.filterTo(songbooks) { it.startsWith(CATALOG_PREFIX) }
        page.devices.mapTo(phones) { it.id }
        rev = page.rev
        pages++
    } while (page.more && pages < RETIRE_PAGES)
    val empty = StateRequest(records = emptyList(), presetsBox = "")
    repeat(RETIRE_ATTEMPTS) { attempt ->
        try {
            putState(token, empty, rev)
            songbooks.forEach { deleteRecord(token, it) }
            phones.forEach { revokeDevice(token, it) }
            return
        } catch (e: RelayFailure.Conflict) {
            // A phone wrote in between: read where the relay is now and empty it from there.
            if (attempt == RETIRE_ATTEMPTS - 1) throw e
            rev = changes(token, rev).rev
        }
    }
}
