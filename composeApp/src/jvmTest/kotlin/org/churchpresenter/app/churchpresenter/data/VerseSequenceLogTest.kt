package org.churchpresenter.app.churchpresenter.data

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the operator tends to show after a given verse, learned from their go-lives.
 *
 * The rules worth pinning are the ones that decide what *not* to learn — a new service must not
 * be chained onto the last one, reading straight through a passage must not become a suggestion,
 * and a single transition must not be offered as a habit. Each of those is a judgement about live
 * use that is invisible in the code's happy path.
 *
 * Time is injected, so the 90-minute session boundary is asserted by moving a fake clock rather
 * than by waiting; no test here sleeps. The store is a per-test temp file, so nothing touches the
 * developer's own `~/.churchpresenter`.
 */
class VerseSequenceLogTest {

    private lateinit var tempDir: File
    private lateinit var storeFile: File
    private var now = 1_700_000_000_000L

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("verse-sequences").toFile()
        storeFile = File(tempDir, "verse_sequences.json")
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun log(file: File = storeFile) = VerseSequenceLog(file) { now }

    /** Advances the fake clock by [minutes], the way a service does between go-lives. */
    private fun advance(minutes: Long) {
        now += minutes * 60_000L
    }

    // John 3:16, Romans 6:23, Ephesians 2:8 — the sequence a gospel presentation actually uses.
    private val john316 = Triple(43, 3, 16)
    private val romans623 = Triple(45, 6, 23)
    private val ephesians28 = Triple(49, 2, 8)

    private fun VerseSequenceLog.goLive(ref: Triple<Int, Int, Int>) =
        recordGoLive(ref.first, ref.second, ref.third)

    private fun VerseSequenceLog.after(ref: Triple<Int, Int, Int>) =
        successors(ref.first, ref.second, ref.third)

    // ── Learning ──────────────────────────────────────────────────────────────

    @Test
    fun `a transition seen twice becomes a suggestion`() {
        val log = log()

        repeat(2) {
            log.goLive(john316)
            advance(2)
            log.goLive(romans623)
            advance(2)
        }

        val suggestions = log.after(john316)
        assertEquals(1, suggestions.size)
        assertEquals(LearnedRef(45, 6, 23, count = 2), suggestions.single())
    }

    @Test
    fun `a transition seen once is withheld`() {
        val log = log()

        log.goLive(john316)
        advance(2)
        log.goLive(romans623)

        assertEquals(emptyList(), log.after(john316))
        // It is recorded, just not offered — a second sighting promotes it.
        assertEquals(mapOf("045006023" to 1), log.snapshot().pairs["043003016"])
    }

    @Test
    fun `the more frequent successor ranks first`() {
        val log = log()

        repeat(3) {
            log.goLive(john316); advance(2); log.goLive(romans623); advance(2)
        }
        repeat(2) {
            log.goLive(john316); advance(2); log.goLive(ephesians28); advance(2)
        }

        assertEquals(
            listOf(LearnedRef(45, 6, 23, 3), LearnedRef(49, 2, 8, 2)),
            log.after(john316),
        )
    }

    @Test
    fun `the more recent successor wins a tie on count`() {
        val log = log()

        repeat(2) { log.goLive(john316); advance(2); log.goLive(romans623); advance(2) }
        repeat(2) { log.goLive(john316); advance(2); log.goLive(ephesians28); advance(2) }

        assertEquals(
            listOf(LearnedRef(49, 2, 8, 2), LearnedRef(45, 6, 23, 2)),
            log.after(john316),
            "equal counts should be broken by which was seen most recently",
        )
    }

    // ── What must not be learned ──────────────────────────────────────────────

    @Test
    fun `a gap longer than the session boundary does not link the two verses`() {
        val log = log()

        log.goLive(john316)
        advance(91)          // next week's service
        log.goLive(romans623)

        assertTrue(log.snapshot().pairs.isEmpty(), "services must not be chained together")
    }

    @Test
    fun `the verse after a session boundary still anchors the one after it`() {
        val log = log()

        log.goLive(john316)
        advance(91)
        log.goLive(romans623)   // dropped as a pair, but becomes the new anchor
        advance(2)
        log.goLive(ephesians28)
        advance(2)
        log.goLive(romans623)
        advance(2)
        log.goLive(ephesians28)

        assertEquals(listOf(LearnedRef(49, 2, 8, 2)), log.after(romans623))
    }

    @Test
    fun `the session boundary survives a restart`() {
        log().goLive(john316)
        advance(91)

        // A second instance over the same file is what a restarted app sees.
        log().goLive(romans623)

        assertTrue(log().snapshot().pairs.isEmpty())
    }

    @Test
    fun `a restart inside a service still links across it`() {
        log().goLive(john316)
        advance(5)
        log().goLive(romans623)
        advance(5)
        log().goLive(john316)
        advance(5)
        log().goLive(romans623)

        assertEquals(listOf(LearnedRef(45, 6, 23, 2)), log().after(john316))
    }

