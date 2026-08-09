package org.churchpresenter.app.churchpresenter.data

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The bundled cross-reference dataset and the parsing that turns it into references.
 *
 * The repository is fed through its `loader` lambda here rather than the real 3 MB resource: the
 * packed-key format, the range suffix and the load-once behaviour are what this file is about, and
 * a fixture makes each of them a two-line assertion.
 *
 * The shipped dataset is not asserted on here. Parsing 245,662 links would cost more wall clock
 * than every other test in this file put together, and the suite has no precedent for reading a
 * bundled resource — `StrongsDictionaryRepositoryTest` stubs `Res.readBytes` rather than loading
 * the real dictionary. The file is instead validated where it is produced: the last thing
 * `scripts/build_cross_references.py` does is re-read its own output and check every key and
 * target against the KJV versification, so a bad regeneration fails at the point it happens.
 */
class CrossReferenceRepositoryTest {

    /** John 3:16 with plain targets, Ps 33:6 with a range, Gen 1:1 with both. */
    private val fixture = """
        {"v":1,"r":{
          "043003016":"045005008 062004009",
          "019033006":"001001001-003",
          "001001001":"043001001-003 058001010"
        }}
    """.trimIndent()

    private fun repository(json: String = fixture, onLoad: () -> Unit = {}) =
        CrossReferenceRepository { onLoad(); json.toByteArray() }

    @Test
    fun `parses plain targets in the order they are written`() = runTest {
        val repository = repository()
        repository.ensureLoaded()

        assertEquals(
            listOf(CrossRef(45, 5, 8), CrossRef(62, 4, 9)),
            repository.forVerse(43, 3, 16),
        )
    }

    @Test
    fun `parses a range suffix into endVerse`() = runTest {
        val repository = repository()
        repository.ensureLoaded()

        val ref = repository.forVerse(19, 33, 6).single()
        assertEquals(CrossRef(1, 1, 1, endVerse = 3), ref)
    }

    @Test
    fun `a verse with no references answers empty rather than failing`() = runTest {
        val repository = repository()
        repository.ensureLoaded()

        assertEquals(emptyList(), repository.forVerse(43, 3, 17))
    }

    @Test
    fun `distinguishes verses that would collide on a naive key`() = runTest {
        // 1:1001 and 2:1 both pack to a different key only because chapter is scaled by 1000.
        val repository = repository("""{"v":1,"r":{"001002001":"045005008"}}""")
        repository.ensureLoaded()

        assertEquals(emptyList(), repository.forVerse(1, 1, 1001))
        assertEquals(listOf(CrossRef(45, 5, 8)), repository.forVerse(1, 2, 1))
    }

    @Test
    fun `returns a copy so a caller holding the list cannot see the index`() = runTest {
        val repository = repository()
        repository.ensureLoaded()

        val first = repository.forVerse(43, 3, 16)
        val second = repository.forVerse(43, 3, 16)
        assertEquals(first, second)
        assertTrue(first !== second, "each call must hand out its own list")
    }

    @Test
    fun `loads once however often it is asked`() = runTest {
        var loads = 0
        val repository = repository(onLoad = { loads++ })

        repository.ensureLoaded()
        repository.ensureLoaded()
        repository.ensureLoaded()

        assertEquals(1, loads)
    }

    @Test
    fun `resetForTest makes the next load re-read`() = runTest {
        var loads = 0
        val repository = repository(onLoad = { loads++ })

        repository.ensureLoaded()
        repository.resetForTest()
        repository.ensureLoaded()

        assertEquals(2, loads)
        assertEquals(2, repository.forVerse(43, 3, 16).size)
    }

    @Test
    fun `a malformed key or target is skipped rather than taking the load down`() = runTest {
        val repository = repository(
            """{"v":1,"r":{"43316":"045005008","043003016":"nonsense 045005008"}}"""
        )
        repository.ensureLoaded()

        // The short key is dropped entirely; the bad target is dropped from an otherwise good row.
        assertEquals(listOf(CrossRef(45, 5, 8)), repository.forVerse(43, 3, 16))
    }

    @Test
    fun `a failed read leaves the repository able to try again`() = runTest {
        var attempts = 0
        val repository = CrossReferenceRepository {
            attempts++
            if (attempts == 1) throw IllegalStateException("resource missing") else fixture.toByteArray()
        }

        runCatching { repository.ensureLoaded() }
        repository.ensureLoaded()

        assertEquals(2, attempts)
        assertEquals(2, repository.forVerse(43, 3, 16).size)
    }

    @Test
    fun `formats a single verse and a range`() {
        assertEquals("Rom 5:8", formatCrossRefLabel("Rom", 5, 8, null))
        assertEquals("Ps 33:6-9", formatCrossRefLabel("Ps", 33, 6, 9))
    }

    @Test
    fun `merging interleaves the verses of a range so each contributes its strongest`() {
        val first = listOf(CrossRef(1, 1, 1), CrossRef(1, 1, 2))
        val second = listOf(CrossRef(2, 2, 1), CrossRef(2, 2, 2))

        assertEquals(
            listOf(CrossRef(1, 1, 1), CrossRef(2, 2, 1), CrossRef(1, 1, 2), CrossRef(2, 2, 2)),
            mergeCrossRefs(listOf(first, second), limit = 8),
        )
    }

    @Test
    fun `merging drops duplicates across the verses of a range`() {
        val shared = CrossRef(45, 5, 8)

        assertEquals(
            listOf(shared, CrossRef(1, 1, 1)),
            mergeCrossRefs(listOf(listOf(shared), listOf(shared, CrossRef(1, 1, 1))), limit = 8),
        )
    }

    @Test
    fun `merging stops at the limit`() {
        val refs = (1..20).map { CrossRef(1, 1, it) }

        assertEquals(5, mergeCrossRefs(listOf(refs), limit = 5).size)
        assertEquals(emptyList(), mergeCrossRefs(emptyList(), limit = 5))
    }

    @Test
    fun `a range with the same start and end is not the same reference as a single verse`() {
        // They label differently ("Ps 33:6" vs "Ps 33:6-6"), so dedupe must not conflate them.
        assertNull(CrossRef(19, 33, 6).endVerse)
        assertEquals(
            2,
            mergeCrossRefs(listOf(listOf(CrossRef(19, 33, 6), CrossRef(19, 33, 6, 6))), limit = 8).size,
        )
    }
}
