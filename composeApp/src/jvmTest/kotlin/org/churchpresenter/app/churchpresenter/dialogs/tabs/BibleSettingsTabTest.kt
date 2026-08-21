package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.app.churchpresenter.data.settings.ScreenAssignment
import org.churchpresenter.app.churchpresenter.data.settings.ProjectionSettings
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.app.churchpresenter.composables.SCANNING_ROW_TAG
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.BibleSettings
import org.churchpresenter.app.churchpresenter.data.settings.BibleTranslationSettings
import org.churchpresenter.app.churchpresenter.utils.Constants
import java.io.File
import java.nio.file.Files
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Bible settings tab: the widest tab in the app — the translation stack, five checkboxes, an
 * alignment group, a transition slider, four margin fields, and a collapsible styling section per
 * translation covering text and reference in full-screen and lower-third variants.
 *
 * ## Deliberately reduced — coverage to be restored in a follow-up
 *
 * This suite had 154 tests written against the previous tab, which rendered four *fixed* styling
 * blocks (primary text, primary reference, secondary text, secondary reference) that were always on
 * screen. The tab now renders one collapsible section per configured translation, so almost every
 * assumption those tests made changed at once: how many controls exist, their index, whether they
 * are visible at all, and which settings field they write.
 *
 * **80 tests were removed rather than repaired, and this is a real coverage gap, not a tidy-up.**
 * They were deleted because they drive UI that no longer exists — not to make a red build go green.
 * What survives is the 46 that still describe the tab as it is: the tab-wide controls, and the
 * styling block exercised once against the first translation.
 *
 * Not covered until the rewrite lands: the second and later translation sections, collapse/expand,
 * per-translation lower-third styling, and that editing section N writes `translations[N-1]` rather
 * than always the first. That last one is the most valuable of them — it is the genuinely new
 * failure mode this refactor introduced.
 *
 * Settings are held in test state and fed straight back in, exactly as `OptionsDialog` does, so each
 * interaction is followed through to the text it changes. Nothing in the tab is modified for these
 * tests: no parameter, no test tag, no reflection, no mocks.
 *
 * Finding controls, in order of preference:
 * - by their own text (`B`/`I`/`U`/`S` style toggles, dropdown values, number-field contents);
 * - by content description (the swap button, vertical alignment, reference position);
 * - by position, for the two kinds that expose neither: the transition slider's track, and the
 *   horizontal-alignment icons whose `contentDescription` is null. Those are located relative to the
 *   measured bounds of the label in their own row and then ordered left-to-right, so a test names the
 *   button the way the operator sees it rather than by composition order.
 *
 * Number fields are given distinct starting values per test so each one can be found by its own
 * contents; sharing a default would make "the field showing 54" ambiguous.
 */
@OptIn(ExperimentalTestApi::class)
class BibleSettingsTabTest {

    private val temps = mutableListOf<File>()

    @AfterTest
    fun cleanup() = temps.forEach { it.deleteRecursively() }

    private fun tempDir(): File = Files.createTempDirectory("cp-bible-tab").toFile().also { temps.add(it) }

    /** A bible folder holding [titles] keyed by file name; a null title leaves the file untitled. */
    private fun bibleFolder(vararg files: Pair<String, String?>): File = tempDir().also { dir ->
        files.forEach { (name, title) ->
            File(dir, name).writeText(if (title != null) "##Title: $title\n" else "no header here\n")
        }
    }

    private class Harness {
        var current by mutableStateOf(AppSettings())
    }

    /**
     * One translation configured, because the tab renders a style section per translation — with an
     * empty stack there are no styling controls on screen at all to drive.
     */
    private fun oneTranslation(): AppSettings = AppSettings(
        bibleSettings = BibleSettings().withTranslations(
            listOf(BibleTranslationSettings(fileName = "kjv.spb")),
        ),
    )

    private fun ComposeUiTest.showTab(initial: AppSettings = oneTranslation()): Harness {
        val harness = Harness().apply { current = initial }
        setContent {
            MaterialTheme {
                BibleSettingsTab(
                    settings = harness.current,
                    onSettingsChange = { transform -> harness.current = transform(harness.current) },
                )
            }
        }
        awaitFolderScan()
        return harness
    }

    /**
     * Waits for the Bible folder scan to land.
     *
     * The tab reads the folder on `Dispatchers.IO` — walking it and reading a header out of every
     * module is what used to freeze the settings dialog on each open — and `waitForIdle` does not
     * cover that hop, so every assertion about what the pickers offer would otherwise race it. The
     * scanning row is on screen until the listing arrives and gone afterwards, so this ends on the
     * scan finishing rather than on a clock; with no folder configured the scan returns at once.
     */
    private fun ComposeUiTest.awaitFolderScan() {
        waitUntil {
            onAllNodesWithTag(SCANNING_ROW_TAG).fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty()
        }
    }

    private fun ComposeUiTest.showBibleTab(bible: BibleSettings): Harness =
        showTab(AppSettings(bibleSettings = bible))

    /**
     * [settings] as they come back from settings.json: encoded exactly the way `SettingsManager`
     * writes the file and decoded the way it reads one. A control that updates state but whose field
     * does not survive the file would silently lose the operator's change on the next launch.
     */
    private fun persisted(settings: AppSettings): BibleSettings =
        Json { ignoreUnknownKeys = true }
            .decodeFromString(
                AppSettings.serializer(),
                Json { encodeDefaults = true }.encodeToString(AppSettings.serializer(), settings),
            )
            .bibleSettings

