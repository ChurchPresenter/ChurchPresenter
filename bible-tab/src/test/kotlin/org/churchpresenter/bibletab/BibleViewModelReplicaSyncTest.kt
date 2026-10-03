package org.churchpresenter.bibletab

import kotlinx.coroutines.Dispatchers
import org.churchpresenter.bible.SpbFixture
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleSyncMode
import java.io.File
import java.nio.file.Files
import java.util.Collections
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BibleViewModelReplicaSyncTest {

    private lateinit var dir: File
    private lateinit var testHome: File
    private var realHome: String? = null
    private val built = mutableListOf<BibleViewModel>()
    private val log = Collections.synchronizedList(mutableListOf<Pair<String, Map<String, Any?>>>())

    private val module = SpbFixture.buildContent(
        title = "Replica",
        books = listOf(SpbFixture.Book(43, "John", 3)),
        verses = listOf(SpbFixture.Verse(43, 3, 16, "For God so loved the world.")),
    )

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("cp-bible-replica-sync").toFile()
        realHome = System.getProperty("user.home")
        testHome = Files.createTempDirectory("cp-bible-replica-sync-home").toFile()
        System.setProperty("user.home", testHome.absolutePath)
        SpbFixture.spbFile(dir, name = "local.spb", content = module)
    }

    @AfterTest
    fun tearDown() {
        built.forEach { it.dispose() }
        realHome?.let { System.setProperty("user.home", it) }
        dir.deleteRecursively()
        testHome.deleteRecursively()
    }

    private fun follower() = BibleViewModel(
        AppSettings(bibleSettings = BibleSettings(storageDirectory = dir.absolutePath, primaryBible = "local.spb")),
        dispatcher = Dispatchers.Unconfined,
        ioDispatcher = Dispatchers.Unconfined,
        remoteSyncLog = { event, fields -> log += event to fields },
    ).also { built += it }

    private fun awaitUntil(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("timed out waiting for $what")
            Thread.yield()
        }
    }

    private fun syncResults() = synchronized(log) { log.filter { it.first == "bible_sync_result" }.map { it.second } }

    @Test
    fun `a replica of one translation has no second module`() {
        val vm = follower()

        vm.setInstanceLinkSource(
            active = true,
            mode = BibleSyncMode.FULL_REPLICA,
            fetchBibleFile = null,
            fetchSecondaryBibleFile = null,
            fetchBibleTranslations = { listOf("only.spb" to module.toByteArray()) },
        )
        awaitUntil("the replica") { vm.remoteBibleCacheFile != null }

        assertNotNull(vm.remoteBibleCacheFile)
        assertNull(vm.remoteSecondaryBibleCacheFile)
    }

    @Test
    fun `a full replica with nowhere to fetch the module from reports the failed fetch`() {
        val vm = follower()

        vm.setInstanceLinkSource(
            active = true,
            mode = BibleSyncMode.FULL_REPLICA,
            fetchBibleFile = null,
            fetchSecondaryBibleFile = null,
        )
        awaitUntil("the sync result") { syncResults().isNotEmpty() }

        assertEquals("primary_fetch_failed", syncResults().single()["reason"])
        assertNull(vm.remoteBibleCacheFile)
    }

    @Test
    fun `a replica already cached is used without fetching it again`() {
        val vm = follower()
        vm.remoteBibleCacheDir.mkdirs()
        File(vm.remoteBibleCacheDir, "primary.spb").writeText(module)
        File(vm.remoteBibleCacheDir, "secondary.spb").writeText(module)
        var fetches = 0

        vm.setInstanceLinkSource(
            active = true,
            mode = BibleSyncMode.FULL_REPLICA,
            fetchBibleFile = { fetches++; null },
            fetchSecondaryBibleFile = { fetches++; null },
        )
        awaitUntil("the sync result") { syncResults().isNotEmpty() }

        assertEquals(0, fetches)
        val result = syncResults().single()
        assertEquals(true, result["primaryDownloaded"])
        assertEquals(true, result["secondaryDownloaded"])
        assertNotNull(vm.remoteSecondaryBibleCacheFile)
    }
}
