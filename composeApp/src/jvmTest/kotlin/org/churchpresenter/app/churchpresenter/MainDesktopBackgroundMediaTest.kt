package org.churchpresenter.app.churchpresenter

import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.core.models.tabs.Tabs
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The decisions the root composable makes about work that outlives the tab it belongs to: keeping a
 * decoder alive off the Media tab, following a section chosen elsewhere, and the guards that stop a
 * stale index or a missing folder reaching a view model.
 */
class MainDesktopBackgroundMediaTest {

    // ── Which decoder, if any, the root hosts ───────────────────────────────────

    @Test
    fun `audio playing away from the Media tab is hosted here`() {
        assertTrue(shouldHostBackgroundAudio(isAudioFile = true, isPlaying = true, currentTab = Tabs.SONGS))
    }

    @Test
    fun `audio is left to the Media tab while the operator is on it`() {
        // The tab hosts its own player; two decoders on one file is the thing being avoided.
        assertFalse(shouldHostBackgroundAudio(isAudioFile = true, isPlaying = true, currentTab = Tabs.MEDIA))
    }

    @Test
    fun `paused audio needs nothing kept alive`() {
        assertFalse(shouldHostBackgroundAudio(isAudioFile = true, isPlaying = false, currentTab = Tabs.SONGS))
    }

    @Test
    fun `a video is not hosted by the audio player`() {
        assertFalse(shouldHostBackgroundAudio(isAudioFile = false, isPlaying = true, currentTab = Tabs.SONGS))
    }

    @Test
    fun `no media at all hosts nothing`() {
        assertFalse(shouldHostBackgroundAudio(isAudioFile = null, isPlaying = null, currentTab = Tabs.SONGS))
        assertFalse(shouldHostBackgroundVideo(isAudioFile = null, isLoaded = null, currentTab = Tabs.SONGS))
    }

    @Test
    fun `a loaded video away from the Media tab is hosted here`() {
        assertTrue(shouldHostBackgroundVideo(isAudioFile = false, isLoaded = true, currentTab = Tabs.BIBLE))
    }

    @Test
    fun `a paused video is still hosted, because its frame is still on screen`() {
        // Loaded, not playing: the decoder exists to keep a frame up, which a paused video needs.
        assertTrue(shouldHostBackgroundVideo(isAudioFile = false, isLoaded = true, currentTab = Tabs.BIBLE))
    }

    @Test
    fun `video is left to the Media tab while the operator is on it`() {
        assertFalse(shouldHostBackgroundVideo(isAudioFile = false, isLoaded = true, currentTab = Tabs.MEDIA))
    }

    @Test
    fun `an unloaded video hosts nothing`() {
        assertFalse(shouldHostBackgroundVideo(isAudioFile = false, isLoaded = false, currentTab = Tabs.BIBLE))
    }

    @Test
    fun `the two decoders are never both wanted`() {
        // Mutually exclusive by construction — one file cannot be audio and not audio at once.
        Tabs.entries.forEach { tab ->
            listOf(true, false).forEach { audio ->
                assertFalse(
                    shouldHostBackgroundAudio(audio, true, tab) && shouldHostBackgroundVideo(audio, true, tab),
                    "both decoders wanted for audio=$audio on $tab",
                )
            }
        }
    }

    // ── Following a section chosen somewhere else ───────────────────────────────

    @Test
    fun `a different section arriving while songs are live is followed`() {
        assertTrue(shouldFollowRemoteSection(Presenting.LYRICS, selectedSectionIndex = 0, incomingSectionIndex = 2))
    }

    @Test
    fun `the section already selected is not followed again`() {
        // Otherwise every emission writes the selection back over itself.
        assertFalse(shouldFollowRemoteSection(Presenting.LYRICS, selectedSectionIndex = 2, incomingSectionIndex = 2))
    }

    @Test
    fun `a section is not followed when songs are not what is live`() {
        assertFalse(shouldFollowRemoteSection(Presenting.BIBLE, selectedSectionIndex = 0, incomingSectionIndex = 2))
        assertFalse(shouldFollowRemoteSection(Presenting.NONE, selectedSectionIndex = 0, incomingSectionIndex = 2))
    }

    // ── Guards ──────────────────────────────────────────────────────────────────

    @Test
    fun `either bible signal invalidates the cache`() {
        assertTrue(shouldInvalidateBibleCache(bibleUpdatedSignal = 1, secondaryBibleUpdatedSignal = 0))
        assertTrue(shouldInvalidateBibleCache(bibleUpdatedSignal = 0, secondaryBibleUpdatedSignal = 1))
        assertTrue(shouldInvalidateBibleCache(bibleUpdatedSignal = 3, secondaryBibleUpdatedSignal = 2))
    }

    @Test
    fun `no signal leaves the cached bible alone`() {
        assertFalse(shouldInvalidateBibleCache(bibleUpdatedSignal = 0, secondaryBibleUpdatedSignal = 0))
    }

    @Test
    fun `a slide index is only valid inside the deck`() {
        assertTrue(isValidSlideIndex(index = 0, slideCount = 3))
        assertTrue(isValidSlideIndex(index = 2, slideCount = 3))
        assertFalse(isValidSlideIndex(index = 3, slideCount = 3))
        assertFalse(isValidSlideIndex(index = -1, slideCount = 3))
    }

    @Test
    fun `no deck means no valid index`() {
        assertFalse(isValidSlideIndex(index = 0, slideCount = 0))
    }

    @Test
    fun `a picture folder is loadable only when it is a folder that exists`() {
        val dir = Files.createTempDirectory("cp-main-desktop-pictures").toFile()
        try {
            assertTrue(isLoadablePictureFolder(dir))

            val file = java.io.File(dir, "not-a-folder.png").apply { writeText("x") }
            assertFalse(isLoadablePictureFolder(file), "a file is not a folder to load")
            assertFalse(isLoadablePictureFolder(java.io.File(dir, "missing")), "a folder that is gone")
        } finally {
            dir.deleteRecursively()
        }
    }
}
