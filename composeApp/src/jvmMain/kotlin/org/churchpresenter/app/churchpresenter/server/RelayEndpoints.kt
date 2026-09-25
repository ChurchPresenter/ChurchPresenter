package org.churchpresenter.app.churchpresenter.server

import org.churchpresenter.app.churchpresenter.BuildConfig

/**
 * Where calendar sync reaches the relay, and where it fetches the relay's client key. Neither is in
 * the repository: both are built in from GitHub Secrets (see `generateBuildConfig`), so a build made
 * without them has no relay to reach and says so rather than guessing one.
 */
data class RelayEndpoints(val relayUrl: String, val clientKeyUrl: String) {
    companion object {
        val BUILT_IN = RelayEndpoints(BuildConfig.CALENDAR_RELAY_URL, BuildConfig.CALENDAR_RELAY_CONFIG_URL)
    }
}
