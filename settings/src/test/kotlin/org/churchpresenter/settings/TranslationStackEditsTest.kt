package org.churchpresenter.settings

import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Removing and reordering translations, with every output's selection carried along.
 *
 * An output names what it shows by position in the stack, so an edit to the stack moves the ground
 * under every selection. Left alone they point at whatever slid into that position — delete the
 * first of `[KJV, RST, NIV]` and the screen pinned to position 1 goes from Russian to NIV, silently,
 * which on a Sunday morning is a room full of people reading the wrong language.
 *
 * The stack is `[kjv, rst, niv]` throughout, with two outputs so it is visible that both a screen
 * and a browser source are covered.
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
            screenAssignments = listOf(
                ScreenAssignment(bibleMode = screenMode, bibleTranslations = screenSelection),
            ),
            browserSourceOutputs = listOf(ScreenAssignment(bibleTranslations = browserSelection)),
        ),
    )

    private fun AppSettings.screen() = projectionSettings.screenAssignments.single()
    private fun AppSettings.browserSource() = projectionSettings.browserSourceOutputs.single()
    private fun AppSettings.stack() = bibleSettings.translationList().map { it.fileName }

    // ── Removing ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `removing a translation shifts the selections that sat after it`() {
        // The screen was showing rst; after kjv goes, rst is position 0.
        val after = settings(screenSelection = listOf(1), browserSelection = listOf(2))
            .removeBibleTranslation(0)

        assertEquals(listOf("rst.spb", "niv.spb"), after.stack())
        assertEquals(listOf(0), after.screen().bibleTranslations, "the screen must still be showing rst")
        assertEquals(listOf(1), after.browserSource().bibleTranslations, "and the browser source still niv")
    }

    @Test
    fun `removing a translation leaves earlier selections alone`() {
        val after = settings(screenSelection = listOf(0)).removeBibleTranslation(2)

        assertEquals(listOf(0), after.screen().bibleTranslations)
    }

    @Test
    fun `a multi-translation selection keeps only what survives, renumbered`() {
        val after = settings(screenSelection = listOf(0, 1, 2)).removeBibleTranslation(1)

        assertEquals(listOf(0, 1), after.screen().bibleTranslations, "kjv and niv, now positions 0 and 1")
    }

    @Test
    fun `an output showing only the removed translation switches its scripture off`() {
        // Letting the selection fall empty would read as "all of them" and put both remaining
        // languages on a screen deliberately narrowed to one.
        val after = settings(screenSelection = listOf(1)).removeBibleTranslation(1)

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

    // ── Reordering ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `moving a translation down carries its own selection with it`() {
        val after = settings(screenSelection = listOf(0)).moveBibleTranslation(0, 1)

        assertEquals(listOf("rst.spb", "kjv.spb", "niv.spb"), after.stack())
        assertEquals(listOf(1), after.screen().bibleTranslations, "the screen follows kjv to position 1")
    }

    @Test
    fun `moving a translation shifts whatever it passed over`() {
        // rst was at 1 and kjv moves past it, so rst is now position 0.
        val after = settings(screenSelection = listOf(1)).moveBibleTranslation(0, 1)

        assertEquals(listOf(0), after.screen().bibleTranslations)
    }

    @Test
    fun `moving a translation up to the front renumbers everything it passed`() {
        val after = settings(screenSelection = listOf(0, 1)).moveBibleTranslation(2, -2)

        assertEquals(listOf("niv.spb", "kjv.spb", "rst.spb"), after.stack())
        assertEquals(listOf(1, 2), after.screen().bibleTranslations, "kjv and rst each moved down one")
    }

    @Test
    fun `a translation the move did not touch keeps its position`() {
        val after = settings(screenSelection = listOf(2)).moveBibleTranslation(0, 1)

        assertEquals(listOf(2), after.screen().bibleTranslations, "niv never moved")
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

    @Test
    fun `moving one up leaves a selection below the move where it was`() {
        // rst moves above kjv; niv sat below both and is untouched by it.
        val after = settings(screenSelection = listOf(2)).moveBibleTranslation(1, -1)

        assertEquals(listOf("rst.spb", "kjv.spb", "niv.spb"), after.stack())
        assertEquals(listOf(2), after.screen().bibleTranslations)
    }

    // ── Swapping ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the swap button exchanges the first two and both of their selections`() {
        val after = settings(screenSelection = listOf(0), browserSelection = listOf(1))
            .swapBibleTranslations()

        assertEquals(listOf("rst.spb", "kjv.spb", "niv.spb"), after.stack())
        assertEquals(listOf(1), after.screen().bibleTranslations)
        assertEquals(listOf(0), after.browserSource().bibleTranslations)
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
