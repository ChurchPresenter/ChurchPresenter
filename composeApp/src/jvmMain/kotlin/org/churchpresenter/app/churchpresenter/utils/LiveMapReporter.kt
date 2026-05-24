package org.churchpresenter.app.churchpresenter.utils

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Sends an anonymous, city-level ping to the ChurchPresenter live world map
 * (churchpresenter.app/map) when the app is opened.
 *
 * No personal data is transmitted — Cloudflare derives a city-level coordinate
 * server-side from the network layer. No IP address is stored.
 */
object LiveMapReporter {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val http by lazy { HttpClient(CIO) }

    private const val PING_URL = "https://www.churchpresenter.app/api/ping"

    fun pingOnOpen() {
        scope.launch {
            try {
                http.get(PING_URL)
            } catch (_: Exception) {
                // Non-fatal — silently ignore network errors.
            }
        }
    }
}
