package engine.version

import engine.Config
import engine.bible.Script
import java.util.concurrent.Executor
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * That version scoring never runs on the caller's thread.
 *
 * The caller is the single `ble-detection` thread, which has to turn speech into an on-screen verse
 * during a live service. Scoring a verse costs a seek into every indexed bible, so it must not be
 * allowed anywhere near that path — not on a cache miss, and not when the work is backing up.
 */
class VersionAsyncTest {

    /** Holds submitted work until told to run it, so "did it run yet?" is directly observable. */
    private class ManualExecutor : Executor {
        val queued = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { queued.add(command) }
        fun runAll() { while (queued.isNotEmpty()) queued.removeFirst().run() }
    }

    private val corpus = MapVersionCorpus(
        mapOf(
            MATT_18_13 to listOf(candidate("KJV", KJV_MATT_18_13), candidate("NASB", NASB_MATT_18_13)),
            "B040C018V014" to listOf(candidate("KJV", KJV_MATT_18_14), candidate("NASB", NASB_MATT_18_14)),
        )
    )

    private val spoken13 = "if it turns out that he finds it truly i say to you he rejoices over it " +
        "more than over the ninety nine which have not gone astray"
    private val spoken14 = "so it is not the will of your father who is in heaven that one of these " +
        "little ones perish"

    private var enabled = true
    private var minVerses = 2

    @BeforeTest fun snapshot() {
        enabled = Config.versionDetectionEnabled
        minVerses = Config.versionMinVerses
    }

    @AfterTest fun restore() {
        Config.versionDetectionEnabled = enabled
        Config.versionMinVerses = minVerses
    }

    /** The anchor must be that verse's own rendering — it is the language reference for the filter. */
    private fun VersionDetector.read(code: String, spoken: String) = observe(
        code, bookId = 40, chapter = 18,
        anchorText = if (code == MATT_18_13) KJV_MATT_18_13 else KJV_MATT_18_14,
        spoken = spoken, script = Script.LATIN,
    )

    @Test fun `observe does no scoring on the calling thread`() {
        val exec = ManualExecutor()
        val d = VersionDetector({ corpus }, { 1_000L }, {}, exec)

        d.read(MATT_18_13, spoken13)
        d.read("B040C018V014", spoken14)
        assertEquals(2, exec.queued.size, "the work should be queued, not done")
        assertNull(d.verdict(), "nothing can be known before the queued work runs")

        exec.runAll()
        assertEquals("NASB", assertNotNull(d.verdict()).label)
    }

    @Test fun `a corpus that throws cannot break the caller or the detector`() {
        // A bible file replaced mid-service makes reads fail; that must not poison the thread.
        val exploding = object : VersionCorpus {
            override fun rendering(code: String): List<VersionCandidate> = error("disk gone")
            override val labels = emptyList<String>()
        }
        val exec = ManualExecutor()
        val d = VersionDetector({ exploding }, { 1_000L }, {}, exec)
        d.read(MATT_18_13, spoken13)
        exec.runAll()
        assertNull(d.verdict())
    }

    @Test fun `a full queue drops work rather than running it on the caller`() {
        // The whole point of the bounded queue: under load version detection gives up, it does not
        // borrow the detection thread. A rejecting executor stands in for a saturated one.
        var ranInline = false
        val rejecting = Executor { throw java.util.concurrent.RejectedExecutionException() }
        val d = VersionDetector({ corpus }, { 1_000L }, { ranInline = true }, rejecting)

        d.read(MATT_18_13, spoken13)   // must not throw
        assertFalseFlag(ranInline)
        assertNull(d.verdict())
    }

    @Test fun `the real detector runs work off the caller thread`() {
        // The default executor, exercised for real. Waits on a positive signal from the callback —
        // never on a timeout — so it is fast and cannot flake.
        val done = java.util.concurrent.CountDownLatch(1)
        var scoringThread: String? = null
        val d = VersionDetector({ corpus }, { 1_000L }, { scoringThread = Thread.currentThread().name; done.countDown() })
        try {
            d.read(MATT_18_13, spoken13)
            d.read("B040C018V014", spoken14)
            assertTrue(done.await(5, java.util.concurrent.TimeUnit.SECONDS), "verdict callback never fired")
            assertEquals("ble-version", scoringThread, "scoring must not happen on the caller's thread")
        } finally {
            d.shutdown()
        }
    }

    private fun assertFalseFlag(v: Boolean) = assertTrue(!v, "work must not run on the calling thread")
}
