package org.churchpresenter.canvas

import org.churchpresenter.diagnostics.CrashReporter
import java.util.concurrent.ConcurrentHashMap

/**
 * Why a DeckLink input will not open, decided before the card is asked — or null when nothing does.
 *
 * Both answers are the operator's setup rather than a defect, so neither is reported: a saved source
 * can name a card that is not fitted on this machine, and an output-only card has no input to open.
 * An active output is deliberately not a reason here — some cards capture and play out at once — so
 * it only decides what [deckLinkOpenFailure] says after a real attempt has failed.
 */
internal fun deckLinkInputBlocker(present: Boolean, hasInput: Boolean): CameraFailure? = when {
    !present -> CameraFailure.DECKLINK_NOT_FOUND
    !hasInput -> CameraFailure.DECKLINK_NO_INPUT
    else -> null
}

/** What a failed open is shown as: the card's own output is the likely cause when it is driving one. */
internal fun deckLinkOpenFailure(outputActive: Boolean): CameraFailure =
    if (outputActive) CameraFailure.DECKLINK_INPUT_IN_USE else CameraFailure.DECKLINK_OPEN_FAILED

/** One report per DeckLink index per run: the next attempt at the same card carries nothing new. */
internal class DeckLinkOpenReports {
    private val gates = ConcurrentHashMap<Int, ReportOnce>()

    /** True the first time only for [deckLinkIndex]. */
    fun claim(deckLinkIndex: Int): Boolean = gates.getOrPut(deckLinkIndex) { ReportOnce() }.claim()
}

/**
 * Reports an open that failed with nothing to explain it — the card is there, has an input and is not
 * driving an output. The title is constant so every occurrence lands in one issue; the rest is tags.
 */
internal fun reportDeckLinkOpenFailed(
    deckLinkIndex: Int,
    deviceName: String,
    inputModeCount: Int,
    reports: DeckLinkOpenReports,
) {
    if (!reports.claim(deckLinkIndex)) return
    CrashReporter.reportWarning(
        "DeckLink: Failed to open input on device",
        tags = mapOf(
            "subsystem" to "decklink",
            "decklink_index" to deckLinkIndex.toString(),
        ),
        extras = mapOf(
            "decklink_device" to deviceName,
            "decklink_input_modes" to inputModeCount.toString(),
        ),
    )
}
