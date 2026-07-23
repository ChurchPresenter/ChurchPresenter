package org.churchpresenter.app.churchpresenter.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The single shared progress bar for ATEM media uploads.
 *
 * One [AtemUploadStatus] is observed by the Lower Third tab, but two uploads can overlap — the
 * companion API can start a second while the in-app one is still transferring. Every mutator is
 * therefore gated on the upload id returned by [begin]: a late [progress]/[complete]/[fail]/[clear]
 * from the OLDER upload must not touch the newer upload's bar, or the operator sees the wrong name,
 * a bar that jumps backwards, or a bar that clears while an upload is still running.
 *
 * State is a plain StateFlow read synchronously via `.value`, so these assertions need no waiting.
 * The object is a JVM singleton; each test calls [begin] first, which replaces the state outright,
 * so tests don't leak into one another.
 */
class AtemUploadStatusTest {

    @Test
    fun `begin publishes the upload and returns its id`() {
        val id = AtemUploadStatus.begin("Welcome", clip = false, slot = 3)
        val s = AtemUploadStatus.state.value
        assertEquals(id, s?.id)
        assertEquals("Welcome", s?.name)
        assertEquals(3, s?.slot)
        assertEquals(0f, s?.progress, "a fresh upload starts at zero progress")
    }

    @Test
    fun `each upload gets a distinct id`() {
        val first = AtemUploadStatus.begin("a", clip = false, slot = 1)
        val second = AtemUploadStatus.begin("b", clip = false, slot = 1)
        assertTrue(second != first, "ids must be unique so late calls can be attributed to their owner")
    }

    @Test
    fun `progress is clamped to the zero-to-one range`() {
        val id = AtemUploadStatus.begin("clip", clip = true, slot = 1)
        AtemUploadStatus.progress(id, 1.5f)
        assertEquals(1f, AtemUploadStatus.state.value?.progress, "over-unity progress would overflow the bar")
        AtemUploadStatus.progress(id, -0.2f)
        assertEquals(0f, AtemUploadStatus.state.value?.progress, "negative progress would render as a full or broken bar")
    }

    @Test
    fun `a stale upload cannot move the current upload's bar`() {
        val old = AtemUploadStatus.begin("old", clip = false, slot = 1)
        val current = AtemUploadStatus.begin("new", clip = false, slot = 2)
        AtemUploadStatus.progress(old, 0.9f)
        val s = AtemUploadStatus.state.value
        assertEquals(current, s?.id, "the newer upload must still own the bar")
        assertEquals("new", s?.name)
        assertEquals(0f, s?.progress, "the old upload's progress must not leak onto the new bar")
    }

    @Test
    fun `a stale upload cannot clear the current upload`() {
        val old = AtemUploadStatus.begin("old", clip = false, slot = 1)
        val current = AtemUploadStatus.begin("new", clip = false, slot = 2)
        AtemUploadStatus.clear(old)
        assertEquals(current, AtemUploadStatus.state.value?.id, "an old upload finishing must not blank a running one")
    }

    @Test
    fun `startProcessing flips to the ingest phase and resets progress`() {
        val id = AtemUploadStatus.begin("clip", clip = true, slot = 1)
        AtemUploadStatus.progress(id, 1f)
        AtemUploadStatus.startProcessing(id)
        val s = assertNotNull(AtemUploadStatus.state.value)
        assertTrue(s.processing, "post-upload ingest is a distinct phase the bar must show")
        assertEquals(0f, s.progress, "the processing phase restarts progress from zero")
    }

    @Test
    fun `complete drives the bar to full`() {
        val id = AtemUploadStatus.begin("done", clip = false, slot = 1)
        AtemUploadStatus.complete(id)
        assertEquals(1f, AtemUploadStatus.state.value?.progress)
    }

    @Test
    fun `fail without a message uses a default so the bar is never blank`() {
        val id = AtemUploadStatus.begin("boom", clip = false, slot = 1)
        AtemUploadStatus.fail(id, null)
        assertEquals("Upload failed", AtemUploadStatus.state.value?.error, "a failure with no cause still needs a label")
    }

    @Test
    fun `clear on the owning id returns to idle`() {
        val id = AtemUploadStatus.begin("gone", clip = false, slot = 1)
        AtemUploadStatus.clear(id)
        assertNull(AtemUploadStatus.state.value, "clearing the current upload leaves the bar idle")
    }
}
