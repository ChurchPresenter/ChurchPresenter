package org.churchpresenter.app.churchpresenter.composables

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What a camera failure report says about the enumeration behind it.
 *
 * These tags are the reason we can stop asking a reporter to run `ffmpeg -list_devices` by hand.
 * Issue #462 had two possible causes — ffmpeg absent, and ffmpeg present but the device coming from
 * the platform inventory whose names DirectShow does not answer to — and an event carrying only
 * "could not open" cannot tell them apart. Each test below is one of the states we have to be able
 * to read straight off the issue.
 *
 * Whole maps are asserted rather than individual keys on purpose: a later edit that drops a tag
 * should fail here rather than quietly make a class of report unanswerable again.
 */
class CameraReportFactsTest {

    private fun facts(
        enumerator: CameraEnumerator,
        ffmpegListed: Int = 0,
        fallbackListed: Int = 0,
        deckLink: Int = 0,
        ffmpegAvailable: Boolean = true,
        names: Set<String> = emptySet(),
        enumeratedAtMs: Long = 1_000L,
    ) = CameraEnumerationFacts(
        enumerator = enumerator,
        ffmpegListedCount = ffmpegListed,
        fallbackListedCount = fallbackListed,
        deckLinkCount = deckLink,
        ffmpegAvailable = ffmpegAvailable,
        enumeratedAtMs = enumeratedAtMs,
        names = names,
    )

    // ── The four states a report has to distinguish ───────────────────────────────────────────

    @Test
    fun `ffmpeg absent is readable from the tags alone`() {
        val tags = cameraEnumerationTags(
            facts(CameraEnumerator.PNP_FALLBACK, fallbackListed = 2, ffmpegAvailable = false, names = setOf("webcam")),
            deviceName = "Webcam",
            ffmpegAvailable = false,
        )

        assertEquals(
            mapOf(
                "camera.ffmpeg" to "absent",
                "camera.enumerator" to "pnp_fallback",
                "camera.listed" to "2",
                "camera.device_listed" to "yes",
            ),
            tags,
        )
    }

    @Test
    fun `ffmpeg present but listing nothing is the fallback enumerator`() {
        val tags = cameraEnumerationTags(
            facts(CameraEnumerator.PNP_FALLBACK, fallbackListed = 1, names = setOf("webcam")),
            deviceName = "Webcam",
            ffmpegAvailable = true,
        )

        assertEquals("present", tags["camera.ffmpeg"])
        assertEquals(
            "pnp_fallback", tags["camera.enumerator"],
            "the fallback runs only when ffmpeg listed nothing, so this alone proves the dshow count was zero",
        )
    }

    @Test
    fun `a device ffmpeg listed and then failed on reads as an ordinary capture failure`() {
        val tags = cameraEnumerationTags(
            facts(CameraEnumerator.DSHOW, ffmpegListed = 3, names = setOf("logitech brio")),
            deviceName = "Logitech BRIO",
            ffmpegAvailable = true,
        )

        assertEquals(
            mapOf(
                "camera.ffmpeg" to "present",
                "camera.enumerator" to "dshow",
                "camera.listed" to "3",
                "camera.device_listed" to "yes",
            ),
            tags,
        )
    }

    @Test
    fun `a device missing from the last listing says so`() {
        val tags = cameraEnumerationTags(
            facts(CameraEnumerator.DSHOW, ffmpegListed = 1, names = setOf("integrated webcam")),
            deviceName = "Some Other Camera",
            ffmpegAvailable = true,
        )

        assertEquals(
            "no", tags["camera.device_listed"],
            "a settings file carried from another machine, or a camera unplugged since the picker was opened",
        )
    }

    @Test
    fun `nothing having enumerated is distinct from nothing having been found`() {
        val tags = cameraEnumerationTags(facts = null, deviceName = "Webcam", ffmpegAvailable = true)

        assertEquals("not_run", tags["camera.enumerator"])
        assertEquals(
            "not_enumerated", tags["camera.device_listed"],
            "the presenter restore path can capture without any picker having been opened",
        )
    }

    // ── Privacy ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `no tag and no extra carries what the camera is called`() {
        val deviceName = "Bob's Logitech"
        val withName = facts(CameraEnumerator.DSHOW, ffmpegListed = 1, names = setOf(deviceName.lowercase()))

        val tags = cameraEnumerationTags(withName, deviceName, ffmpegAvailable = true)
        val extra = cameraEnumerationExtra(withName, deviceName, ffmpegAvailable = true, nowMs = 2_000L)

        // Tag values are NOT scrubbed by CrashReporter — this is the only thing standing between a
        // camera named after its owner and a Sentry tag, so it is asserted rather than assumed.
        tags.forEach { (key, value) ->
            assertFalse(value.contains("Logitech", ignoreCase = true), "tag $key leaked the device name")
            assertFalse(value.contains("Bob", ignoreCase = true), "tag $key leaked the device name")
        }
        assertFalse(extra.contains("Logitech", ignoreCase = true), extra)
        assertFalse(extra.contains("Bob", ignoreCase = true), extra)
    }

    @Test
    fun `the extra carries the counts a triager needs and nothing else`() {
        val extra = cameraEnumerationExtra(
            facts(CameraEnumerator.PNP_FALLBACK, fallbackListed = 2, deckLink = 1, names = setOf("webcam")),
            deviceName = "Webcam",
            ffmpegAvailable = true,
            nowMs = 6_000L,
        )

        assertEquals(
            """
            enumerator=pnp_fallback
            ffmpeg=present
            ffmpeg_listed=0
            fallback_listed=2
            decklink=1
            device_listed=yes
            enumerated_age_s=5
            """.trimIndent(),
            extra,
        )
    }

    @Test
    fun `an age is never invented for an enumeration that did not happen`() {
        val extra = cameraEnumerationExtra(facts = null, deviceName = "Webcam", ffmpegAvailable = false)

        assertTrue("enumerated_age_s=never" in extra, extra)
    }

    // ── The blind-ffmpeg predicate ────────────────────────────────────────────────────────────

    @Test
    fun `ffmpeg working but listing nothing while windows lists cameras is worth reporting`() {
        assertTrue(shouldReportBlindFfmpeg(facts(CameraEnumerator.PNP_FALLBACK, fallbackListed = 1)))
    }

    @Test
    fun `a machine with no camera at all is not a defect and is not reported`() {
        assertFalse(
            shouldReportBlindFfmpeg(facts(CameraEnumerator.PNP_FALLBACK, fallbackListed = 0)),
            "the operator's own hardware inventory must not be made to look like a fault in the app",
        )
    }

    @Test
    fun `a machine without ffmpeg is not reported here`() {
        assertFalse(
            shouldReportBlindFfmpeg(
                facts(CameraEnumerator.PNP_FALLBACK, fallbackListed = 2, ffmpegAvailable = false)
            ),
            "that is the ffmpeg-missing report's business, and reporting both would double-count it",
        )
    }

    @Test
    fun `a machine where ffmpeg answered is not reported`() {
        assertFalse(shouldReportBlindFfmpeg(facts(CameraEnumerator.DSHOW, ffmpegListed = 2)))
    }

    @Test
    fun `nothing having enumerated is not reported`() {
        assertFalse(shouldReportBlindFfmpeg(null))
    }

    // ── The once-per-process gate ─────────────────────────────────────────────────────────────

    @Test
    fun `a gate lets exactly one report through`() {
        val gate = ReportOnce()

        assertTrue(gate.claim(), "the first caller reports")
        assertFalse(gate.claim(), "and no one else does")
        assertFalse(gate.claim())
    }
}
