package org.churchpresenter.ndi

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Holds a source up long enough for a human to look at it in NDI Video Monitor, because the one
 * thing no automated test can check is whether the *picture* is right — specifically whether alpha
 * survives the wire, which is the entire premise of [NdiOutputMode.ALPHA].
 *
 * ```
 * ./gradlew :ndi:test -PndiHardware=true -PndiSeconds=240 --tests '*NdiLiveSenderProbe*'
 * ```
 *
 * Opt-in and inert by default, like [NdiHardwareTest], and for the same reason: it advertises a
 * source every NDI receiver on the LAN can discover.
 *
 * **The pattern is the point, and getting it right took two attempts.** The obvious test image —
 * an opaque shape on a transparent field — cannot answer the question: with RGB=black behind
 * alpha=0, the surround looks black whether the alpha arrived or was silently dropped. The bands
 * below fix that by holding RGB constant at pure white and varying *only* alpha, so the two
 * outcomes are visually opposite: a brightness staircase if alpha survives, a flat white rectangle
 * if it did not.
 *
 * **Verified 2026-08-26 against NDI SDK 6.3.2.0, received in NDI Video Monitor: a staircase,
 * darkening toward the bottom.** Alpha survives the BGRA path, so a lower third reaches OBS already
 * keyed.
 */
class NdiLiveSenderProbe {

    @Test
    fun `send a keyable test pattern`() {
        if (System.getProperty("churchpresenter.ndiHardware") != "true") return
        val seconds = System.getProperty("churchpresenter.ndiSeconds")?.toIntOrNull() ?: 30
        val path = NdiRuntime.detect() ?: return
        val lib = JnaNdiLibrary.load(path) ?: return
        check(lib.initialize())

        val w = 640
        val h = 360

        // Four horizontal bands, all with RGB = pure white and only the ALPHA byte differing.
        //
        // This is the point: a receiver that honours alpha composites white onto its own background
        // (black, in NDI Video Monitor), so the bands read 100% / 66% / 33% / 0% brightness — a
        // staircase. A receiver that has *dropped* the alpha sees four identical opaque white bands
        // — a flat white rectangle.
        //
        // The first pattern this probe sent could not tell those apart: its transparent region was
        // alpha=0 over RGB=black, which looks black whether the alpha arrived or not.
        val argb = IntArray(w * h) { i ->
            val y = i / w
            val alpha = when (y * 4 / h) {
                0 -> 0xFF   // opaque white — the control; must look white in every case
                1 -> 0xAA
                2 -> 0x55
                else -> 0x00 // fully transparent white — black if alpha honoured, WHITE if dropped
            }
            (alpha shl 24) or 0x00FFFFFF
        }

        val sender = NdiSender(lib, "ChurchPresenter ALPHA TEST", NdiOutputMode.ALPHA, fps = 30)
        check(sender.open())
        println(">>> Sending 'ChurchPresenter ALPHA TEST' for $seconds s — open NDI Video Monitor now.")
        try {
            val deadline = System.nanoTime() + seconds * 1_000_000_000L
            var frames = 0
            var lastReport = 0L
            while (System.nanoTime() < deadline) {
                sender.send(argb, w, h)
                frames++
                val elapsed = (System.nanoTime() - deadline) / 1_000_000_000L + seconds
                if (elapsed != lastReport) {
                    lastReport = elapsed
                    println("    ${elapsed}s  frames=$frames  receivers=${sender.connectionCount()}")
                }
                Thread.sleep(33)
            }
            println(">>> Done. $frames frames sent, final receivers=${sender.connectionCount()}")
            // Not much of an assertion, but not nothing: if the runtime accepted a sender and then
            // took no frames at all, that is a failure rather than a picture worth squinting at.
            assertTrue(frames > 0, "the runtime accepted a sender but took no frames")
        } finally {
            sender.close()
            lib.destroy()
        }
    }
}
