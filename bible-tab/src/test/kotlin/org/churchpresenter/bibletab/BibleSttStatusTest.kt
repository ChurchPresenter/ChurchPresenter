package org.churchpresenter.bibletab

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Bible-tab STT status ladder. It resolves ~10 connection booleans to one status, and the ORDER
 * is the whole point: a hard fault must outrank a hopeful "listening". These tests pin each case and
 * the overrides that were easy to get wrong inline (a down engine STT socket previously hidden behind
 * the app's own "listening").
 */
class BibleSttStatusTest {

    private fun status(
        engineStartFailed: Boolean = false,
        noBibleSelected: Boolean = false,
        sttConnected: Boolean = false,
        engineConnected: Boolean = false,
        engineSttDown: Boolean = false,
        sttReceiving: Boolean = false,
        hasDetectedReferences: Boolean = false,
        sttReconnecting: Boolean = false,
        sttConnectError: Boolean = false,
        sttConnecting: Boolean = false,
    ) = bibleSttStatus(BibleSttSignals(
        engineStartFailed, noBibleSelected, sttConnected, engineConnected, engineSttDown,
        sttReceiving, hasDetectedReferences, sttReconnecting, sttConnectError, sttConnecting,
    ))

    // ── each case in isolation ─────────────────────────────────────────────────

    @Test fun `nothing connected is not connected`() =
        assertEquals(BibleSttStatus.NOT_CONNECTED, status())

    @Test fun `a failed engine is unavailable`() =
        assertEquals(BibleSttStatus.ENGINE_UNAVAILABLE, status(engineStartFailed = true))

    @Test fun `no bible selected wins over connection state`() =
        assertEquals(BibleSttStatus.NO_BIBLE, status(noBibleSelected = true, sttConnected = true))

    @Test fun `connected to stt but not yet to the engine is engine-connecting`() =
        assertEquals(BibleSttStatus.ENGINE_CONNECTING, status(sttConnected = true, engineConnected = false))

    @Test fun `engine reachable but its stt socket down is surfaced`() =
        assertEquals(BibleSttStatus.ENGINE_STT_DOWN, status(engineConnected = true, engineSttDown = true))

    @Test fun `connected with no transcript and no detections is waiting`() =
        assertEquals(
            BibleSttStatus.WAITING_FOR_STT,
            status(sttConnected = true, engineConnected = true, sttReceiving = false, hasDetectedReferences = false),
        )

    @Test fun `connected and receiving transcript is listening`() =
        assertEquals(
            BibleSttStatus.LISTENING,
            status(sttConnected = true, engineConnected = true, sttReceiving = true),
        )

    @Test fun `reconnecting shows when not connected`() =
        assertEquals(BibleSttStatus.RECONNECTING, status(sttReconnecting = true))

    @Test fun `a connect error is unreachable`() =
        assertEquals(BibleSttStatus.UNREACHABLE, status(sttConnectError = true))

    @Test fun `connecting shows while dialing out`() =
        assertEquals(BibleSttStatus.CONNECTING, status(sttConnecting = true))

    // ── ordering / overrides ───────────────────────────────────────────────────

    @Test fun `a failed engine outranks a missing bible`() =
        assertEquals(BibleSttStatus.ENGINE_UNAVAILABLE, status(engineStartFailed = true, noBibleSelected = true))

    @Test fun `a down engine stt socket outranks listening`() =
        // Before this case existed the UI said "Listening" while nothing reached the engine.
        assertEquals(
            BibleSttStatus.ENGINE_STT_DOWN,
            status(sttConnected = true, engineConnected = true, engineSttDown = true, sttReceiving = true),
        )

    @Test fun `having detected a reference lifts waiting to listening`() =
        assertEquals(
            BibleSttStatus.LISTENING,
            status(sttConnected = true, engineConnected = true, sttReceiving = false, hasDetectedReferences = true),
        )

    @Test fun `being connected outranks a stale reconnecting flag`() =
        assertEquals(
            BibleSttStatus.LISTENING,
            status(sttConnected = true, engineConnected = true, sttReceiving = true, sttReconnecting = true),
        )
}