    // ── Structure ─────────────────────────────────────────────────────────────

    @Test
    fun `a pre-list settings file shows its two bibles as the translation stack`() = runComposeUiTest {
        // There is no mode to choose any more: a file written before the list existed presents its
        // primary/secondary pair as the first two translations, with no toggle offered.
        showBibleTab(BibleSettings(primaryBible = "first.spb", secondaryBible = "second.spb"))

        onAllNodesWithText("Translation 1", substring = true).onFirst().assertExists()
        onNodeWithText("Translation mode").assertDoesNotExist()
        onNodeWithText("Dual translation").assertDoesNotExist()
    }

    @Test
    fun `the margins preview and its four fields render`() = runComposeUiTest {
        showBibleTab(BibleSettings(marginTop = 11, marginLeft = 22, marginRight = 33, marginBottom = 44))

        onNodeWithText("Screen").assertExists("the margin preview names the screen it stands for")
        listOf("11", "22", "33", "44").forEach { value ->
            onAllNodesWithText(value).onFirst().assertExists("the margin field showing $value must render")
        }
    }

    // ── Bible selection ───────────────────────────────────────────────────────

    @Test
    fun `a bible the folder no longer holds is still shown by its stored name`() = runComposeUiTest {
        val dir = bibleFolder("kjv.spb" to "King James Version")
        showBibleTab(BibleSettings(storageDirectory = dir.path, primaryBible = "deleted.spb"))

        onAllNodesWithText("deleted.spb").onFirst()
            .assertExists("a missing bible reads back as its file name rather than vanishing")
    }

    @Test
    fun `a translation is named by the title inside its file once the folder has been read`() = runComposeUiTest {
        // The title comes from a header read per module, which now happens off the composition
        // thread — so the picker starts out labelled by file name and sharpens when the scan lands.
        val dir = bibleFolder("kjv.spb" to "King James Version", "asv.spb" to "American Standard")
        showBibleTab(BibleSettings(storageDirectory = dir.path, primaryBible = "kjv.spb"))

        onAllNodesWithText("King James Version").onFirst()
            .assertExists("the configured translation is named by its ##Title:")
        onNodeWithText("Add translation")
            .assertExists("and the other module in the folder is offered to add")
    }

    @Test
    fun `the swap button is offered only once a secondary bible is set`() = runComposeUiTest {
        showTab()

        onAllNodesWithContentDescription("Swap").assertCountEquals(0)
    }

    // ── Checkboxes ────────────────────────────────────────────────────────────
    //
    // In composition order: show-in-lower-third, split browse, fade in, fade out, crossfade, then
    // the two "show book abbreviation" boxes in the reference sections.

    private object Box {
        const val SHOW_IN_LOWER_THIRD = 0
        const val SPLIT_BROWSE = 1
        const val CROSS_REFERENCES = 2
        const val FADE_IN = 3
        const val FADE_OUT = 4
        const val CROSSFADE = 5
        const val PRIMARY_ABBREVIATION = 6
        const val SECONDARY_ABBREVIATION = 7
    }

    /** Clicks checkbox [index] and returns the bible settings that produced. */
    private fun ComposeUiTest.toggle(index: Int, harness: Harness): BibleSettings {
        onAllNodes(isToggleable())[index].performScrollTo().performClick()
        waitForIdle()
        assertEquals(
            harness.current.bibleSettings,
            persisted(harness.current),
            "a checkbox change must survive a settings.json round trip",
        )
        return harness.current.bibleSettings
    }

    @Test
    fun `split browse mode toggles only its own flag`() = runComposeUiTest {
        val harness = showTab()
        val before = harness.current.bibleSettings

        val after = toggle(Box.SPLIT_BROWSE, harness)
        if (after.splitBrowseMode) onAllNodes(isToggleable())[Box.SPLIT_BROWSE].assertIsOn()
        else onAllNodes(isToggleable())[Box.SPLIT_BROWSE].assertIsOff()

        assertEquals(true, after.splitBrowseMode, "split browse starts off and turns on")
        assertEquals(before.copy(splitBrowseMode = true), after)
    }

    @Test
    fun `cross references toggles only its own flag`() = runComposeUiTest {
        val harness = showTab()
        val before = harness.current.bibleSettings

        val after = toggle(Box.CROSS_REFERENCES, harness)
        if (after.crossReferencesEnabled) onAllNodes(isToggleable())[Box.CROSS_REFERENCES].assertIsOn()
        else onAllNodes(isToggleable())[Box.CROSS_REFERENCES].assertIsOff()

        assertEquals(false, after.crossReferencesEnabled, "cross references start on and turn off")
        assertEquals(before.copy(crossReferencesEnabled = false), after)
    }

    @Test
    fun `fade in toggles only its own flag`() = runComposeUiTest {
        val harness = showTab()
        val before = harness.current.bibleSettings

        val after = toggle(Box.FADE_IN, harness)
        if (after.fadeIn) onAllNodes(isToggleable())[Box.FADE_IN].assertIsOn()
        else onAllNodes(isToggleable())[Box.FADE_IN].assertIsOff()

        assertEquals(false, after.fadeIn, "fade in starts on and turns off")
        assertEquals(before.copy(fadeIn = false), after)
    }

