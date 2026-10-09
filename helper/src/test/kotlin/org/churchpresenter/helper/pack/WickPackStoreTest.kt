package org.churchpresenter.helper.pack

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The pack store over a cache in a folder of its own. The HTTPS fetch itself is not exercised — it needs the
 * network; everything around it runs through the [WickPackStore] `fetch` parameter.
 */
class WickPackStoreTest {

    private val dir = createTempDirectory("wick-pack").toFile()
    private val cache = File(dir, "cache/wick-pack.json")
    private var now = 10L * DAY
    private val fetched = mutableListOf<String>()
    private var answer: String? = null

    private val store = WickPackStore(
        cacheFile = { cache },
        fetch = { url -> fetched += url; answer },
        nowMillis = { now },
    )

    private fun pack(version: Int, minApp: String = "26.0.0") =
        Json.encodeToString(PackFile.serializer(), PackFile(version, minApp))

    private fun cached(version: Int, age: Long) {
        cache.parentFile.mkdirs()
        cache.writeText(pack(version))
        cache.setLastModified(now - age)
    }

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun `with no cache, the pack is fetched, used and cached`() {
        answer = pack(3)
        store.refreshNow(APP, WICK_PACK_URL)
        assertEquals(listOf(WICK_PACK_URL), fetched)
        assertEquals(3, store.current.value?.version)
        assertEquals("3", store.versionLabel)
        assertEquals(pack(3), cache.readText())
    }

    @Test
    fun `a cache less than a day old is used without asking again`() {
        cached(2, age = DAY / 2)
        answer = pack(3)
        store.refreshNow(APP, WICK_PACK_URL)
        assertEquals(emptyList(), fetched)
        assertEquals(2, store.current.value?.version)
    }

    @Test
    fun `offline, a day-old cache is still used`() {
        cached(2, age = 2 * DAY)
        store.refreshNow(APP, WICK_PACK_URL)
        assertEquals(1, fetched.size)
        assertEquals(2, store.current.value?.version)
    }

    @Test
    fun `a fetched pack that does not load leaves the cache and the pack in use alone`() {
        cached(2, age = 2 * DAY)
        answer = pack(3, minApp = "99.0.0")
        store.refreshNow(APP, WICK_PACK_URL)
        assertEquals(2, store.current.value?.version)
        assertEquals(pack(2), cache.readText())
    }

    @Test
    fun `with neither a cache nor a connection, Wick runs on its bundled data`() {
        store.refreshNow(APP, WICK_PACK_URL)
        assertNull(store.current.value)
        assertEquals("bundled", store.versionLabel)
    }

    @Test
    fun `a dev URL is read every time, cache or not`() {
        cached(2, age = 0)
        answer = pack(4)
        store.refreshNow(APP, "/some/local/pack.json")
        assertEquals(listOf("/some/local/pack.json"), fetched)
        assertEquals(4, store.current.value?.version)
    }

    @Test
    fun `a failing fetch never escapes a refresh`() {
        val failing = WickPackStore(cacheFile = { cache }, fetch = { error("no network") }, nowMillis = { now })
        runBlocking { failing.refresh(APP, WICK_PACK_URL) }
        assertNull(failing.current.value)
    }

    @Test
    fun `a local pack is read from a path or a file URL, and a missing or oversized one is not`() {
        val local = File(dir, "pack.json").apply { writeText(pack(5)) }
        assertEquals(pack(5), httpGet(local.absolutePath))
        assertEquals(pack(5), httpGet(local.toURI().toString()))
        assertNull(httpGet(File(dir, "missing.json").absolutePath))
        val big = File(dir, "big.json").apply { writeText(" ".repeat(MAX_PACK_BYTES + 1)) }
        assertNull(httpGet(big.absolutePath))
    }

    private companion object {
        const val APP = "26.15.96"
        const val DAY = 24L * 60L * 60L * 1000L
    }
}
