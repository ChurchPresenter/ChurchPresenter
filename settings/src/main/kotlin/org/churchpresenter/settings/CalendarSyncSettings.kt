package org.churchpresenter.settings

import kotlinx.serialization.Serializable

/** The connection between this desktop and the calendar relay that phones plan through. */
@Serializable
data class CalendarSyncSettings(
    val enabled: Boolean = false,
    val relayUrl: String = DEFAULT_RELAY_URL,
    /** Which church this is, as the relay knows it. Generated here, once. */
    val instanceId: String = "",
    /** Minted by the relay at registration. */
    val desktopToken: String = "",
    /** The key records are sealed under; made here, handed to phones at enrollment. URL-safe base64 of 32 bytes. */
    val instanceKey: String = "",
    /** This installation, so two desktops on one instance can tell each other apart. */
    val installId: String = "",
    /** The relay revision this desktop has seen everything up to. */
    val cursor: Long = 0L,
    /** ISO instant of the last successful sync, for the settings screen. */
    val lastSyncAt: String = "",
    /** The relay's shared client key, fetched from the website and cached;
     *  refreshed when the relay stops accepting it. */
    val clientKey: String = "",
) {
    val isPaired: Boolean get() = instanceId.isNotBlank() && desktopToken.isNotBlank() && instanceKey.isNotBlank()

    companion object {
        const val DEFAULT_RELAY_URL = "https://sync.churchpresenter.org"
        // The www host: the apex answers with a redirect, and a key is fetched, never followed to.
        const val CLIENT_KEY_URL = "https://www.churchpresenter.org/api/relay-config"
    }
}