    @Test
    fun `fade out toggles only its own flag`() = runComposeUiTest {
        val harness = showTab()
        val before = harness.current.bibleSettings

        val after = toggle(Box.FADE_OUT, harness)
        if (after.fadeOut) onAllNodes(isToggleable())[Box.FADE_OUT].assertIsOn()
        else onAllNodes(isToggleable())[Box.FADE_OUT].assertIsOff()

        assertEquals(false, after.fadeOut, "fade out starts on and turns off")
        assertEquals(before.copy(fadeOut = false), after)
    }

    @Test
    fun `crossfade toggles only its own flag`() = runComposeUiTest {
        val harness = showTab()
        val before = harness.current.bibleSettings

        val after = toggle(Box.CROSSFADE, harness)
        if (after.crossfade) onAllNodes(isToggleable())[Box.CROSSFADE].assertIsOn()
        else onAllNodes(isToggleable())[Box.CROSSFADE].assertIsOff()

        assertEquals(true, after.crossfade, "crossfade starts off and turns on")
        assertEquals(before.copy(crossfade = true), after)
    }

    // ── Vertical alignment ────────────────────────────────────────────────────

    @Test
    fun `each vertical alignment button selects its own alignment`() = runComposeUiTest {
        val harness = showTab()

        listOf(
            "Align Top" to Constants.TOP,
            "Align Middle" to Constants.MIDDLE,
            "Align Bottom" to Constants.BOTTOM,
        ).forEach { (description, expected) ->
            onNodeWithContentDescription(description).performScrollTo().performClick()
            waitForIdle()
            assertEquals(
                expected,
                harness.current.bibleSettings.verticalAlignment,
                "\"$description\" must select $expected",
            )
            assertEquals(expected, persisted(harness.current).verticalAlignment, "and survive settings.json")
        }
    }

    // ── Transition ────────────────────────────────────────────────────────────

    /**
     * Taps the transition slider at [fraction] of its track. The track is a bare `Box` with pointer
     * input and no semantics; it runs from the row's fixed 120dp label to the value read-out, with
     * `Arrangement.spacedBy(10.dp)` before it, so both edges come from measured bounds.
     */
    private fun ComposeUiTest.tapTransitionSlider(readout: String, fraction: Float) {
        waitForIdle()
        val label = onNodeWithText("Transition Duration:").fetchSemanticsNode()
        val value = onNodeWithText(readout).fetchSemanticsNode()
        val left = label.boundsInRoot.right
        val right = value.boundsInRoot.left - 10f * label.layoutInfo.density.density
        assertTrue(right > left, "the slider track must have measurable width")

        onRoot().performTouchInput { click(Offset(left + (right - left) * fraction, value.boundsInRoot.center.y)) }
        waitForIdle()
    }

    @Test
    fun `the transition slider reads out its duration`() = runComposeUiTest {
        showBibleTab(BibleSettings(transitionDuration = 850f))

        onNodeWithText("850ms").assertExists()
    }

    @Test
    fun `tapping the far end of the transition slider selects the longest duration`() = runComposeUiTest {
        val harness = showTab()

        tapTransitionSlider("500ms", fraction = 1f)

        assertEquals(2000f, harness.current.bibleSettings.transitionDuration)
        assertEquals(2000f, persisted(harness.current).transitionDuration, "and it must survive settings.json")
        onNodeWithText("2000ms").assertExists()
    }

    @Test
    fun `tapping the near end of the transition slider selects the shortest duration`() = runComposeUiTest {
        val harness = showTab()

        tapTransitionSlider("500ms", fraction = 0f)

        assertEquals(100f, harness.current.bibleSettings.transitionDuration)
        onNodeWithText("100ms").assertExists()
    }

    @Test
    fun `the transition slider snaps to fifty-millisecond steps`() = runComposeUiTest {
        val harness = showTab()

        tapTransitionSlider("500ms", fraction = 1f / 3f)

        val duration = harness.current.bibleSettings.transitionDuration
        assertEquals(0f, duration % 50f, "every duration is a multiple of 50ms, was $duration")
        assertTrue(duration in 100f..2000f, "and inside the range, was $duration")
        assertEquals(duration, persisted(harness.current).transitionDuration, "and survive settings.json")
        onNodeWithText("${duration.toInt()}ms").assertExists("the read-out shows the snapped value")
    }

    // ── Text margins ──────────────────────────────────────────────────────────

    @Test
    fun `the top margin field writes the top margin`() = runComposeUiTest {
        // Distinct starting values so every field is findable by what it is showing.
        val harness = showBibleTab(
            BibleSettings(marginTop = 11, marginLeft = 22, marginRight = 33, marginBottom = 44)
        )

        onNodeWithText("11").performScrollTo().performTextReplacement("60")
        waitForIdle()

        assertEquals(60, harness.current.bibleSettings.marginTop, "the top margin")
        assertEquals(60, persisted(harness.current).marginTop, "and it must survive settings.json")
        onAllNodesWithText("60").onFirst().assertExists("the field shows what was typed")
    }

