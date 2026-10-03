package org.churchpresenter.bibletab

/**
 * The speech-to-text status shown on the Bible tab, as a value rather than a string. The rule is a
 * priority ladder over ~10 connection booleans (engine start, engine link, the engine's own upstream
 * STT socket, the app's own STT connection, whether transcript is arriving, reconnect/connect state).
 * It was an inline `when` in BibleTab; extracting it lets the ordering — which case wins when several
 * conditions hold at once — be tested apart from the Compose string lookup that renders it.
 */
internal enum class BibleSttStatus {
    ENGINE_UNAVAILABLE,
    NO_BIBLE,
    ENGINE_CONNECTING,
    ENGINE_STT_DOWN,
    WAITING_FOR_STT,
    LISTENING,
    RECONNECTING,
    UNREACHABLE,
    CONNECTING,
    NOT_CONNECTED,
}

/**
 * The winning [BibleSttStatus] for the current connection state. Order matters: a hard problem
 * (engine failed, no bible, engine reachable but its STT socket down) outranks the app's own
 * "listening", which outranks the reconnect/connect/idle states.
 */
internal fun bibleSttStatus(signals: BibleSttSignals): BibleSttStatus = with(signals) { when {
    engineStartFailed -> BibleSttStatus.ENGINE_UNAVAILABLE
    noBibleSelected -> BibleSttStatus.NO_BIBLE
    sttConnected && !engineConnected -> BibleSttStatus.ENGINE_CONNECTING
    engineConnected && engineSttDown -> BibleSttStatus.ENGINE_STT_DOWN
    sttConnected && !sttReceiving && !hasDetectedReferences -> BibleSttStatus.WAITING_FOR_STT
    sttConnected -> BibleSttStatus.LISTENING
    sttReconnecting -> BibleSttStatus.RECONNECTING
    sttConnectError -> BibleSttStatus.UNREACHABLE
    sttConnecting -> BibleSttStatus.CONNECTING
    else -> BibleSttStatus.NOT_CONNECTED
} }

/** What [bibleSttStatus] reads: the engine's, the speech feed's and the selection's state. */
internal data class BibleSttSignals(
    val engineStartFailed: Boolean = false,
    val noBibleSelected: Boolean = false,
    val sttConnected: Boolean = false,
    val engineConnected: Boolean = false,
    val engineSttDown: Boolean = false,
    val sttReceiving: Boolean = false,
    val hasDetectedReferences: Boolean = false,
    val sttReconnecting: Boolean = false,
    val sttConnectError: Boolean = false,
    val sttConnecting: Boolean = false,
)
