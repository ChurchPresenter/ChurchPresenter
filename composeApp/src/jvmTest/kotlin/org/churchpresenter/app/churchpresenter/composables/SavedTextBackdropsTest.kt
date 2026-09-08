package org.churchpresenter.app.churchpresenter.composables

import kotlinx.serialization.json.Json
import org.churchpresenter.core.models.text.TextBackdrop
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The operator's own presets: what goes in the row, in what order, and what survives a restart.
 *
 * The store is a process-wide singleton over one file under the fake home, so each test clears both
 * before and after — the same shape [ColorPickerDialogTest] uses for [RecentColors].
 */
class SavedTextBackdropsTest {

    private val file = File(System.getProperty("user.home"), ".churchpresenter/saved_backdrops.json")

    private fun look(opacity: Int) = TextBackdrop(lineBackground = true, lineBackgroundOpacity = opacity)

    @BeforeTest
    fun fresh() {
        file.delete()
        SavedTextBackdrops.looks.clear()
    }

    @AfterTest
    fun cleanup() {
        file.delete()
        SavedTextBackdrops.looks.clear()
    }

    // ── Adding ────────────────────────────────────────────────────────────────

    @Test
    fun `a saved look goes to the front`() {
        SavedTextBackdrops.add(look(10))
        SavedTextBackdrops.add(look(20))
        assertEquals(listOf(20, 10), SavedTextBackdrops.looks.map { it.lineBackgroundOpacity })
    }

    @Test
    fun `an empty look is not a look and is not stored`() {
        SavedTextBackdrops.add(TextBackdrop())
        assertTrue(SavedTextBackdrops.looks.isEmpty(), "Off is not something to keep in the row")
    }

    @Test
    fun `saving the same look twice moves it rather than duplicating it`() {
        SavedTextBackdrops.add(look(10))
        SavedTextBackdrops.add(look(20))
        SavedTextBackdrops.add(look(10))
        assertEquals(listOf(10, 20), SavedTextBackdrops.looks.map { it.lineBackgroundOpacity })
    }

    @Test
    fun `a look identical in every field is the same look`() {
        val border = TextBackdrop(border = true, borderWidth = 4)
        SavedTextBackdrops.add(border)
        SavedTextBackdrops.add(border.copy())
        assertEquals(1, SavedTextBackdrops.looks.size, "deduplicated by content, not by identity")
    }

    @Test
    fun `the row holds at most MAX looks`() {
        repeat(SavedTextBackdrops.MAX + 4) { SavedTextBackdrops.add(look(it + 1)) }
        assertEquals(SavedTextBackdrops.MAX, SavedTextBackdrops.looks.size)
    }

    @Test
    fun `the oldest look is the one that falls off the end`() {
        repeat(SavedTextBackdrops.MAX + 1) { SavedTextBackdrops.add(look(it + 1)) }
        assertFalse(
            SavedTextBackdrops.looks.any { it.lineBackgroundOpacity == 1 },
            "the first look saved is the first to go",
        )
        assertEquals(
            SavedTextBackdrops.MAX + 1,
            SavedTextBackdrops.looks.first().lineBackgroundOpacity,
            "and the newest is still at the front",
        )
    }

    @Test
    fun `two rows of four is what the dialog has room for`() {
        assertEquals(8, SavedTextBackdrops.MAX)
    }

    // ── Persisting ────────────────────────────────────────────────────────────

    @Test
    fun `saving writes the file`() {
        SavedTextBackdrops.add(look(37))
        assertTrue(file.exists(), "a saved preset must reach disk")
        assertTrue(file.readText().contains("37"), "and must carry what was saved")
    }

    @Test
    fun `what was saved comes back after a reload`() {
        SavedTextBackdrops.add(look(11))
        SavedTextBackdrops.add(look(22))
        SavedTextBackdrops.looks.clear()

        SavedTextBackdrops.load()
        assertEquals(listOf(22, 11), SavedTextBackdrops.looks.map { it.lineBackgroundOpacity })
    }

    @Test
    fun `a reload replaces whatever was in memory`() {
        SavedTextBackdrops.add(look(11))
        SavedTextBackdrops.looks.add(look(99))

        SavedTextBackdrops.load()
        assertEquals(listOf(11), SavedTextBackdrops.looks.map { it.lineBackgroundOpacity })
    }

    @Test
    fun `an empty entry on disk is dropped on the way in`() {
        file.parentFile?.mkdirs()
        file.writeText(Json.encodeToString(listOf(TextBackdrop(), look(5))))

        SavedTextBackdrops.load()
        assertEquals(listOf(5), SavedTextBackdrops.looks.map { it.lineBackgroundOpacity })
    }

    @Test
    fun `a file holding more than MAX is truncated on the way in`() {
        file.parentFile?.mkdirs()
        file.writeText(Json.encodeToString((1..SavedTextBackdrops.MAX + 5).map { look(it) }))

        SavedTextBackdrops.load()
        assertEquals(SavedTextBackdrops.MAX, SavedTextBackdrops.looks.size)
    }

    @Test
    fun `no file at all leaves the row as it was`() {
        SavedTextBackdrops.looks.add(look(3))
        file.delete()

        SavedTextBackdrops.load()
        assertEquals(listOf(3), SavedTextBackdrops.looks.map { it.lineBackgroundOpacity })
    }

    @Test
    fun `an unreadable file loses the row rather than the app`() {
        SavedTextBackdrops.looks.add(look(3))
        file.parentFile?.mkdirs()
        file.writeText("{ not json at all")

        SavedTextBackdrops.load()
        assertEquals(listOf(3), SavedTextBackdrops.looks.map { it.lineBackgroundOpacity })
    }

    @Test
    fun `a field written by a later version does not throw the row away`() {
        file.parentFile?.mkdirs()
        file.writeText("""[{"lineBackground":true,"lineBackgroundOpacity":64,"somethingNew":"x"}]""")

        SavedTextBackdrops.load()
        assertEquals(listOf(64), SavedTextBackdrops.looks.map { it.lineBackgroundOpacity })
    }
}