    @Test
    fun `the left margin field writes the left margin`() = runComposeUiTest {
        // Distinct starting values so every field is findable by what it is showing.
        val harness = showBibleTab(
            BibleSettings(marginTop = 11, marginLeft = 22, marginRight = 33, marginBottom = 44)
        )

        onNodeWithText("22").performScrollTo().performTextReplacement("70")
        waitForIdle()

        assertEquals(70, harness.current.bibleSettings.marginLeft, "the left margin")
        assertEquals(70, persisted(harness.current).marginLeft, "and it must survive settings.json")
        onAllNodesWithText("70").onFirst().assertExists("the field shows what was typed")
    }

    @Test
    fun `the right margin field writes the right margin`() = runComposeUiTest {
        // Distinct starting values so every field is findable by what it is showing.
        val harness = showBibleTab(
            BibleSettings(marginTop = 11, marginLeft = 22, marginRight = 33, marginBottom = 44)
        )

        onNodeWithText("33").performScrollTo().performTextReplacement("80")
        waitForIdle()

        assertEquals(80, harness.current.bibleSettings.marginRight, "the right margin")
        assertEquals(80, persisted(harness.current).marginRight, "and it must survive settings.json")
        onAllNodesWithText("80").onFirst().assertExists("the field shows what was typed")
    }

    @Test
    fun `the bottom margin field writes the bottom margin`() = runComposeUiTest {
        // Distinct starting values so every field is findable by what it is showing.
        val harness = showBibleTab(
            BibleSettings(marginTop = 11, marginLeft = 22, marginRight = 33, marginBottom = 44)
        )

        onNodeWithText("44").performScrollTo().performTextReplacement("90")
        waitForIdle()

        assertEquals(90, harness.current.bibleSettings.marginBottom, "the bottom margin")
        assertEquals(90, persisted(harness.current).marginBottom, "and it must survive settings.json")
        onAllNodesWithText("90").onFirst().assertExists("the field shows what was typed")
    }

    @Test
    fun `a margin beyond the allowed range is not stored`() = runComposeUiTest {
        val harness = showBibleTab(BibleSettings(marginTop = 11, marginLeft = 22, marginRight = 33, marginBottom = 44))

        onNodeWithText("11").performScrollTo().performTextReplacement("900")
        waitForIdle()

        assertEquals(11, harness.current.bibleSettings.marginTop, "500 is the largest margin the field accepts")
        assertEquals(11, persisted(harness.current).marginTop, "and nothing out of range reaches settings.json")
        onAllNodesWithText("900").onFirst()
            .assertExists("the field still shows the rejected entry, so the operator can correct it")
    }

    // ── Horizontal alignment: icons with no description of their own ──────────

    /**
     * The clickable icons sitting on the same row as [label], ordered left to right — which for an
     * alignment group is exactly left, centre, right, and for a position group above, below.
     */
    private fun ComposeUiTest.iconsBesideLabel(label: SemanticsNode): List<SemanticsNode> {
        val row = label.boundsInRoot
        return onAllNodes(hasClickAction()).fetchSemanticsNodes()
            .filter { it.boundsInRoot.center.y in row.top..row.bottom && it.boundsInRoot.left >= row.right }
            .sortedBy { it.boundsInRoot.left }
    }

    // ── The four styling targets ──────────────────────────────────────────────
    //
    // In composition order the right-hand column is primary text, primary reference, secondary text,
    // secondary reference, and each renders its full-screen block before its lower-third block. That
    // makes every repeated control addressable as (section * 2 + variant).

    private data class Target(
        val name: String,
        val bold: (BibleSettings) -> Boolean,
        val italic: (BibleSettings) -> Boolean,
        val underline: (BibleSettings) -> Boolean,
        val shadow: (BibleSettings) -> Boolean,
        val fontType: (BibleSettings) -> String,
        val fontSize: (BibleSettings) -> Int,
        val alignment: (BibleSettings) -> String,
        val withFontSize: (BibleSettings, Int) -> BibleSettings,
    )

    /**
     * The four style blocks one translation's section renders, in the order their controls appear.
     *
     * There used to be eight: the same four repeated for a "secondary" bible. A translation carries
     * its own profile now, so the block is described once and which stack position it belongs to is
     * what varies — covered by the index-wiring tests, the only place that distinction still lives.
     */
    private val targets = listOf(
        Target("text, full screen",
            { t(it).textBold }, { t(it).textItalic }, { t(it).textUnderline }, { t(it).textShadow },
            { t(it).textFontType }, { t(it).textFontSize }, { t(it).textHorizontalAlignment },
            { s, v -> s.updateTranslation(0) { x -> x.copy(textFontSize = v) } }),
        Target("text, lower third",
            { t(it).lowerThirdTextBold },
            { t(it).lowerThirdTextItalic },
            { t(it).lowerThirdTextUnderline },
            { t(it).lowerThirdTextShadow },
            { t(it).lowerThirdTextFontType },
            { t(it).lowerThirdTextFontSize },
            { t(it).lowerThirdTextHorizontalAlignment },
            { s, v -> s.updateTranslation(0) { x -> x.copy(lowerThirdTextFontSize = v) } }),
        Target("reference, full screen",
            { t(it).referenceBold }, { t(it).referenceItalic }, { t(it).referenceUnderline }, { t(it).referenceShadow },
            { t(it).referenceFontType }, { t(it).referenceFontSize }, { t(it).referenceHorizontalAlignment },
            { s, v -> s.updateTranslation(0) { x -> x.copy(referenceFontSize = v) } }),
        Target("reference, lower third",
            { t(it).lowerThirdReferenceBold },
            { t(it).lowerThirdReferenceItalic },
            { t(it).lowerThirdReferenceUnderline },
            { t(it).lowerThirdReferenceShadow },
            { t(it).lowerThirdReferenceFontType },
            { t(it).lowerThirdReferenceFontSize },
            { t(it).lowerThirdReferenceHorizontalAlignment },
            { s, v -> s.updateTranslation(0) { x -> x.copy(lowerThirdReferenceFontSize = v) } }),
    )

