package org.churchpresenter.songs

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SongFolderWatcherCoverageTest {

    private lateinit var dir: File
    private val parent = Job()
    private val scope = CoroutineScope(parent)

    @BeforeTest
    fun createDir() {
        dir = Files.createTempDirectory("cp-watcher-coverage").toFile()
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
        dir.deleteRecursively()
    }

    private fun awaitChildren() = runBlocking {
        withTimeout(5_000) { parent.children.toList().forEach { it.join() } }
    }

    @Test
    fun `a folder that is not there is not watched`() {
        SongFolderWatcher(scope) {}.watchDirectory(File(dir, "missing"))
        assertEquals(0, parent.children.count())
    }

    @Test
    fun `a file is not watched as a folder`() {
        val file = File(dir, "a.song").apply { writeText("x") }
        SongFolderWatcher(scope) {}.watchDirectory(file)
        assertEquals(0, parent.children.count())
    }

    @Test
    fun `disposing a watcher ends its watch`() {
        File(dir, "Hymnal").mkdirs()
        val watcher = SongFolderWatcher(scope) {}
        watcher.watchDirectory(dir)
        assertEquals(1, parent.children.count())
        watcher.dispose()
        awaitChildren()
        assertTrue(parent.children.none { it.isActive })
    }

    @Test
    fun `watching again ends the previous watch`() {
        val watcher = SongFolderWatcher(scope) {}
        watcher.watchDirectory(dir)
        val first = parent.children.single()
        watcher.watchDirectory(dir)
        runBlocking { withTimeout(5_000) { first.join() } }
        assertTrue(first.isCancelled)
        watcher.dispose()
        awaitChildren()
    }

    @Test
    fun `disposing a watcher that never watched is harmless`() {
        SongFolderWatcher(scope) {}.dispose()
        assertEquals(0, parent.children.count())
    }
}
