package org.churchpresenter.server

/**
 * Where calendar sync reaches the relay, and where it fetches the relay's client key. Neither is in
 * the repository: the app builds both in from GitHub Secrets (see `generateBuildConfig`) and passes
 * them in, so a build made without them has no relay to reach and says so rather than guessing one.
 */
data class RelayEndpoints(val relayUrl: String, val clientKeyUrl: String) {
    companion object {
        /** No relay: what a build without the secrets has, and what a test starts from. */
        val NONE = RelayEndpoints("", "")
    }
}