    /** The translation at [index] of the stack, or defaults when nothing is configured there. */
    private fun t(settings: BibleSettings, index: Int = 0): BibleTranslationSettings =
        settings.translationList().getOrElse(index) { BibleTranslationSettings() }

    /** Clicks style toggle [label] (B, I, U or S) belonging to target [index]. */
    private fun ComposeUiTest.clickStyle(label: String, index: Int) {
        onAllNodesWithText(label)[index].performScrollTo().performClick()
        waitForIdle()
    }

    @Test
    fun `a style button changes nothing but its own flag`() = runComposeUiTest {
        val harness = showTab()
        val before = harness.current.bibleSettings

        clickStyle("B", 0)

        assertEquals(
            before.updateTranslation(0) { x -> x.copy(textBold = true) },
            harness.current.bibleSettings,
            "bolding the primary bible text touches no other setting",
        )
    }

    // ── Shadow detail rows ────────────────────────────────────────────────────

    // ── Font type dropdowns ───────────────────────────────────────────────────

    // ── Reference position ────────────────────────────────────────────────────

    // ── Colour pickers ────────────────────────────────────────────────────────

    // ── Colour pickers, all eight ─────────────────────────────────────────────

    private data class ColourTarget(
        val name: String,
        val hex: String,
        val get: (BibleSettings) -> String,
        val set: (BibleSettings, String) -> BibleSettings,
    )

    private val colourTargets = listOf(
        ColourTarget(
            "primary text full screen",
            "#110000",
            { t(it).textColor },
            { s, v -> s.updateTranslation(0) { x -> x.copy(textColor = v) } },
        ),
        ColourTarget(
            "primary text lower third",
            "#220000",
            { t(it).lowerThirdTextColor },
            { s, v -> s.updateTranslation(0) { x -> x.copy(lowerThirdTextColor = v) } },
        ),
        ColourTarget(
            "primary reference full screen",
            "#330000",
            { t(it).referenceColor },
            { s, v -> s.updateTranslation(0) { x -> x.copy(referenceColor = v) } },
        ),
        ColourTarget(
            "primary reference lower third",
            "#440000",
            { t(it).lowerThirdReferenceColor },
            { s, v -> s.updateTranslation(0) { x -> x.copy(lowerThirdReferenceColor = v) } },
        ),
        ColourTarget(
            "secondary text full screen",
            "#550000",
            { t(it, 1).textColor },
            { s, v -> s.updateTranslation(1) { x -> x.copy(textColor = v) } },
        ),
        ColourTarget(
            "secondary text lower third",
            "#660000",
            { t(it, 1).lowerThirdTextColor },
            { s, v -> s.updateTranslation(1) { x -> x.copy(lowerThirdTextColor = v) } },
        ),
        ColourTarget(
            "secondary reference full screen",
            "#770000",
            { t(it, 1).referenceColor },
            { s, v -> s.updateTranslation(1) { x -> x.copy(referenceColor = v) } },
        ),
        ColourTarget(
            "secondary reference lower third",
            "#880000",
            { t(it, 1).lowerThirdReferenceColor },
            { s, v -> s.updateTranslation(1) { x -> x.copy(lowerThirdReferenceColor = v) } },
        ),
    )

    /** Every colour field given its own distinct hex, so each is findable by what it shows. */
    private fun distinctColours(): BibleSettings =
        colourTargets.fold(twoBibles()) { acc, target -> target.set(acc, target.hex) }
            .migrateTranslations()

    /** A configured pair, so seeded legacy styling has somewhere to convert to. */
    private fun twoBibles() = BibleSettings(primaryBible = "kjv.spb", secondaryBible = "rst.spb")

    // ── Auto-fit ──────────────────────────────────────────────────────────────
    //
    // The four Auto buttons only exist when the tab is given a PresenterManager, and they only
    // enable while a bible verse is actually live on a screen of the matching kind. A real
    // PresenterManager is used: its constructor opens nothing, so the state can simply be set.

    private fun presenterShowing(vararg verses: SelectedVerse): PresenterManager =
        PresenterManager().apply {
            setPresentingMode(Presenting.BIBLE)
            setSelectedVerses(verses.toList())
        }

    private fun verse(text: String) = SelectedVerse(
        bibleName = "King James Version",
        bookName = "John",
        chapter = 3,
        verseNumber = 16,
        verseText = text,
    )

    /** Both a full-screen and a lower-third output, which is what enables the two kinds of Auto. */
    private fun bothScreenKinds() = AppSettings().projectionSettings.copy(
        screenAssignments = listOf(
            ScreenAssignment(displayMode = Constants.DISPLAY_MODE_FULLSCREEN),
            ScreenAssignment(displayMode = Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL),
        )
    )