    @Test
    fun `reading straight through a chapter is not a suggestion`() {
        val log = log()

        // John 3:16 → 3:17 → 3:18: sequential reading, already an arrow-key away.
        log.goLive(john316); advance(1)
        log.goLive(Triple(43, 3, 17)); advance(1)
        log.goLive(Triple(43, 3, 18))

        assertTrue(log.snapshot().pairs.isEmpty())
    }

    @Test
    fun `a real jump within one chapter is learned`() {
        val log = log()

        // Three verses apart is past ADJACENT_SKIP, so it is a deliberate move.
        repeat(2) {
            log.goLive(john316); advance(1)
            log.goLive(Triple(43, 3, 19)); advance(1)
        }

        assertEquals(listOf(LearnedRef(43, 3, 19, 2)), log.after(john316))
    }

    @Test
    fun `showing the same verse twice in a row is not a transition`() {
        val log = log()

        repeat(4) { log.goLive(john316); advance(1) }

        assertTrue(log.snapshot().pairs.isEmpty())
    }

    // ── Bounds ────────────────────────────────────────────────────────────────

    @Test
    fun `successors per verse are capped, dropping the ones used longest ago`() {
        val log = log()

        for (verse in 1..12) {
            repeat(2) {
                log.goLive(john316); advance(1)
                log.goLive(Triple(45, 6, verse)); advance(1)
            }
        }

        val stored = log.snapshot().pairs.getValue("043003016")
        assertEquals(VerseSequenceLog.MAX_SUCCESSORS, stored.size)
        assertEquals(
            (5..12).map { "045006%03d".format(it) }.toSet(),
            stored.keys,
            "the eight most recently used should survive, the four oldest should not",
        )
    }

    @Test
    fun `a habit formed later still displaces one that has fallen out of use`() {
        val log = log()

        // Eight successors, each firmly established, filling every slot.
        for (verse in 1..8) {
            repeat(5) {
                log.goLive(john316); advance(1)
                log.goLive(Triple(45, 6, verse)); advance(1)
            }
        }
        // A ninth the operator starts using now. Evicting by count would drop it on arrival every
        // time — it could never reach a count of two, so a habit they have actually formed would
        // stay invisible behind ones they abandoned.
        repeat(2) {
            log.goLive(john316); advance(1)
            log.goLive(ephesians28); advance(1)
        }

        // It accumulates rather than being reset to one on each sighting. It does not yet outrank
        // habits done five times — ranking is still by count, and it should not — but it is now in
        // the store and will overtake them if the operator keeps going there.
        assertEquals(2, log.snapshot().pairs.getValue("043003016")["049002008"])
    }

    @Test
    fun `suggestions are limited even when more qualify`() {
        val log = log()

        for (verse in 1..8) {
            repeat(2) {
                log.goLive(john316); advance(1)
                log.goLive(Triple(45, 6, verse)); advance(1)
            }
        }

        assertEquals(VerseSequenceLog.MAX_SUGGESTIONS, log.after(john316).size)
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Test
    fun `what was learned survives a restart`() {
        val first = log()
        repeat(2) { first.goLive(john316); advance(2); first.goLive(romans623); advance(2) }

        assertEquals(listOf(LearnedRef(45, 6, 23, 2)), log().after(john316))
    }

    @Test
    fun `a corrupt store starts empty instead of throwing`() {
        storeFile.parentFile.mkdirs()
        storeFile.writeText("{not json at all")

        val log = log()
        assertTrue(log.snapshot().pairs.isEmpty())

        // And it recovers: recording works and rewrites the file.
        repeat(2) { log.goLive(john316); advance(2); log.goLive(romans623); advance(2) }
        assertEquals(listOf(LearnedRef(45, 6, 23, 2)), log().after(john316))
    }

    @Test
    fun `a store that cannot be written does not throw out of a go-live`() {
        // A directory where the file should be: every write fails, recording must not care.
        val blocked = File(tempDir, "blocked.json").also { it.mkdirs() }
        val log = VerseSequenceLog(blocked) { now }

        log.goLive(john316)
        advance(2)
        log.goLive(romans623)

        assertTrue(log.snapshot().pairs.isNotEmpty(), "in-memory learning continues regardless")
    }

    @Test
    fun `the store is created only once something is recorded`() {
        log()
        assertTrue(!storeFile.exists(), "merely opening the log must not write to disk")

        log().goLive(john316)
        assertTrue(storeFile.exists())
    }

    // ── Reference packing ─────────────────────────────────────────────────────

    @Test
    fun `references pack and unpack round trip`() {
        assertEquals("043003016", packRef(43, 3, 16))
        assertEquals(Triple(43, 3, 16), unpackRef("043003016"))
    }

    @Test
    fun `an out of range reference is refused rather than packed wrongly`() {
        assertNull(packRef(0, 1, 1))
        assertNull(packRef(43, 3, 1000))
        assertNull(unpackRef("43316"))
        assertNull(unpackRef("abcdefghi"))
    }

    @Test
    fun `an unpackable reference is not recorded`() {
        val log = log()

        log.goLive(Triple(43, 3, 16))
        advance(2)
        log.recordGoLive(0, 0, 0)

        assertEquals("043003016", log.snapshot().last, "the bad reference must not become the anchor")
    }
}
