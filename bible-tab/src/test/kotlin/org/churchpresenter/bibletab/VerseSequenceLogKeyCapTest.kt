package org.churchpresenter.bibletab

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VerseSequenceLogKeyCapTest {

    private val dir = Files.createTempDirectory("cp-verse-seq-cap").toFile()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun `past the key limit the least-followed sources are dropped first`() {
        val store = File(dir, "verse_sequences.json")
        val strong = (1..VerseSequenceLog.MAX_KEYS).joinToString(",") { i ->
            "\"${packRef(1, 1 + i / 900, 1 + i % 900)}\":{\"043003016\":5}"
        }
        store.writeText("""{"v":1,"p":{"002002002":{"043003016":1},$strong},"t":{},"a":0}""")

        val log = VerseSequenceLog(store, clock = { 1_000L })
        log.recordGoLive(19, 23, 1)

        val pairs = log.snapshot().pairs
        assertEquals(VerseSequenceLog.MAX_KEYS, pairs.size)
        assertFalse("002002002" in pairs, "the one source followed only once goes")
        assertTrue(pairs.values.all { it.values.sum() == 5 })
    }
}