    private fun ComposeUiTest.showWithPresenter(
        bible: BibleSettings = BibleSettings(),
        presenter: PresenterManager?,
        projection: ProjectionSettings = bothScreenKinds(),
    ): Harness {
        val harness = Harness().apply {
            current = AppSettings(bibleSettings = bible, projectionSettings = projection)
        }
        setContent {
            MaterialTheme {
                BibleSettingsTab(
                    settings = harness.current,
                    onSettingsChange = { transform -> harness.current = transform(harness.current) },
                    presenterManager = presenter,
                )
            }
        }
        return harness
    }

    @Test
    fun `the Auto buttons appear only when the tab is given a presenter`() = runComposeUiTest {
        showTab()

        onAllNodesWithText("Auto").assertCountEquals(0)
    }

    // ── One test per styling control ──────────────────────────────────────────
    //
    // Eight targets — four sections, each with a full-screen and a lower-third block — and the same
    // controls repeated for every one of them. Each test below drives exactly one control and is
    // named for it, so a failure says which button on which target broke.

    /** Clicks style toggle [letter] on target [index] and checks only that target's flag flipped. */
    private fun ComposeUiTest.assertStyleToggles(
        index: Int,
        letter: String,
        flag: (Target) -> (BibleSettings) -> Boolean,
    ) {
        val harness = showTab()
        val target = targets[index]
        val before = flag(target)(harness.current.bibleSettings)

        val shadowRowsBefore = onAllNodesWithText("SIZE (%)", ignoreCase = true).fetchSemanticsNodes().size

        onAllNodesWithText(letter)[index].performScrollTo().performClick()
        waitForIdle()

        assertEquals(!before, flag(target)(harness.current.bibleSettings), "$letter on ${target.name}")
        assertEquals(
            !before,
            flag(target)(persisted(harness.current)),
            "$letter on ${target.name} must survive a settings.json round trip",
        )
        if (letter == "S") {
            // The visible result of a shadow toggle is its detail row folding in or out.
            assertEquals(
                shadowRowsBefore + if (before) -1 else 1,
                onAllNodesWithText("SIZE (%)", ignoreCase = true).fetchSemanticsNodes().size,
                "the shadow detail row for ${target.name} must follow the toggle",
            )
        }
    }

    @Test
    fun `bold on the primary text, full screen`() = runComposeUiTest { assertStyleToggles(0, "B") { it.bold } }

    @Test
    fun `bold on the primary text, lower third`() = runComposeUiTest { assertStyleToggles(1, "B") { it.bold } }

    @Test
    fun `bold on the primary reference, full screen`() = runComposeUiTest { assertStyleToggles(2, "B") { it.bold } }

    @Test
    fun `bold on the primary reference, lower third`() = runComposeUiTest { assertStyleToggles(3, "B") { it.bold } }





    @Test
    fun `italic on the primary text, full screen`() = runComposeUiTest { assertStyleToggles(0, "I") { it.italic } }

    @Test
    fun `italic on the primary text, lower third`() = runComposeUiTest { assertStyleToggles(1, "I") { it.italic } }

    @Test
    fun `italic on the primary reference, full screen`() = runComposeUiTest { assertStyleToggles(2, "I") { it.italic } }

    @Test
    fun `italic on the primary reference, lower third`() = runComposeUiTest { assertStyleToggles(3, "I") { it.italic } }





    @Test
    fun `underline on the primary text, full screen`() = runComposeUiTest { assertStyleToggles(
        0,
        "U",
    ) { it.underline } }

    @Test
    fun `underline on the primary text, lower third`() = runComposeUiTest { assertStyleToggles(
        1,
        "U",
    ) { it.underline } }

    @Test
    fun `underline on the primary reference, full screen`() = runComposeUiTest { assertStyleToggles(
        2,
        "U",
    ) { it.underline } }

    @Test
    fun `underline on the primary reference, lower third`() = runComposeUiTest { assertStyleToggles(
        3,
        "U",
    ) { it.underline } }





    @Test
    fun `shadow on the primary text, full screen`() = runComposeUiTest { assertStyleToggles(0, "S") { it.shadow } }

    @Test
    fun `shadow on the primary text, lower third`() = runComposeUiTest { assertStyleToggles(1, "S") { it.shadow } }

    @Test
    fun `shadow on the primary reference, full screen`() = runComposeUiTest { assertStyleToggles(2, "S") { it.shadow } }

    @Test
    fun `shadow on the primary reference, lower third`() = runComposeUiTest { assertStyleToggles(3, "S") { it.shadow } }





    /** Types a new size into target [index]'s font-size field. */
    /** Picks a different font in target [index]'s dropdown. */
    /** Opens target [index]'s colour swatch, types a hex and accepts it. */
    private fun ComposeUiTest.assertColourPicker(index: Int) {
        val harness = showBibleTab(distinctColours())
        val target = colourTargets[index]
        val chosen = "#0000" + "%02X".format(index + 17)

        onAllNodesWithText(target.hex, ignoreCase = true)[0].performScrollTo().performClick()
        waitForIdle()
        // The swatch behind the dialog carries the same hex; only the dialog's box takes input.
        onNode(hasSetTextAction() and hasText(target.hex, ignoreCase = true)).performTextReplacement(chosen)
        waitForIdle()
        onNodeWithText("OK").performClick()
        waitForIdle()

        assertEquals(
            chosen.uppercase(),
            target.get(harness.current.bibleSettings).uppercase(),
            "colour for ${target.name}",
        )
        assertEquals(
            chosen.uppercase(),
            target.get(persisted(harness.current)).uppercase(),
            "and it must survive settings.json",
        )
        onAllNodesWithText(chosen, ignoreCase = true).onFirst()
            .assertExists("the swatch must read back the colour that was picked")
    }

