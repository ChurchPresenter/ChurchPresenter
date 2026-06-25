package org.churchpresenter.app.churchpresenter.data.settings

import kotlinx.serialization.Serializable

/**
 * Settings for the Bible Lookup Engine (BLE) — the scripture-detection microservice. Connection
 * config (local vs remote, host/port) is operator-facing; [textMatchLevel] / [autoFollow] persist
 * the Bible-tab live controls across restarts. Deep engine tuning (BM25, translations) lives in the
 * engine's own config, not here. [textMatchLevel] is stored as a String to avoid coupling settings
 * to the viewmodel enum ("off" / "conservative" / "balanced" / "aggressive").
 */
@Serializable
data class BibleEngineSettings(
    val enabled: Boolean = true,
    val runLocal: Boolean = true,
    val host: String = "localhost",
    // Must differ from Constants.SERVER_DEFAULT_PORT (8765, the Companion server) — sharing a port
    // made the engine's WS server fail to bind silently while the STT client kept detecting, so no
    // detections ever reached the Bible tab.
    val port: Int = 8766,
    val textMatchLevel: String = "off",
    val autoFollow: Boolean = false,
)
