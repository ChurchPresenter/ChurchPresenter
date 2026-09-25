package org.churchpresenter.app.churchpresenter.server

import org.churchpresenter.calendar.CalendarStore
import org.churchpresenter.calendar.PresetStore
import org.churchpresenter.calendar.sync.CatalogSync
import org.churchpresenter.calendar.sync.CatalogSyncStore
import org.churchpresenter.calendar.sync.Envelope
import org.churchpresenter.calendar.sync.PushedStateStore
import org.churchpresenter.calendar.sync.RelayClient
import org.churchpresenter.calendar.sync.RelayFailure
import org.churchpresenter.calendar.sync.RelayTransport
import org.churchpresenter.calendar.sync.Sealing
import org.churchpresenter.calendar.sync.SyncCoordinator
import org.churchpresenter.calendar.sync.fetchClientKey
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.core.models.songs.SongLibrary
import org.churchpresenter.settings.CalendarSyncSettings
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

/**
 * What every relay call is built from, read from the sync block of `settings.json` each time:
 * the client, the sealing, this installation's id, and the registration that mints them.
 */
internal class CalendarRelayAccess(
    private val folder: File,
    private val songFolder: File?,
    private val settings: () -> CalendarSyncSettings,
    private val saveSettings: (CalendarSyncSettings) -> Unit,
    private val transport: RelayTransport,
    private val onSaved: () -> Unit,
    /** How long a song usually runs here, for the catalog the phones plan with; null when never measured. */
    private val typicalSeconds: (SongItem) -> Int? = { null },
) {
    /**
     * Runs [block] with a client key, fetching one first if none is cached and once more if the
     * relay refuses it. Never with none: a call without a key is a wrong-key attempt as far as the
     * relay is concerned, and twenty of those in a day ban the address -- so a key that cannot be
     * fetched fails here, before anything is sent.
     */
    fun <T> withClientKey(block: () -> T): T {
        if (settings().clientKey.isEmpty() && !refreshClientKey()) {
            throw RelayFailure.ClientKey("could not fetch the client key from the website")
        }
        return try {
            block()
        } catch (_: RelayFailure.ClientKey) {
            if (!refreshClientKey()) throw RelayFailure.ClientKey()
            block()
        }
    }

    private fun refreshClientKey(): Boolean {
        val key = fetchClientKey(CalendarSyncSettings.CLIENT_KEY_URL, transport) ?: return false
        saveSettings(settings().copy(clientKey = key))
        return true
    }

    /** Registers with a fresh instance id, trying again once if the relay already knows the id. */
    fun ensureRegistered(): String {
        val current = settings()
        if (current.isPaired) return current.desktopToken
        var instanceId = current.instanceId.ifBlank { UUID.randomUUID().toString() }
        val installId = current.installId.ifBlank { UUID.randomUUID().toString() }
        val key = Envelope.encodeKey(Envelope.newKey())
        repeat(REGISTER_ATTEMPTS) {
            try {
                val client = RelayClient(current.relayUrl, instanceId, installId, transport, settings().clientKey)
                val token = client.register()
                saveSettings(
                    current.copy(
                        instanceId = instanceId,
                        desktopToken = token,
                        instanceKey = key,
                        installId = installId,
                        cursor = 0L,
                    ),
                )
                return token
            } catch (_: RelayFailure.Taken) {
                instanceId = UUID.randomUUID().toString()
            }
        }
        throw RelayFailure.Taken()
    }

    fun client(): RelayClient {
        val current = settings()
        return RelayClient(current.relayUrl, current.instanceId, installId(), transport, current.clientKey)
    }

    fun coordinator(): SyncCoordinator = SyncCoordinator(
        store = CalendarStore(folder),
        presetStore = PresetStore(folder),
        client = client(),
        sealing = sealing(),
        installId = installId(),
        songs = ::songs,
        onSaved = onSaved,
        pushed = PushedStateStore(folder),
    )

    /** The songbooks kept on the relay for the phones -- pushed on its own, after a round. */
    fun catalog(): CatalogSync = CatalogSync(CatalogSyncStore(folder), client(), sealing(), ::songs, typicalSeconds)

    fun sealing(): Sealing {
        val current = settings()
        val key = Envelope.decodeKey(current.instanceKey) ?: throw RelayFailure.Unauthorized()
        return Sealing(Envelope(key), current.instanceId)
    }

    private fun installId(): String {
        val current = settings()
        if (current.installId.isNotBlank()) return current.installId
        val fresh = UUID.randomUUID().toString()
        saveSettings(current.copy(installId = fresh))
        return fresh
    }

    val token: String get() = settings().desktopToken
    val cursor: Long get() = settings().cursor

    @Volatile
    private var songCache: List<SongItem>? = null

    /** The library, read once per process: a phone names songs by id and title, and both come from here. */
    private fun songs(): List<SongItem> {
        songCache?.let { return it }
        val loaded = songFolder?.let { runCatching { SongLibrary(it).load() }.getOrDefault(emptyList()) }.orEmpty()
        songCache = loaded
        return loaded
    }

    companion object {
        private val random = SecureRandom()
        private const val TOKEN_BYTES = 32
        private const val REGISTER_ATTEMPTS = 2

        /** A phone's bearer token: 32 random bytes, URL-safe so the QR carries it as-is. */
        fun newDeviceToken(): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(TOKEN_BYTES).also(random::nextBytes))

        /** What the relay stores instead of the token itself. */
        fun sha256Hex(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
