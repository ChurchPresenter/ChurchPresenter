package org.churchpresenter.app.churchpresenter.utils

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.churchpresenter.app.churchpresenter.BuildConfig

/**
 * Sends an anonymous, city-level ping to the ChurchPresenter live world map
 * (churchpresenter.org/map) when the app is opened.
 *
 * No personal data is transmitted — Cloudflare derives a city-level coordinate
 * server-side from the network layer. No IP address is stored.
 */
object LiveMapReporter {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val http by lazy {
        HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = 10_000
                connectTimeoutMillis = 5_000
            }
        }
    }

    private const val PING_URL = "https://www.churchpresenter.org/api/ping"

    // BuildConfig.IS_RELEASE is true only for packaged installer builds (see the
    // generateBuildConfig task in build.gradle.kts). A `run`/IDE launch is a
    // developer build, which pings with ?src=dev so test launches are tracked
    // separately and don't skew real-user stats on the live map.
    private val isDevBuild: Boolean = !BuildConfig.IS_RELEASE

    /**
     * @param installId Stable anonymous install id, sent as the X-Install-Id
     * header so the server dedupes repeat launches to one row per install. Pass
     * null (the default) to opt out — the server then falls back to a coarse
     * geo-grid dedupe. Callers should only pass an id when analytics is enabled.
     */
    fun pingOnOpen(installId: String? = null) {
        val url = if (isDevBuild) "$PING_URL?src=dev" else PING_URL
        scope.launch {
            try {
                http.get(url) {
                    if (!installId.isNullOrBlank()) header("X-Install-Id", installId)
                }
            } catch (_: Exception) {
                // Non-fatal — silently ignore network errors.
            }
        }
    }
}
