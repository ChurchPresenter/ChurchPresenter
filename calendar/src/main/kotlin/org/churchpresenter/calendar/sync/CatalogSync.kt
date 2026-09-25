package org.churchpresenter.calendar.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.churchpresenter.core.models.io.writeTextAtomically
import org.churchpresenter.core.models.songs.SongItem
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate

/** What the desktop last pushed of one catalog part: the bytes' hash, when, and how long the relay keeps it. */
@Serializable
data class PushedCatalogPart(val hash: String, val at: String, val keepUntil: String)

/**
 * Every catalog part on the relay, by record id, as this desktop last wrote it -- for the instance
 * [instanceId]. After "Start over" the desktop is a new instance whose relay holds nothing, so a
 * memory of another instance's parts is no memory at all: without the id, an unchanged library
 * was never pushed to the new one and every phone's song list stayed empty.
 */
@Serializable
data class CatalogSyncState(val instanceId: String = "", val pushed: Map<String, PushedCatalogPart> = emptyMap())

/** `catalog-sync.json` beside `calendar.json`: the memory that keeps an unchanged library costing nothing. */
class CatalogSyncStore(private val folder: File) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    internal val file: File = File(folder, CATALOG_SYNC_FILE)

    fun load(): CatalogSyncState {
        if (!file.isFile) return CatalogSyncState()
        return runCatching { json.decodeFromString(CatalogSyncState.serializer(), file.readText()) }
            .getOrDefault(CatalogSyncState())
    }

    fun save(state: CatalogSyncState) {
        folder.mkdirs()
        file.writeTextAtomically(json.encodeToString(CatalogSyncState.serializer(), state))
    }

    private companion object {
        const val CATALOG_SYNC_FILE = "catalog-sync.json"
    }
}

/**
 * Keeps the desktop's songbooks on the relay -- see `CatalogRecord`. A part is written only when
 * its bytes changed since the last push, and no more than once a day, so a library that never
 * changes is pushed once and then never again; a part whose `keepUntil` is running out is
 * rewritten to keep it; a songbook that is gone has its parts deleted.
 *
 * Runs on its own, after a calendar round and never inside one: the startup round has a budget
 * the auto-loader depends on, and a first push of a large library must not spend it. The cursor
 * is left where the calendar push put it -- a phone may write between the two -- so the next pull
 * hands the parts back once and `SyncCoordinator` passes over them by their prefix.
 */
class CatalogSync(
    private val store: CatalogSyncStore,
    private val client: RelayClient,
    private val sealing: Sealing,
    private val songs: () -> List<SongItem>,
    private val seconds: (SongItem) -> Int?,
    private val today: () -> LocalDate = LocalDate::now,
    private val now: () -> Instant = Instant::now,
) {
    private val json = Json { encodeDefaults = true; explicitNulls = false }

    /** One pass; how many parts were written or deleted. Nothing happens while the library has not loaded. */
    fun push(token: String): Int {
        val library = songs()
        if (library.isEmpty()) return 0
        val records = Projection.catalog(library, seconds)
        val loaded = store.load()
        val state = loaded.takeIf { it.instanceId == sealing.instanceId } ?: CatalogSyncState(sealing.instanceId)
        val next = state.pushed.toMutableMap()
        val at = now()
        var changed = 0

        for ((id, record) in records) {
            val hash = sha256(json.encodeToString(CatalogRecord.serializer(), record))
            if (!due(state.pushed[id], hash, at)) continue
            val sealed = sealing.sealCatalog(id, record, today())
            client.putRecord(token, sealed, ifRev = 0)
            next[id] = PushedCatalogPart(hash = hash, at = at.toString(), keepUntil = sealed.keepUntil)
            changed++
        }
        for (gone in state.pushed.keys - records.keys) {
            client.deleteRecord(token, gone)
            next.remove(gone)
            changed++
        }
        if (next != state.pushed || loaded.instanceId != sealing.instanceId) {
            store.save(CatalogSyncState(sealing.instanceId, next))
        }
        return changed
    }

    /**
     * Whether a part goes this round: never pushed, its bytes changed, or its keep-until is running
     * out -- but a book that changed goes once a day, not once a keystroke.
     */
    private fun due(before: PushedCatalogPart?, hash: String, at: Instant): Boolean {
        if (before == null) return true
        val differs = before.hash != hash
        val stale = LocalDate.parse(before.keepUntil) < today().plusDays(REFRESH_BEFORE_DAYS)
        val recent = Instant.parse(before.at).plusMillis(MIN_INTERVAL_MS) > at
        return (differs && !recent) || stale
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val MIN_INTERVAL_MS = 24L * 60L * 60L * 1_000L
        const val REFRESH_BEFORE_DAYS = 180L
    }
}
