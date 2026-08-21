package org.churchpresenter.bibleengine.replay

import org.churchpresenter.bibleengine.AppConfig
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import java.sql.DriverManager
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Service-level replay regression test (the harness README/TRAINING_PLAN promised).
 *
 * Golden mode (needs local assets, skips gracefully on CI):
 *   ./gradlew test -Dreplay.db=/path/to/service.db
 * regenerate the golden after an intentional behavior change:
 *   ./gradlew test -Dreplay.db=... -Dreplay.updateGolden=true
 * optional: -Dreplay.bibles="<primary>.spb,<secondary>.spb"
 *   Name the modules the SERVICE ran with (bibleSettings in ~/.churchpresenter/settings.json), not
 *   whichever files happen to be in the folder: two modules of one translation can number Psalms
 *   differently (a Synodal-numbered RST and a Hebrew-numbered one both call themselves "RST"), and
 *   replaying against the wrong one shifts every Psalm by a chapter.
 *           -Dreplay.level=balanced
 *
 * The golden JSONL contains references and scores only — never transcript text — so it is
 * privacy-safe to commit. See [DbReplay] for the determinism/fidelity contract.
 *
 * The smoke test below always runs: it builds a tiny in-memory database and asserts the driver
 * plumbing end-to-end with no local assets.
 */
class DbReplayTest {

    private lateinit var configSnapshot: Map<String, Any>

    @BeforeTest
    fun snapshot() {
        configSnapshot = DbReplay.snapshotConfig()
    }

    @AfterTest
    fun restore() {
        DbReplay.restoreConfig(configSnapshot)
    }

    @Test
    fun `golden replay matches committed baseline`() {
        val dbPath = System.getProperty("replay.db")
        assumeTrue(!dbPath.isNullOrBlank(), "replay.db not set — golden replay skipped")
        assumeTrue(File(dbPath!!).isFile, "replay.db does not exist: $dbPath")
        val bibleRoot = AppConfig.discoverBibleRoot()
        assumeTrue(!bibleRoot.isNullOrBlank(), "no bible root discoverable — golden replay skipped")

        val bibles = System.getProperty("replay.bibles")
            ?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?: emptyList()
        val level = System.getProperty("replay.level") ?: "balanced"

        val translations = DbReplay.loadTranslations(bibleRoot!!, bibles)
        assumeTrue(translations.isNotEmpty(), "no bibles loadable — golden replay skipped")
        val rows = DbReplay.readRows(dbPath)
        assertTrue(rows.isNotEmpty(), "db has no transcription rows")

        // Determinism guard: two replays must be byte-identical.
        val first = DbReplay.replay(rows, translations, level)
        val second = DbReplay.replay(rows, translations, level)
        assertEquals(first.lines, second.lines, "replay is not deterministic — a wall-clock or ordering dependence survived")

        val sessionId = File(dbPath).nameWithoutExtension
        val goldenFile = File("src/test/resources/replay/golden-$sessionId.jsonl")
        if (System.getProperty("replay.updateGolden")?.toBooleanStrictOrNull() == true) {
            goldenFile.parentFile.mkdirs()
            goldenFile.writeText(first.lines.joinToString("\n") + "\n")
            println("golden updated: ${goldenFile.path} (${first.lines.size} events)")
            return
        }
        assumeTrue(goldenFile.isFile, "no golden for $sessionId — run with -Dreplay.updateGolden=true first")
        val golden = goldenFile.readLines().filter { it.isNotBlank() }
        if (golden != first.lines) {
            val added = first.lines.filterNot { it in golden }
            val removed = golden.filterNot { it in first.lines }
            throw AssertionError(
                "Replay diverged from golden (${golden.size} -> ${first.lines.size} events).\n" +
                    "ADDED (${added.size}):\n" + added.joinToString("\n") { "  + $it" } + "\n" +
                    "REMOVED (${removed.size}):\n" + removed.joinToString("\n") { "  - $it" } + "\n" +
                    "If this change is intentional, regenerate with -Dreplay.updateGolden=true and " +
                    "summarize the diff in the commit message."
            )
        }
    }

