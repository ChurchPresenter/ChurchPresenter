package org.churchpresenter.bibletab

import java.nio.file.Files
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** An Instance Link follower's cached Bible modules, written beside their place and moved in. */
class BibleCacheFileWriteTest {

    @Test
    fun `a write puts the bytes in place and leaves no part file behind`() {
        val dir = Files.createTempDirectory("bible-cache").toFile()
        val cacheFile = dir.resolve("translation-0.spb")

        writeCacheFile(cacheFile, byteArrayOf(1, 2, 3))
        writeCacheFile(cacheFile, byteArrayOf(4, 5))

        assertEquals(listOf<Byte>(4, 5), cacheFile.readBytes().toList())
        assertEquals(listOf("translation-0.spb"), dir.list()!!.toList())
        dir.deleteRecursively()
    }

    @Test
    fun `writers of the same module at once never move each other's part file`() {
        val dir = Files.createTempDirectory("bible-cache").toFile()
        val cacheFile = dir.resolve("translation-0.spb")
        val writers = 8
        val start = CyclicBarrier(writers)
        val pool = Executors.newFixedThreadPool(writers)
        try {
            val writes = (0 until writers).map { n ->
                pool.submit {
                    start.await()
                    repeat(ROUNDS) { writeCacheFile(cacheFile, ByteArray(MODULE_BYTES) { n.toByte() }) }
                }
            }
            writes.forEach { it.get(WAIT_SECONDS, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }

        val written = cacheFile.readBytes()
        assertEquals(MODULE_BYTES, written.size)
        assertTrue(written.all { it == written[0] }, "the module is one writer's whole file")
        assertEquals(listOf("translation-0.spb"), dir.list()!!.toList())
        dir.deleteRecursively()
    }

    private companion object {
        const val ROUNDS = 20
        const val MODULE_BYTES = 4096
        const val WAIT_SECONDS = 10L
    }
}
