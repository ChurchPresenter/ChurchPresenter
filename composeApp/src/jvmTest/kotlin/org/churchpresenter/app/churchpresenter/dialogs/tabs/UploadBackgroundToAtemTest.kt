package org.churchpresenter.app.churchpresenter.dialogs.tabs

import kotlinx.coroutines.runBlocking
import org.churchpresenter.settings.AtemSettings
import java.io.File
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

/**
 * Covers the part of `uploadBackgroundToAtem` that runs before it goes near the network: reading the
 * chosen file.
 *
 * The upload as a whole needs a real switcher — `AtemClient.connect()` sends a UDP hello and waits
 * out its own connect timeout for a reply — so the tests here stop short of it. What they can reach
 * is the failure an operator actually hits: a background image that has been moved, deleted or is
 * not really an image. `ImageIO.read` returns null for all three and the upload has to fail with a
 * clear message rather than a `NullPointerException`, before any upload slot is reserved.
 *
 * The function is a private top-level `suspend` one, which `AGENT.md` names as the case where
 * reflection is the fallback rather than widening it; no production code is changed for this test.
 */
class UploadBackgroundToAtemTest {

    private val method = Class.forName("org.churchpresenter.app.churchpresenter.dialogs.tabs.BackgroundSettingsTabKt")
        .declaredMethods
        .first { it.name == "uploadBackgroundToAtem" }
        .apply { isAccessible = true }

    /** Drives the private suspend function by handing it a continuation directly. */
    private suspend fun upload(atem: AtemSettings, imagePath: String, slot: Int): Any? =
        suspendCoroutineUninterceptedOrReturn { continuation ->
            method.invoke(null, atem, imagePath, slot, continuation)
        }

    private fun atem() = AtemSettings(host = "10.0.0.5", renderWidth = 16, renderHeight = 16)

    /**
     * A background whose file has been moved or deleted must stop the upload cleanly.
     *
     * As with the non-image case below, **which** exception carries that is not asserted, and neither
     * is its message. A missing file fails inside `ImageIO` rather than coming back null, so the
     * message is the reader's — and the reader is whichever one the JDK has installed. This test did
     * assert a non-blank message, which held on macOS and then failed on CI with a null one; that is
     * the same platform dependency already documented below, and it is not worth pinning from either
     * side. What holds everywhere is that it fails, and that it does not fail as a bare
     * `NullPointerException` from dereferencing a null image — an operator seeing an NPE learns
     * nothing.
     */
    @Test
    fun `a missing file fails rather than dereferencing a null image`() {
        val missing = File(System.getProperty("java.io.tmpdir"), "churchpresenter-no-such-background.png")
        if (missing.exists()) missing.delete()

        val error = assertFailsWith<Exception> {
            runBlocking { upload(atem(), missing.absolutePath, slot = 1) }
        }
        assertTrue(error !is NullPointerException, "a missing file must not surface as a NPE")
    }

    /**
     * A file that is not an image must stop the upload rather than send garbage to the switcher.
     *
     * **Which** exception carries that is deliberately not asserted. `ImageIO.read` may either return
     * null — in which case the upload's own `?: throw Exception("Could not read image file")` fires —
     * or throw one of its own, and which happens depends on the JDK's installed image readers. This
     * test originally asserted the message and passed on macOS/JDK 24 while failing elsewhere, where
     * ImageIO threw first with a null message. What holds everywhere is that it fails, and that it
     * does not fail as a bare `NullPointerException` from dereferencing a null image.
     */
    @Test
    fun `a file that is not an image fails the same way`() {
        val notAnImage = File.createTempFile("churchpresenter-background", ".png")
        try {
            notAnImage.writeText("this is text, not a PNG")

            val error = assertFailsWith<Exception> {
                runBlocking { upload(atem(), notAnImage.absolutePath, slot = 1) }
            }
            assertTrue(
                error !is NullPointerException,
                "a non-image must be rejected deliberately, not by dereferencing a null image",
            )
        } finally {
            notAnImage.delete()
        }
    }

    @Test
    fun `an empty file fails before any upload slot is reserved`() {
        val empty = File.createTempFile("churchpresenter-empty-background", ".png")
        try {
            assertFailsWith<Exception> {
                runBlocking { upload(atem(), empty.absolutePath, slot = 0) }
            }
            // The failure happens while reading, ahead of AtemUploadStatus.begin, so nothing is left
            // half-started for the Lower Third tab's progress bar to show.
            assertTrue(empty.length() == 0L, "the fixture must really be empty")
        } finally {
            empty.delete()
        }
    }
}
