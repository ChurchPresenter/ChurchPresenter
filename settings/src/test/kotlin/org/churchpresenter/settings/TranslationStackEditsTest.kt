package org.churchpresenter.settings

import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Removing and reordering translations, with every profile's selection kept on its places.
 *
 * A profile names what it shows by position in the stack -- the primary, the secondary -- and keeps
 * showing that place whichever Bible is in it: a confidence monitor set to the secondary shows the
 * secondary after a swap. A remove that shortens the stack drops the places past its end, and a
 * profile left with none switches its scripture off rather than falling to "all of them".
 *
 * The stack is `[kjv, rst, niv]` throughout, with two profiles so it is visible that more than one
 * is covered.
 */
class TranslationStackEditsTest {

    private fun settings(
        screenSelection: List<Int> = emptyList(),
        browserSelection: List<Int> = emptyList(),
        screenMode: String = Constants.SONG_LANG_BOTH,
    ) = AppSettings(
        bibleSettings = BibleSettings().withTranslations(
            listOf("kjv.spb", "rst.spb", "niv.spb").map { BibleTranslationSettings(fileName = it) },
        ),
        projectionSettings = ProjectionSettings(
            outputProfiles = listOf(
                OutputProfile(id = "screen", bibleMode = screenMode, bibleTranslations = screenSelection),
                OutputProfile(id = "browser", bibleTranslations = browserSelection),
            ),
        ),
    )

    private fun AppSettings.screen() = projectionSettings.outputProfiles.first { it.id == "screen" }
    private fun AppSettings.browserSource() = projectionSettings.outputProfiles.first { it.id == "browser" }
    private fun AppSettings.stack() = bibleSettings.translationList().map { it.fileName }

    // ── Removing ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `removing a translation leaves every selection on its places`() {
        // kjv goes: rst becomes the primary and niv the secondary, and the screens keep showing
        // the primary and the secondary.
        val after = settings(screenSelection = listOf(0), browserSelection = listOf(1))
            .removeBibleTranslation(0)

        assertEquals(listOf("rst.spb", "niv.spb"), after.stack())
        assertEquals(listOf(0), after.screen().bibleTranslations, "the screen still shows the primary")
        assertEquals(listOf(1), after.browserSource().bibleTranslations, "and the browser source the secondary")
    }

    @Test
    fun `a multi-translation selection loses only the places past the end`() {
        val after = settings(screenSelection = listOf(0, 1, 2)).removeBibleTranslation(1)

        assertEquals(listOf(0, 1), after.screen().bibleTranslations, "the third place is gone")
    }

    @Test
    fun `an output showing only a place that is gone switches its scripture off`() {
        // Letting the selection fall empty would read as "all of them" and put both remaining
        // languages on a screen deliberately narrowed to one.
        val after = settings(screenSelection = listOf(2)).removeBibleTranslation(0)

        assertEquals(Constants.SONG_LANG_OFF, after.screen().bibleMode)
        assertEquals(emptyList(), after.screen().bibleTranslations)
    }

    @Test
    fun `an output showing all of them still shows all of them`() {
        // Empty means "all", which stays true however the stack changes.
        val after = settings().removeBibleTranslation(0)

        assertEquals(emptyList(), after.screen().bibleTranslations)
        assertEquals(Constants.SONG_LANG_BOTH, after.screen().bibleMode)
    }

    @Test
    fun `removing a position that is not in the stack changes nothing`() {
        val before = settings(screenSelection = listOf(1))

        assertEquals(before, before.removeBibleTranslation(7))
        assertEquals(before, before.removeBibleTranslation(-1), "nor does a negative index")
    }

    @Test
    fun `removing a translation keeps a profile's own order`() {
        val after = settings(screenSelection = listOf(1, 0)).removeBibleTranslation(2)

        assertEquals(listOf(1, 0), after.screen().bibleTranslations, "the secondary, then the primary")
    }

    // ── Reordering ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `moving a translation leaves every selection on its places`() {
        val after = settings(screenSelection = listOf(0), browserSelection = listOf(1, 2))
            .moveBibleTranslation(2, -2)

        assertEquals(listOf("niv.spb", "kjv.spb", "rst.spb"), after.stack())
        assertEquals(listOf(0), after.screen().bibleTranslations, "the primary is now niv")
        assertEquals(listOf(1, 2), after.browserSource().bibleTranslations)
    }

    @Test
    fun `a move that would leave the stack changes nothing`() {
        val before = settings(screenSelection = listOf(1))

        assertEquals(before, before.moveBibleTranslation(0, -1))
        assertEquals(before, before.moveBibleTranslation(2, 1))
    }

    @Test
    fun `moving a translation that is not in the stack changes nothing`() {
        val before = settings(screenSelection = listOf(1))

        assertEquals(before, before.moveBibleTranslation(9, -1), "no such row to move")
        assertEquals(before, before.moveBibleTranslation(-1, 1))
    }

    // ── Swapping ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the swap button exchanges the first two and leaves the selections on their places`() {
        // A confidence monitor set to the secondary shows the secondary after a swap.
        val after = settings(screenSelection = listOf(0), browserSelection = listOf(1))
            .swapBibleTranslations()

        assertEquals(listOf("rst.spb", "kjv.spb", "niv.spb"), after.stack())
        assertEquals(listOf(0), after.screen().bibleTranslations)
        assertEquals(listOf(1), after.browserSource().bibleTranslations)
    }

    @Test
    fun `swapping twice returns everything to where it started`() {
        val before = settings(screenSelection = listOf(0, 2), browserSelection = listOf(1))

        assertEquals(before, before.swapBibleTranslations().swapBibleTranslations())
    }

    // ── Bibles arriving from outside the Bible settings tab ─────────────────────
    //
    // Both of these used to write `primaryBible` directly. That field is a mirror of the stack kept
    // for older builds to read, so writing it alone produces a settings file with a configured bible
    // and an empty stack — which the Bible settings tab, and every stack edit, then work against.

    @Test
    fun `the bundled bible goes into the stack, not just the legacy field`() {
        val first = AppSettings().withBundledBible("/bibles", "kjv1769.spb")

        assertEquals(listOf("kjv1769.spb"), first.stack())
        assertEquals("/bibles", first.bibleSettings.storageDirectory)
        assertEquals(
            listOf("kjv1769.spb"), first.bibleSettings.translations.map { it.fileName },
            "the stack itself has to hold it -- translationList() would report it either way, " +
                "because it falls back to the legacy pair",
        )
    }

    @Test
    fun `a bible installed with nothing configured becomes the one that presents`() {
        val after = AppSettings().withInstalledBible("downloaded.spb")

        assertEquals(listOf("downloaded.spb"), after.stack())
    }

    @Test
    fun `a bible installed alongside a configured stack does not join it`() {
        // The rule this restores is "become the presented bible if there isn't one". It had stopped
        // working in both directions: with a stack configured the old test was against a mirrored
        // field that is never empty, so the download vanished; with none, it created the drift.
        val before = settings()
        val after = before.withInstalledBible("downloaded.spb")

        assertEquals(before, after, "a stack the operator has set up is not to be added to behind them")
    }
}
