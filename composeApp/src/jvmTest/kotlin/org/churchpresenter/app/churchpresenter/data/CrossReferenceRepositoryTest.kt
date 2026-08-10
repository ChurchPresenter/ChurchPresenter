package org.churchpresenter.app.churchpresenter.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
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
    fun `a caller arriving mid-load waits for it instead of seeing an empty index`() = runTest {
        // The panel resolves its rows the instant ensureLoaded returns, so a second caller that
        // returned early while the first load was still in flight would render "no cross
        // references" for a verse that has them — and nothing re-runs to correct it until the
        // selection changes again.
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var loads = 0
        val repository = CrossReferenceRepository {
            loads++
            started.complete(Unit)
            release.await()
            fixture.toByteArray()
        }

        val first = launch { repository.ensureLoaded() }
        started.await() // the load is genuinely in flight — not merely scheduled

        val second = async { repository.ensureLoaded(); repository.forVerse(43, 3, 16).size }
        runCurrent() // second gets to run as far as it can while the load is still blocked

        release.complete(Unit)
        assertEquals(2, second.await(), "the second caller must see the loaded index")
        first.join()
        assertEquals(1, loads, "waiting, not a second read of the 3 MB file")
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

    // ── Aggregating a reading ─────────────────────────────────────────────────

    @Test
    fun `references are grouped into the passages they land in`() {
        // Three read verses, all pointing into Luke 3 at different places.
        val read = listOf(
            listOf(CrossRef(42, 3, 23)),
            listOf(CrossRef(42, 3, 31)),
            listOf(CrossRef(42, 3, 38)),
        )

        assertEquals(
            listOf(PassageRef(42, 3, startVerse = 23, endVerse = 38, sourceCount = 3)),
            aggregateCrossRefs(read, limit = 8),
            "one passage spanning what the reading pointed at, not three separate verses",
        )
    }

    @Test
    fun `a passage only one verse points at keeps a single-verse label`() {
        val read = listOf(listOf(CrossRef(42, 3, 23)))

        assertEquals(
            listOf(PassageRef(42, 3, startVerse = 23, endVerse = null, sourceCount = 1)),
            aggregateCrossRefs(read, limit = 8),
        )
    }

    @Test
    fun `what more of the reading agrees on ranks higher`() {
        val read = listOf(
            listOf(CrossRef(42, 3, 23), CrossRef(1, 5, 1)),
            listOf(CrossRef(42, 3, 24)),
            listOf(CrossRef(42, 3, 25)),
        )

        assertEquals(
            listOf(42 to 3, 1 to 5),
            aggregateCrossRefs(read, limit = 8).map { it.bookId to it.chapter },
        )
    }

    @Test
    fun `one verse citing a chapter many times does not outrank a passage that agrees`() {
        val read = listOf(
            // A single verse enthusiastic about Genesis 5.
            (1..6).map { CrossRef(1, 5, it) },
            listOf(CrossRef(42, 3, 23)),
            listOf(CrossRef(42, 3, 24)),
        )

        val aggregated = aggregateCrossRefs(read, limit = 8)
        assertEquals(42, aggregated.first().bookId, "two verses agreeing beat one verse repeating")
        assertEquals(2, aggregated.first().sourceCount)
        assertEquals(1, aggregated[1].sourceCount, "six references from one verse still count once")
    }

    @Test
    fun `equal agreement comes out in Bible order`() {
        val read = listOf(listOf(CrossRef(45, 5, 8), CrossRef(1, 1, 1), CrossRef(19, 23, 1)))

        assertEquals(
            listOf(1, 19, 45),
            aggregateCrossRefs(read, limit = 8).map { it.bookId },
        )
    }

    @Test
    fun `a target range widens the passage it lands in`() {
        val read = listOf(listOf(CrossRef(42, 3, 23, endVerse = 38)))

        val passage = aggregateCrossRefs(read, limit = 8).single()
        assertEquals(23, passage.startVerse)
        assertEquals(38, passage.endVerse)
    }

    @Test
    fun `aggregation stops at the limit`() {
        val read = listOf((1..20).map { CrossRef(it, 1, 1) })

        assertEquals(5, aggregateCrossRefs(read, limit = 5).size)
        assertEquals(emptyList(), aggregateCrossRefs(emptyList(), limit = 5))
    }

    @Test
    fun `chapters of the same book stay separate passages`() {
        val read = listOf(listOf(CrossRef(42, 2, 1), CrossRef(42, 3, 23)))

        assertEquals(
            listOf(2, 3),
            aggregateCrossRefs(read, limit = 8).map { it.chapter },
            "Luke 2 and Luke 3 are different places to turn to",
        )
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