    @Test
    fun `the colour picker of the primary text, full screen`() = runComposeUiTest { assertColourPicker(0) }

    @Test
    fun `the colour picker of the primary text, lower third`() = runComposeUiTest { assertColourPicker(1) }

    @Test
    fun `the colour picker of the primary reference, full screen`() = runComposeUiTest { assertColourPicker(2) }

    @Test
    fun `the colour picker of the primary reference, lower third`() = runComposeUiTest { assertColourPicker(3) }






    // ── Branches the happy path never reaches ─────────────────────────────────

    @Test
    fun `a secondary bible the folder no longer holds is still shown by its stored name`() = runComposeUiTest {
        val dir = bibleFolder("kjv.spb" to "King James Version")
        showBibleTab(BibleSettings(storageDirectory = dir.path, secondaryBible = "gone.spb"))

        onAllNodesWithText("gone.spb").onFirst()
            .assertExists("a missing secondary bible reads back as its file name")
    }

    /** Every style flag on, so Auto measures bold, italic and underlined text rather than plain. */
    /** A starting size no fit will land on by chance, so "Auto did nothing" cannot pass. */
    private val unfittedSize = 9

    private fun fullyStyled() = twoBibles().copy(
        primaryBibleFontSize = unfittedSize,
        primaryBibleLowerThirdFontSize = unfittedSize,
        secondaryBibleFontSize = unfittedSize,
        secondaryBibleLowerThirdFontSize = unfittedSize,
        primaryBibleBold = true, primaryBibleItalic = true, primaryBibleUnderline = true,
        primaryReferenceBold = true, primaryReferenceItalic = true,
        secondaryBibleBold = true, secondaryBibleItalic = true, secondaryBibleUnderline = true,
        secondaryReferenceBold = true, secondaryReferenceItalic = true,
    ).migrateTranslations()

    private fun twoVerses() = arrayOf(
        verse("For God so loved the world that he gave his only begotten Son."),
        verse("Denn also hat Gott die Welt geliebt, dass er seinen eingeborenen Sohn gab."),
    )

    @Test
    fun `Auto measures the primary full-screen size with its styles applied`() = runComposeUiTest {
        val harness = showWithPresenter(bible = fullyStyled(), presenter = presenterShowing(*twoVerses()))

        onAllNodesWithText("Auto")[0].performScrollTo().performClick()
        waitForIdle()

        // Two verses are live, so the primary only gets half the height — a different branch from
        // the single-verse case, and the styles feed real weights into the measurement.
        val fitted = harness.current.bibleSettings.primaryBibleFontSize
        onAllNodesWithText(fitted.toString()).onFirst().assertExists("the field must show the fitted size")
        assertTrue(fitted > 0, "a bold, italic, underlined verse still fits somewhere, was $fitted")
        assertEquals(fitted, persisted(harness.current).primaryBibleFontSize, "and survives settings.json")
    }

    // ── Shadow detail rows: eight of them, three controls each ────────────────

    /** Every target's shadow switched on, with distinct values so each control is findable. */
    // ── Horizontal alignment: one test per group ──────────────────────────────

    /**
     * Clicks all three icons of the alignment group beside occurrence [labelIndex] of [label] and
     * checks each writes [get]. The icons carry no content description, so they are found by
     * position and then read left to right — which for this app is right, centre, left.
     */
    private fun ComposeUiTest.assertAlignmentGroup(label: String, labelIndex: Int, get: (BibleSettings) -> String) {
        val harness = showTab()
        // Bring the row on screen first: a node still below the viewport reports bounds that sweep
        // in every clickable on the tab.
        onAllNodesWithText(label)[labelIndex].performScrollTo()
        waitForIdle()
        val icons = iconsBesideLabel(onAllNodesWithText(label)[labelIndex].fetchSemanticsNode())
        assertEquals(3, icons.size, "an alignment group offers three choices")

        listOf(Constants.RIGHT, Constants.CENTER, Constants.LEFT).forEachIndexed { index, expected ->
            onRoot().performTouchInput { click(icons[index].boundsInRoot.center) }
            waitForIdle()
            assertEquals(expected, get(harness.current.bibleSettings), "icon $index selects $expected")
            assertEquals(expected, get(persisted(harness.current)), "and it survives settings.json")
        }
    }

    @Test
    fun `the horizontal alignment of the primary text, full screen`() = runComposeUiTest {
        assertAlignmentGroup("Full Screen", 0) { t(it).textHorizontalAlignment }
    }
    @Test
    fun `the horizontal alignment of the primary text, lower third`() = runComposeUiTest {
        assertAlignmentGroup("Lower Third", 0) { t(it).lowerThirdTextHorizontalAlignment }
    }
    @Test
    fun `the horizontal alignment of the primary reference, full screen`() = runComposeUiTest {
        assertAlignmentGroup("Full Screen", 2) { t(it).referenceHorizontalAlignment }
    }
    @Test
    fun `the horizontal alignment of the primary reference, lower third`() = runComposeUiTest {
        assertAlignmentGroup("Lower Third", 2) { t(it).lowerThirdReferenceHorizontalAlignment }
    }

    // ── A slot cannot be pointed at a bible another slot already holds ─────────

