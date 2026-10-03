package org.churchpresenter.app.churchpresenter

import org.churchpresenter.server.RelayEndpoints

/** The relay calendar sync reaches and its client-key URL, built in from GitHub Secrets. */
internal val builtInRelayEndpoints =
    RelayEndpoints(BuildConfig.CALENDAR_RELAY_URL, BuildConfig.CALENDAR_RELAY_CONFIG_URL)
