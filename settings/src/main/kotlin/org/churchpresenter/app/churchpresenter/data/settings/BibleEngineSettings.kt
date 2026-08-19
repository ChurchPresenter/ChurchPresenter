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
    // Must not conflict with the Companion server's port (8765) or any port it may bind on startup.
    // The Companion server previously also bound port+1 (8766) as a localhost connector, which caused
    // the BLE server to silently fail to bind on Mac (kqueue async failure not caught by runCatching),
    // leaving the BLE client connecting to the Companion server on 8766 and getting 404. That
    // localhost connector has been removed; 8766 is now safe to use for the BLE.
    val port: Int = 8766,
    val textMatchLevel: String = "off",
    val autoFollow: Boolean = false,
    // "balanced" / "fast" — how much of a verse must be spoken before the engine confirms it
    // while reading straight through several in a row (Config.applyContinuationSpeed). Same
    // String-not-enum decoupling rationale as [textMatchLevel]; independent of it.
    val continuationSpeed: String = "balanced",
    // When on, the Bible tab shows live feedback buttons (wrong passage / premature / missed
    // passage) that write to TrainingDataLogger's operator-flags-*.jsonl for the BLE training
    // workflow — set here so STTTab (where the checkbox lives) and BibleTab (where the buttons
    // render) share it without passing a ViewModel between tabs.
    val helpDevMode: Boolean = false,
)
