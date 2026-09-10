package org.churchpresenter.settings

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The backup copies settings loading leaves behind.
 *
 * One `settings.json.v<n>.bak` is written per schema version a machine has ever migrated through
 * and one `settings.json.corrupt-<stamp>` per failed load, each a full copy of the document and
 * neither ever cleaned. A real installation was carrying `.v0.bak`, `.v6.bak` and a legacy `.bak`
 * from a build that no longer exists — through every backup and every sync, for ever.
 *
 * `SettingsManager` resolves its paths from `user.home` at construction, so the home is swapped
 * before one is built.
 */
class SettingsBackupPruneTest {

    private lateinit var home: File
    private var realHome: String? = null

    @BeforeTest
    fun isolateHome() {
        realHome = System.getProperty("user.home")
        home = Files.createTempDirectory("cp-backup-prune-test").toFile()
        System.setProperty("user.home", home.absolutePath)
    }

    @AfterTest
    fun restoreHome() {
        realHome?.let { System.setProperty("user.home", it) }
        home.deleteRecursively()
    }

    private val appDir: File get() = File(home, ".churchpresenter").also { it.mkdirs() }

    /** A backup written [minutesAgo] minutes ago, so "newest three" has something to sort on. */
    private fun backup(name: String, minutesAgo: Long): File =
        File(appDir, name).apply {
            writeText("""{"settingsVersion":1}""")
            setLastModified(System.currentTimeMillis() - minutesAgo * 60_000)
        }

    /** Files only: constructing a SettingsManager also makes its `lottie_presets` directory. */
    private fun namesIn(dir: File): Set<String> =
        dir.listFiles().orEmpty().filter { it.isFile }.map { it.name }.toSet()

    @Test
    fun `only the newest three schema backups survive`() {
        val kept = (1..3L).map { backup("settings.json.v$it.bak", minutesAgo = it) }
        val dropped = (4..8L).map { backup("settings.json.v$it.bak", minutesAgo = it * 10) }

        SettingsManager().pruneBackups()

        assertEquals(kept.map { it.name }.toSet(), namesIn(appDir))
        assertTrue(dropped.none { it.exists() })
    }

    @Test
    fun `the legacy unversioned backup ages out with the rest`() {
        backup("settings.json.bak", minutesAgo = 500)
        val newer = (1..3L).map { backup("settings.json.v$it.bak", minutesAgo = it) }

        SettingsManager().pruneBackups()

        assertEquals(newer.map { it.name }.toSet(), namesIn(appDir))
    }

    @Test
    fun `corrupt copies are pruned as their own family`() {
        // Three of each: both families are at their limit, so nothing goes.
        val backups = (1..3L).map { backup("settings.json.v$it.bak", minutesAgo = it) }
        val corrupt = (1..3L).map { backup("settings.json.corrupt-2026010$it-000000", minutesAgo = it) }
        val oldCorrupt = backup("settings.json.corrupt-20250101-000000", minutesAgo = 9_999)

        SettingsManager().pruneBackups()

        assertEquals((backups + corrupt).map { it.name }.toSet(), namesIn(appDir))
        assertTrue(!oldCorrupt.exists(), "a fourth corrupt copy must go, even with three backups kept")
    }

    @Test
    fun `the live settings file is never a candidate`() {
        val settings = File(appDir, "settings.json").apply { writeText("""{"settingsVersion":9}""") }
        val tmp = File(appDir, "settings.json.tmp").apply { writeText("scratch") }
        (1..5L).forEach { backup("settings.json.v$it.bak", minutesAgo = it * 10) }

        SettingsManager().pruneBackups()

        assertTrue(settings.exists(), "the settings themselves must never be pruned")
        assertTrue(tmp.exists(), "only .bak and corrupt- copies are backups")
    }

    @Test
    fun `fewer backups than the limit are all kept`() {
        val two = (1..2L).map { backup("settings.json.v$it.bak", minutesAgo = it) }

        SettingsManager().pruneBackups()

        assertEquals(two.map { it.name }.toSet(), namesIn(appDir))
    }

    @Test
    fun `pruning an app directory that does not exist does not throw`() {
        File(home, ".churchpresenter").deleteRecursively()

        SettingsManager().pruneBackups()
    }
}