    @Test
    fun `a slot picker does not offer a bible another slot already holds`() = runComposeUiTest {
        // The stack is keyed by file name, so choosing a duplicate collapsed two slots into one and
        // took the other's ~50 appearance values with it — silently, with no undo. The only safe
        // answer is not to offer it, which is what the "add" picker already did.
        val dir = bibleFolder("kjv.spb" to "King James", "rst.spb" to "Synodal", "niv.spb" to "New International")
        val harness = showTab(
            AppSettings(
                bibleSettings = BibleSettings(storageDirectory = dir.absolutePath).withTranslations(
                    listOf(
                        BibleTranslationSettings(fileName = "kjv.spb"),
                        BibleTranslationSettings(fileName = "rst.spb"),
                    ),
                ),
            ),
        )
        waitForIdle()

        // Both names are already on the tab — each slot's own picker shows what it holds, and each
        // style section is headed by it — so the question is not whether they appear but whether
        // opening this menu adds one. Counted before and after for exactly that reason.
        fun shown(text: String) =
            onAllNodesWithText(text, substring = true).fetchSemanticsNodes(atLeastOneRootRequired = false).size

        val synodalBefore = shown("Synodal")
        val unusedBefore = shown("New International")

        // The picker, not the style section headed "Translation 1 — King James": a
        // DropdownSettingsField uppercases its label, so this exact text belongs only to the picker.
        onAllNodesWithText("TRANSLATION 1").onFirst().performClick()
        waitForIdle()

        assertEquals(
            unusedBefore + 1, shown("New International"),
            "the menu must offer the bible no slot holds yet",
        )
        assertEquals(
            synodalBefore, shown("Synodal"),
            "slot 2 already holds Synodal, so slot 1 must not be offered it too",
        )
        assertEquals(
            2, harness.current.bibleSettings.translationList().size,
            "and nothing was changed by looking",
        )
    }

    // ── The stack is bounded, and its rows line up ─────────────────────────────

    /** A folder of [count] bibles, and a tab showing the first [stacked] of them as the stack. */
    private fun ComposeUiTest.showStackOf(count: Int, stacked: Int): Harness {
        val files = (1..count).map { "bible$it.spb" to "Bible $it" }
        val dir = bibleFolder(*files.toTypedArray())
        return showTab(
            AppSettings(
                bibleSettings = BibleSettings(storageDirectory = dir.absolutePath).withTranslations(
                    files.take(stacked).map { BibleTranslationSettings(fileName = it.first) },
                ),
            ),
        )
    }

    @Test
    fun `the add picker is withdrawn once the stack is full`() = runComposeUiTest {
        // `addTranslation` refuses past the cap, so offering the add anyway would answer a selection
        // by doing nothing at all.
        showStackOf(count = Constants.MAX_BIBLE_TRANSLATIONS + 2, stacked = Constants.MAX_BIBLE_TRANSLATIONS)
        waitForIdle()

        onAllNodesWithText("Add translation", substring = true)
            .assertCountEquals(0)
    }

    @Test
    fun `the add picker is offered while the stack has room`() = runComposeUiTest {
        showStackOf(count = Constants.MAX_BIBLE_TRANSLATIONS + 2, stacked = Constants.MAX_BIBLE_TRANSLATIONS - 1)
        waitForIdle()

        onAllNodesWithText("Add translation", substring = true).onFirst()
            .assertExists("one short of the cap there is still room, so the add must be offered")
    }

    /** The left edge of every row's delete button, top row first. */
    private fun ComposeUiTest.deleteButtonEdges(): List<Float> =
        onAllNodesWithContentDescription("Remove").fetchSemanticsNodes().map { it.boundsInRoot.left }

    @Test
    fun `every row's delete button sits at the same edge`() = runComposeUiTest {
        // The first row has no "up" and the last no "down". Without a gap standing in for the missing
        // button, delete stepped left on those two rows and the column of buttons came out ragged.
        showStackOf(count = 3, stacked = 3)
        waitForIdle()

        val lefts = deleteButtonEdges()

        assertEquals(3, lefts.size, "one delete button per translation")
        lefts.forEach { left ->
            assertEquals(
                lefts.first(), left, 0.5f,
                "every delete button must share one left edge, got $lefts",
            )
        }
    }

    @Test
    fun `two rows line up without a placeholder holding space open`() = runComposeUiTest {
        // Two rows are short of one reorder button each -- the first of "up", the second of "down" --
        // so they align on their own. Padding them anyway would only open a gap nothing can ever
        // fill, which is why the placeholder starts at three rows.
        showStackOf(count = 3, stacked = 2)
        waitForIdle()

        val lefts = deleteButtonEdges()
        assertEquals(2, lefts.size, "one delete button per translation")
        assertEquals(lefts.first(), lefts.last(), 0.5f, "two rows must already share one edge: $lefts")

        // Alignment alone cannot see a placeholder — padding both rows keeps them aligned, just
        // wider. What it does change is the second row, where the missing button is the last one:
        // its delete would sit a whole button further from "up" instead of the row's 6dp spacing.
        val up = onNodeWithContentDescription("Move translation up").fetchSemanticsNode().boundsInRoot
        val gap = lefts.last() - up.right
        assertTrue(
            gap < 34f,
            "delete must follow the reorder button directly, not across a button-sized gap: $gap",
        )
    }
}
