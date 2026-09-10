package org.churchpresenter.app.churchpresenter.dialogs.tabs

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.rules.TemporaryFolder

/**
 * The write probe behind the System Storage card must leave nothing behind — and must clear away
 * what an earlier run stranded, since a force-quit skips `deleteOnExit`.
 */
class SystemStorageWriteProbeTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun aged(name: String, ageMs: Long): File =
        temp.newFile(name).apply { setLastModified(System.currentTimeMillis() - ageMs) }

    @Test
    fun `writable directory probes true and is left empty`() {
        val dir = temp.newFolder("content")

        assertTrue(isWritableDir(dir))

        assertTrue(dir.listFiles().orEmpty().isEmpty(), "probe left a file behind: ${dir.list()?.toList()}")
    }

    @Test
    fun `repeated probes leave nothing behind`() {
        val dir = temp.newFolder("repeat")

        repeat(5) { assertTrue(isWritableDir(dir)) }

        assertTrue(dir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `a path that is not a directory is not writable`() {
        val notADir = temp.newFile("plain.txt")

        assertFalse(isWritableDir(notADir))
    }

    @Test
    fun `sweep removes an old probe file`() {
        val stale = aged(".cp_write_test1234.tmp", FIVE_MINUTES_MS)

        sweepStaleWriteProbes(temp.root)

        assertFalse(stale.exists())
    }

    @Test
    fun `sweep keeps a probe another instance may still be using`() {
        val fresh = temp.newFile(".cp_write_test5678.tmp")

        sweepStaleWriteProbes(temp.root)

        assertTrue(fresh.exists())
    }

    @Test
    fun `sweep keeps files that are not probes`() {
        val song = aged("song.txt", FIVE_MINUTES_MS)
        val wrongSuffix = aged(".cp_write_testfoo.png", FIVE_MINUTES_MS)
        val wrongPrefix = aged("backup.tmp", FIVE_MINUTES_MS)

        sweepStaleWriteProbes(temp.root)

        assertTrue(song.exists())
        assertTrue(wrongSuffix.exists())
        assertTrue(wrongPrefix.exists())
    }

    @Test
    fun `sweeping a directory that does not exist does not throw`() {
        sweepStaleWriteProbes(File(temp.root, "gone"))
    }

    private companion object {
        const val FIVE_MINUTES_MS = 5L * 60 * 1000
    }
}
