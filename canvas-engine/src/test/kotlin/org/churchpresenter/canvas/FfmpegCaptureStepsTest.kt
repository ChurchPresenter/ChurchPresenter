package org.churchpresenter.canvas

import kotlinx.coroutines.runBlocking
import org.churchpresenter.core.models.scene.SceneSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FfmpegCaptureStepsTest {

    private val source = SceneSource.CameraSource(
        id = "cam",
        name = "Camera",
        devicePath = "unknown://camera",
        deviceName = "Unknown Camera",
    )

    @Test
    fun `a device no platform lists has no formats`() {
        val formats = runBlocking { FfmpegCaptureSteps.formats(source) }

        assertTrue(formats.isEmpty())
    }

    @Test
    fun `a pause of nothing returns at once`() {
        runBlocking { FfmpegCaptureSteps.pause(0L) }
    }

    @Test
    fun `releasing an entry with no process leaves it as it was`() {
        val entry = CacheEntry()

        runBlocking { FfmpegCaptureSteps.releaseLingering(entry) }

        assertNull(entry.ffmpegProcess)
        assertNull(entry.error.value)
    }

    @Test
    fun `the last enumeration is the catalog's own`() {
        assertSame(CameraDeviceCatalog.lastEnumeration, FfmpegCaptureSteps.lastEnumeration())
    }

    @Test
    fun `a loop built without steps starts with no failure`() {
        val entry = CacheEntry()

        CaptureLoop(source, entry)

        assertEquals(null, entry.error.value)
    }
}