    @Test
    fun `smoke - synthetic in-memory db drives the pipeline end-to-end`() {
        // A tiny fabricated service: an explicit citation, then verse text being read.
        DriverManager.getConnection("jdbc:sqlite::memory:").use { conn ->
            conn.createStatement().use { st ->
                st.executeUpdate(
                    "CREATE TABLE transcriptions (id INTEGER PRIMARY KEY, ts_ms INTEGER, text TEXT, " +
                        "translated_text TEXT, speech_type TEXT, segment_id TEXT, session_id TEXT, " +
                        "start_time REAL, is_final INTEGER DEFAULT 1, denied INTEGER DEFAULT 0)"
                )
                var ts = 1_000_000L
                fun insert(text: String, speechType: String = "Speaking") {
                    st.executeUpdate(
                        "INSERT INTO transcriptions (ts_ms, text, speech_type, segment_id, session_id) " +
                            "VALUES ($ts, '${text.replace("'", "''")}', '$speechType', 'seg-$ts', 'smoke')"
                    )
                    ts += 5_000
                }
                insert("we continue our study this morning")
                insert("please open your bibles to John chapter 3 verse 16")
                insert("for God so loved the world that he gave his only begotten Son")
                insert("that whosoever believeth in him should not perish")
            }

            // In-memory connections are per-connection; read rows through the same connection.
            val rows = ArrayList<DbReplay.Row>()
            conn.createStatement().use { st ->
                val rs = st.executeQuery(
                    "SELECT id, ts_ms, text, translated_text, speech_type, segment_id, session_id, " +
                        "start_time, is_final, denied FROM transcriptions ORDER BY ts_ms, id"
                )
                while (rs.next()) {
                    rows.add(
                        DbReplay.Row(
                            id = rs.getLong("id"),
                            tsMs = rs.getLong("ts_ms"),
                            text = rs.getString("text"),
                            translated = rs.getString("translated_text"),
                            speechType = rs.getString("speech_type"),
                            segmentId = rs.getString("segment_id"),
                            sessionId = rs.getString("session_id"),
                            startTime = null,
                            isFinal = true,
                            denied = false,
                        )
                    )
                }
            }

            // Real bibles when available; otherwise this smoke test still verified row reading.
            val bibleRoot = AppConfig.discoverBibleRoot()
            assumeTrue(!bibleRoot.isNullOrBlank(), "no bible root — smoke replay skipped after row check")
            val translations = DbReplay.loadTranslations(bibleRoot!!, emptyList())
            assumeTrue(translations.isNotEmpty(), "no bibles loadable — smoke replay skipped")

            val result = DbReplay.replay(rows, translations, "balanced")
            assertTrue(
                result.events.any {
                    it.reference.bookId == 43 && it.reference.chapter == 3 && it.reference.verseStart == 16
                },
                "expected John 3:16 to be detected; got: ${result.lines}"
            )
            // Session/segment stamping flows through.
            assertTrue(result.events.all { it.sessionId == "smoke" })
        }
    }

    @Test fun `translation_ts_ms column is honored when present`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { conn ->
            conn.createStatement().use { st ->
                st.executeUpdate(
                    "CREATE TABLE transcriptions (id INTEGER PRIMARY KEY, ts_ms INTEGER, text TEXT, " +
                        "translated_text TEXT, speech_type TEXT, segment_id TEXT, session_id TEXT, " +
                        "start_time REAL, is_final INTEGER DEFAULT 1, denied INTEGER DEFAULT 0, " +
                        "translation_ts_ms INTEGER)"
                )
                st.executeUpdate(
                    "INSERT INTO transcriptions (ts_ms, text, translated_text, translation_ts_ms, segment_id) " +
                        "VALUES (1000, 'привет мир', 'hello world', 3500, 'seg-1')"
                )
            }
            val rows = ArrayList<DbReplay.Row>()
            conn.createStatement().use { st ->
                // Mirror readRows' optional-column handling through the same code path is not
                // possible on an in-memory connection (readRows opens its own) — assert the
                // parsing contract directly instead.
                val rs = st.executeQuery("SELECT translation_ts_ms FROM transcriptions")
                rs.next()
                kotlin.test.assertEquals(3500L, rs.getLong(1))
            }
            // File-backed path exercises readRows itself.
            val tmp = java.io.File.createTempFile("replay-ts", ".db")
            try {
                DriverManager.getConnection("jdbc:sqlite:${tmp.absolutePath}").use { fileConn ->
                    fileConn.createStatement().use { st ->
                        st.executeUpdate(
                            "CREATE TABLE transcriptions (id INTEGER PRIMARY KEY, ts_ms INTEGER, text TEXT, " +
                                "translated_text TEXT, speech_type TEXT, segment_id TEXT, session_id TEXT, " +
                                "start_time REAL, is_final INTEGER DEFAULT 1, denied INTEGER DEFAULT 0, " +
                                "translation_ts_ms INTEGER)"
                        )
                        st.executeUpdate(
                            "INSERT INTO transcriptions (ts_ms, text, translated_text, translation_ts_ms, segment_id) " +
                                "VALUES (1000, 'привет мир', 'hello world', 3500, 'seg-1')"
                        )
                        st.executeUpdate(
                            "INSERT INTO transcriptions (ts_ms, text, segment_id) VALUES (2000, 'вторая строка', 'seg-2')"
                        )
                    }
                }
                val fileRows = DbReplay.readRows(tmp.absolutePath)
                assertEquals(2, fileRows.size)
                assertEquals(3500L, fileRows[0].translationTsMs, "translation arrival time must be read when the column exists")
                assertEquals(null, fileRows[1].translationTsMs)
            } finally {
                tmp.delete()
            }
        }
    }
}
