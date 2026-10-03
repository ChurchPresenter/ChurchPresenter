package org.churchpresenter.bibletab

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VerseSequenceLogMalformedStoreTest {

    private lateinit var dir: File
    private lateinit var store: File

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("cp-verse-seq-bad").toFile()
        store = File(dir, "nested/verse_sequences.json")
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `a reference with any part out of range is not packed`() {
        assertNull(packRef(0, 1, 1))
        assertNull(packRef(1000, 1, 1))
        assertNull(packRef(1, 0, 1))
        assertNull(packRef(1, 1000, 1))
        assertNull(packRef(1, 1, 0))
        assertNull(packRef(1, 1, 1000))
        assertEquals("043003016", packRef(43, 3, 16))
    }

    @Test
    fun `only nine digits unpack`() {
        assertNull(unpackRef("43003016"))
        assertNull(unpackRef("04300301x"))
        assertEquals(Triple(43, 3, 16), unpackRef("043003016"))
    }

    @Test
    fun `an out-of-range verse is neither recorded nor asked about`() {
        val log = VerseSequenceLog(store) { 0L }

        log.recordGoLive(0, 1, 1)

        assertNull(log.snapshot().last)
        assertTrue(log.successors(1, 1, 0).isEmpty())
    }

    @Test
    fun `the first go-live creates the store's folder`() {
        VerseSequenceLog(store) { 0L }.recordGoLive(43, 3, 16)

        assertTrue(store.exists())
    }

    @Test
    fun `a stored target that is not a reference is never suggested`() {
        store.parentFile.mkdirs()
        store.writeText("""{"v":1,"p":{"043003016":{"bogus":5,"045005008":3}},"t":{},"l":null,"a":0}""")

        val suggested = VerseSequenceLog(store) { 0L }.successors(43, 3, 16)

        assertEquals(listOf(LearnedRef(45, 5, 8, 3)), suggested)
    }

    @Test
    fun `a stored previous verse that is not a reference links to nothing`() {
        store.parentFile.mkdirs()
        store.writeText("""{"v":1,"p":{},"t":{},"l":"bogus","a":0}""")
        val log = VerseSequenceLog(store) { 1L }

        log.recordGoLive(43, 3, 16)

        assertTrue(log.snapshot().pairs.isEmpty())
        assertEquals("043003016", log.snapshot().last)
    }

    @Test
    fun `an unreadable store starts empty`() {
        store.parentFile.mkdirs()
        store.writeText("not json")

        assertTrue(VerseSequenceLog(store) { 0L }.snapshot().pairs.isEmpty())
    }
}
