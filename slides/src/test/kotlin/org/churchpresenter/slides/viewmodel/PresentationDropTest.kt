package org.churchpresenter.slides.viewmodel

import org.churchpresenter.slides.pdfDeck
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PresentationDropTest {

    private val dir: File = Files.createTempDirectory("cp-presentation-drop").toFile()
    private val made = mutableListOf<PresentationViewModel>()

    @AfterTest
    fun cleanUp() {
        made.forEach { runCatching { it.dispose() } }
        dir.deleteRecursively()
    }

    private fun awaitUntil(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "timed out waiting for $what" }
            Thread.onSpinWait()
        }
    }

    @Test
    fun `the decks among the dropped files open, and nothing else`() {
        val deck = pdfDeck(dir, pages = 1)
        val notes = File(dir, "notes.txt").apply { writeText("x") }
        val opened = mutableListOf<File>()
        val vm = PresentationViewModel(null).also { made += it }

        vm.openDropped(listOf(notes, deck, dir)) { synchronized(opened) { opened += it } }
        awaitUntil("the deck to open") { synchronized(opened) { opened.isNotEmpty() } }

        assertEquals(listOf(deck), opened)
        assertEquals(listOf(deck), vm.presentations.toList())
    }

    @Test
    fun `the extension decides what counts as a deck`() {
        assertTrue(isPresentationFile(File("a.PPTX")))
        assertTrue(isPresentationFile(File("talk.key")))
        assertFalse(isPresentationFile(File("photo.jpg")))
    }

    @Test
    fun `a drop opens its decks with no one told`() {
        val deck = pdfDeck(dir, pages = 1, name = "quiet.pdf")
        val vm = PresentationViewModel(null).also { made += it }
        vm.openDropped(listOf(deck))
        awaitUntil("the deck to open") { vm.presentations.isNotEmpty() }
        assertEquals(listOf(deck), vm.presentations.toList())
    }
}
