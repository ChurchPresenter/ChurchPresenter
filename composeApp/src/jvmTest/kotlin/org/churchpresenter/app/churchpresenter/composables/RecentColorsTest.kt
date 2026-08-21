package org.churchpresenter.app.churchpresenter.composables

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [RecentColors] backs the color picker's "recent" swatch row and persists across app restarts —
 * it's a Kotlin `object`, so its `file` path is resolved once per JVM from `user.home` the first
 * time anything touches it. The Gradle test task already points the whole `jvmTest` JVM's
 * `user.home` at a throwaway `build/test-home` directory before any test runs (see
 * `composeApp/build.gradle.kts`), which is what [RecentColors] has therefore always resolved
 * against here — the same safeguard [CrashReporterTest][org.churchpresenter.diagnostics.CrashReporterTest]
 * relies on for its own `~/.churchpresenter` file, and the same reason this test manages its one
 * file/state directly rather than swapping `user.home` itself.
 *
 * `RecentColors` was widened from `private` to `internal` (and `load()` from `private` to
 * `internal`) to make this possible, per this project's stated preference for `internal` over
 * reflection to reach otherwise-private, test-worthy logic.
 */
class RecentColorsTest {

    private val file = File(System.getProperty("user.home"), ".churchpresenter/recent_colors.json")

    @BeforeTest
    fun freshState() {
        file.delete()
        RecentColors.colors.clear()
    }

    @AfterTest
    fun cleanup() {
        file.delete()
        RecentColors.colors.clear()
    }

    // ── add() ──────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `add stores the hex, uppercased, at the front`() {
        RecentColors.add("#ff0000")
        assertEquals(listOf("#FF0000"), RecentColors.colors.toList())
    }

    @Test
    fun `add moves an existing entry to the front instead of duplicating it`() {
        RecentColors.add("#FF0000")
        RecentColors.add("#00FF00")
        RecentColors.add("#ff0000")
        assertEquals(
            listOf("#FF0000", "#00FF00"),
            RecentColors.colors.toList(),
            "re-adding a color (in any case) must move it to the front, not duplicate it",
        )
    }

    @Test
    fun `add evicts the oldest entry once more than 12 colors are stored`() {
        repeat(12) { i -> RecentColors.add("#%06X".format(i)) }
        RecentColors.add("#FFFFFF")
        assertEquals(12, RecentColors.colors.size, "the list must stay capped at 12 entries")
        assertTrue("#000000" !in RecentColors.colors, "the oldest entry must be evicted once the cap is exceeded")
        assertEquals("#FFFFFF", RecentColors.colors.first(), "the newest entry must be at the front")
    }

    @Test
    fun `add persists the list to disk, readable back by load()`() {
        RecentColors.add("#123456")
        RecentColors.add("#ABCDEF")
        RecentColors.colors.clear()

        RecentColors.load()

        assertEquals(
            listOf("#ABCDEF", "#123456"),
            RecentColors.colors.toList(),
            "load() must read back exactly what add() wrote, in the same order",
        )
    }

    // ── load() ─────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `load does nothing when no file exists yet`() {
        RecentColors.load()
        assertTrue(RecentColors.colors.isEmpty(), "with no saved file, the list must stay empty")
    }

    @Test
    fun `load silently ignores a malformed file`() {
        file.parentFile.mkdirs()
        file.writeText("not valid json")

        RecentColors.load()

        assertTrue(RecentColors.colors.isEmpty(), "a malformed file must be ignored, not thrown from")
    }

    @Test
    fun `load truncates a saved file with more than 12 entries`() {
        file.parentFile.mkdirs()
        val fifteen = (1..15).joinToString(",", "[", "]") { "\"#%06X\"".format(it) }
        file.writeText(fifteen)

        RecentColors.load()

        assertEquals(12, RecentColors.colors.size, "load() must cap at 12 entries even if the file has more")
    }
}
