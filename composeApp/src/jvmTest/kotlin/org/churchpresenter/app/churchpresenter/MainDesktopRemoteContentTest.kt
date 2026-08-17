package org.churchpresenter.app.churchpresenter

import org.churchpresenter.app.churchpresenter.presenter.Presenting
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The guards around content chosen from somewhere other than this machine — a phone, a linked
 * instance — plus the small resolutions the payloads need on the way in and out.
 */
class MainDesktopRemoteContentTest {

    // ── Publishing a deck when remote control is switched on ────────────────────

    @Test
    fun `an open deck with slides is published`() {
        assertTrue(shouldPublishPresentation(
            remoteControlEnabled = true,
            hasSelectedPresentation = true,
            slideCount = 12,
        ))
    }

    @Test
    fun `nothing is published while remote control is off`() {
        assertFalse(shouldPublishPresentation(
            remoteControlEnabled = false,
            hasSelectedPresentation = true,
            slideCount = 12,
        ))
    }

    @Test
    fun `nothing is published when no deck is open`() {
        assertFalse(shouldPublishPresentation(
            remoteControlEnabled = true,
            hasSelectedPresentation = false,
            slideCount = 12,
        ))
    }

    @Test
    fun `a deck with no slides is not published`() {
        // A phone handed an empty slide list has nothing it could navigate.
        assertFalse(shouldPublishPresentation(
            remoteControlEnabled = true,
            hasSelectedPresentation = true,
            slideCount = 0,
        ))
    }

    // ── Taking the output ───────────────────────────────────────────────────────

    @Test
    fun `a slide chosen while something else is live takes the output`() {
        assertTrue(shouldTakePresentationLive(Presenting.LYRICS))
        assertTrue(shouldTakePresentationLive(Presenting.NONE))
        assertTrue(shouldTakePresentationLive(Presenting.BIBLE))
    }

    @Test
    fun `a slide chosen while the deck is already live just changes slide`() {
        assertFalse(shouldTakePresentationLive(Presenting.PRESENTATION))
    }

    // ── Notes ───────────────────────────────────────────────────────────────────

    @Test
    fun `a slide's notes are read by its index`() {
        assertEquals("second", presenterNotesAt(listOf("first", "second", "third"), 1))
    }

    @Test
    fun `a slide with no notes reports none rather than failing`() {
        // A deck can carry fewer notes than slides.
        assertEquals("", presenterNotesAt(listOf("first"), 4))
        assertEquals("", presenterNotesAt(emptyList(), 0))
        assertEquals("", presenterNotesAt(listOf("first"), -1))
    }

    // ── Book ids ────────────────────────────────────────────────────────────────

    @Test
    fun `a found book reports the id its bible gives it`() {
        assertEquals(43, resolveBookIdOrZero(bookIndex = 42) { it + 1 })
    }

    @Test
    fun `a book that was not found reports no book`() {
        assertEquals(0, resolveBookIdOrZero(bookIndex = -1) { it + 1 })
    }

    @Test
    fun `a bible that cannot name the book reports no book`() {
        assertEquals(0, resolveBookIdOrZero(bookIndex = 3) { null })
    }

    // ── Picture selection ───────────────────────────────────────────────────────

    @Test
    fun `a selection from another folder switches folder first`() {
        assertTrue(shouldSwitchPictureFolder(requestedFolderId = "uploads", activeFolderId = "hymns"))
    }

    @Test
    fun `a selection from the folder already open does not switch`() {
        assertFalse(shouldSwitchPictureFolder(requestedFolderId = "hymns", activeFolderId = "hymns"))
    }

    @Test
    fun `a selection made before any folder is open switches`() {
        assertTrue(shouldSwitchPictureFolder(requestedFolderId = "hymns", activeFolderId = null))
    }

    @Test
    fun `only a file that is still there can be shown`() {
        val dir = Files.createTempDirectory("cp-main-desktop-images").toFile()
        try {
            val present = File(dir, "slide.png").apply { writeText("x") }
            assertTrue(isUsableImageFile(present))
            assertFalse(isUsableImageFile(File(dir, "deleted.png")), "a file that has since gone")
            assertFalse(isUsableImageFile(null), "nothing resolved at all")
        } finally {
            dir.deleteRecursively()
        }
    }
}
