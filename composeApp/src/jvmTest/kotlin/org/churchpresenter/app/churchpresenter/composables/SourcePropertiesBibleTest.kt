@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.churchpresenter.bible.SpbFixture
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.app.churchpresenter.dialogs.tabs.recolor
import org.churchpresenter.core.models.scene.SceneSource
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Bible source — the one panel that reads from outside the source it is editing.
 *
 * Everything below the divider is ordinary styling of two pieces of text, the verse and its
 * reference, each with its own font size, colour and bold/italic pair. What is different is
 * everything above it: a Bible version picker built from the `.spb` files in the configured storage
 * directory, and book, chapter and verse-range pickers driven by a `BibleViewModel` the panel
 * constructs for itself. The Insert button then joins the chosen verses' text and formats a
 * reference from them — the only control on the whole panel that *derives* what it writes rather
 * than passing it through.
 *
 * Two configurations are covered. With no [AppSettings] the panel must degrade to the styling
 * controls plus a "no Bible configured" notice, which is what an operator sees before the setup
 * wizard has run; a real `.spb` module written to a temporary directory covers the rest. Loading a
 * module is asynchronous, so the pickers are waited for by the condition that they exist rather than
 * by a pause.
 */
class SourcePropertiesBibleTest {

    /** Ordinals of the panel's fields with no Bible configured — the header owns the first six. */
    private object Field {
        const val VERSE_TEXT = 6
        const val REFERENCE = 7
        const val VERSE_FONT_SIZE = 8
        const val REFERENCE_FONT_SIZE = 9
    }

    /** With a Bible loaded, the two verse-range boxes come before the verse text. */
    private object LoadedField {
        const val START_VERSE = 6
        const val END_VERSE = 7
        const val VERSE_TEXT = 8
    }

    /** Ordinals of the panel's checkboxes: the verse's pair, then the reference's. */
    private object Check {
        const val BOLD = 0
        const val ITALIC = 1
        const val REFERENCE_BOLD = 2
        const val REFERENCE_ITALIC = 3
        const val COUNT = 4
    }

    /** Ordinals of the six alignment buttons, in the order the groups lay them out. */
    private object Align {
        const val RIGHT = 0
        const val LEFT = 2
        const val TOP = 5
    }

    private var storage: File? = null

    @AfterTest
    fun cleanUp() {
        storage?.deleteRecursively()
        storage = null
    }

    /** Settings pointing at a temporary directory holding one real `.spb` module. */
    private fun settingsWithBible(fileName: String = "kjv.spb", title: String = "King James"): AppSettings {
        val dir = storage ?: Files.createTempDirectory("cp-canvas-bible").toFile().also { storage = it }
        SpbFixture.spbFile(dir, name = fileName, content = SpbFixture.sampleContent(title = title))
        return AppSettings(
            bibleSettings = BibleSettings(storageDirectory = dir.absolutePath, primaryBible = fileName),
        )
    }

    /**
     * Waits for both asynchronous halves of this panel to land.
     *
     * The module load is what puts the book picker on screen. The version picker is separate: it is
     * filled from a listing of the bible folder plus a header read per module, which happen off the
     * composition thread and so arrive independently — waiting on the book picker alone would leave
     * every assertion about the version picker racing that read.
     */
    private fun ComposeUiTest.awaitBibleLoaded() {
        waitUntil("the Bible module must load and put a book picker on screen", timeoutMillis = 5_000) {
            countOf("BOOK") == 1
        }
        waitUntil("the bible folder listing must reach the version picker", timeoutMillis = 5_000) {
            countOf("BIBLE VERSION") == 1
        }
    }

    // ── With no Bible configured ──────────────────────────────────────────────

    @Test
    fun `with no Bible configured the panel says so and still offers every styling control`() =
        sourcePanel(Fixture.bible()) { _ ->
            onNodeWithText(Label.BIBLE).assertIsDisplayed()
            onNodeWithText("No Primary Bible Configured").assertExists("the operator must be told why")

            listOf(
                "VERSE TEXT", "REFERENCE", "Verse Style", "FONT", "FONT SIZE",
                "FONT COLOR", "Reference Style", "REFERENCE FONT SIZE", "REFERENCE COLOR",
                "BACKGROUND COLOR", "Horizontal", "Vertical", "Line Spacing",
            ).forEach { caption ->
                onNodeWithText(caption).assertExists("\"$caption\" must caption a control on the Bible panel")
            }
        }

    @Test
    fun `with no Bible configured no picker is offered`() = sourcePanel(Fixture.bible()) { _ ->
        listOf("BIBLE VERSION", "BOOK", "CHAPTER", "START VERSE", "END VERSE", "Insert Verse")
            .forEach { assertEquals(0, countOf(it), "\"$it\" needs a Bible module to be useful") }
    }

    @Test
    fun `the unconfigured panel adds four fields, four checkboxes and six alignment buttons`() =
        sourcePanel(Fixture.bible()) { _ ->
            textFields().assertCountEquals(10)
            checkboxes().assertCountEquals(Check.COUNT)
            roleButtons().assertCountEquals(6)
        }

    @Test
    fun `bold and italic are captioned twice, once for each piece of text`() =
        sourcePanel(Fixture.bible()) { _ ->
            assertEquals(2, countOf("Bold"), "the verse and the reference each get their own")
            assertEquals(2, countOf("Italic"))
        }

    @Test
    fun `every stored value is shown by the control that owns it`() {
        val styled = Fixture.bible().copy(
            verseText = "The LORD is my shepherd", referenceText = "Psalm 23:1",
            fontSize = 60, fontColor = "#FFEE00",
            referenceFontSize = 28, referenceFontColor = "#88AAFF",
            backgroundColor = "#101010", lineSpacing = 120,
        )
        sourcePanel(styled) { _ ->
            assertFieldShows("The LORD is my shepherd", "the verse text box")
            assertFieldShows("Psalm 23:1", "the reference box")
            assertFieldShows("60", "the verse font size field")
            assertFieldShows("28", "the reference font size field")
            onNodeWithText("#FFEE00").assertExists("the verse colour reads out its hex")
            onNodeWithText("#88AAFF").assertExists("the reference colour reads out its hex")
            onNodeWithText("#101010").assertExists("the background colour reads out its hex")
            onNodeWithText("120%").assertExists("the line spacing slider reads out percent")
        }
    }

    // ── The two text boxes ────────────────────────────────────────────────────

    @Test
    fun `typing verse text stores it and nothing else`() = sourcePanel(Fixture.bible()) { get ->
        typeField(Field.VERSE_TEXT, "For God so loved the world")

        assertEquals(
            Fixture.bible().copy(verseText = "For God so loved the world"), get(),
            "the verse box may write only the verse text",
        )
        assertFieldShows("For God so loved the world", "the verse text box after typing")
    }

    @Test
    fun `verse text with line breaks is stored whole`() = sourcePanel(Fixture.bible()) { get ->
        typeField(Field.VERSE_TEXT, "First line\nSecond line")

        assertEquals("First line\nSecond line", (get() as SceneSource.BibleSource).verseText)
    }

    @Test
    fun `typing a reference stores it and nothing else`() = sourcePanel(Fixture.bible()) { get ->
        typeField(Field.REFERENCE, "John 3:16")

        assertEquals(
            Fixture.bible().copy(referenceText = "John 3:16"), get(),
            "the reference box may write only the reference",
        )
        assertFieldShows("John 3:16", "the reference box after typing")
    }

    @Test
    fun `both text boxes can be cleared`() {
        sourcePanel(Fixture.bible().copy(verseText = "something", referenceText = "somewhere")) { get ->
            typeField(Field.VERSE_TEXT, "")
            typeField(Field.REFERENCE, "")

            val source = get() as SceneSource.BibleSource
            assertEquals("", source.verseText)
            assertEquals("", source.referenceText)
        }
    }

    // ── Font sizes ────────────────────────────────────────────────────────────

    @Test
    fun `typing a verse font size stores it`() = sourcePanel(Fixture.bible()) { get ->
        typeField(Field.VERSE_FONT_SIZE, "80")

        val source = get() as SceneSource.BibleSource
        assertEquals(80, source.fontSize)
        assertEquals(32, source.referenceFontSize, "and leaves the reference's own size alone")
    }

    @Test
    fun `typing a reference font size stores it`() = sourcePanel(Fixture.bible()) { get ->
        typeField(Field.REFERENCE_FONT_SIZE, "24")

        val source = get() as SceneSource.BibleSource
        assertEquals(24, source.referenceFontSize)
        assertEquals(48, source.fontSize, "and leaves the verse's own size alone")
    }

    @Test
    fun `text that is not a number leaves a font size alone`() = sourcePanel(Fixture.bible()) { get ->
        typeField(Field.VERSE_FONT_SIZE, "big")

        assertEquals(48, (get() as SceneSource.BibleSource).fontSize)
    }

    // ── Colours ───────────────────────────────────────────────────────────────

    @Test
    fun `recolouring the verse text stores the new hex`() {
        // The verse and the reference share a default colour, so the fixture separates them first.
        sourcePanel(Fixture.bible().copy(referenceFontColor = "#AAAAAA")) { get ->
            recolor(fromHex = "#FFFFFF", toHex = "#FFDD00")

            val source = get() as SceneSource.BibleSource
            assertEquals("#FFDD00", source.fontColor)
            assertEquals("#AAAAAA", source.referenceFontColor, "and the reference's colour is untouched")
        }
    }

    @Test
    fun `recolouring the reference stores the new hex`() {
        sourcePanel(Fixture.bible().copy(fontColor = "#AAAAAA")) { get ->
            recolor(fromHex = "#FFFFFF", toHex = "#3366FF")

            val source = get() as SceneSource.BibleSource
            assertEquals("#3366FF", source.referenceFontColor)
            assertEquals("#AAAAAA", source.fontColor, "and the verse's colour is untouched")
        }
    }

    @Test
    fun `recolouring the background stores the new hex`() = sourcePanel(Fixture.bible()) { get ->
        recolor(fromHex = "#00000000", toHex = "#202020")

        assertEquals("#202020", (get() as SceneSource.BibleSource).backgroundColor)
    }

    // ── The four style flags ──────────────────────────────────────────────────

    @Test
    fun `all four style flags are off out of the box`() = sourcePanel(Fixture.bible()) { _ ->
        repeat(Check.COUNT) { checkboxes()[it].assertIsOff() }
    }

    @Test
    fun `ticking the verse's Bold flips only that flag`() = sourcePanel(Fixture.bible()) { get ->
        toggleCheckbox(Check.BOLD)

        assertEquals(Fixture.bible().copy(bold = true), get(), "the verse's Bold owns only its own flag")
        checkboxes()[Check.BOLD].assertIsOn()
    }

    @Test
    fun `ticking the verse's Italic flips only that flag`() = sourcePanel(Fixture.bible()) { get ->
        toggleCheckbox(Check.ITALIC)

        assertEquals(Fixture.bible().copy(italic = true), get())
        checkboxes()[Check.ITALIC].assertIsOn()
    }

    @Test
    fun `ticking the reference's Bold flips only that flag`() = sourcePanel(Fixture.bible()) { get ->
        toggleCheckbox(Check.REFERENCE_BOLD)

        assertEquals(
            Fixture.bible().copy(referenceBold = true), get(),
            "the reference's Bold must not reach the verse's own flag",
        )
        checkboxes()[Check.REFERENCE_BOLD].assertIsOn()
    }

    @Test
    fun `ticking the reference's Italic flips only that flag`() = sourcePanel(Fixture.bible()) { get ->
        toggleCheckbox(Check.REFERENCE_ITALIC)

        assertEquals(Fixture.bible().copy(referenceItalic = true), get())
        checkboxes()[Check.REFERENCE_ITALIC].assertIsOn()
    }

    @Test
    fun `a flag stored on can be turned back off`() {
        sourcePanel(Fixture.bible().copy(bold = true, referenceBold = true)) { get ->
            toggleCheckbox(Check.BOLD)

            val source = get() as SceneSource.BibleSource
            assertEquals(false, source.bold)
            assertEquals(true, source.referenceBold, "the other one is untouched")
        }
    }

    // ── Alignment and line spacing ────────────────────────────────────────────

    @Test
    fun `aligning left stores left`() = sourcePanel(Fixture.bible()) { get ->
        roleButtons()[Align.LEFT].performScrollTo().performClick()
        waitForIdle()

        assertEquals("left", (get() as SceneSource.BibleSource).horizontalAlignment)
    }

    @Test
    fun `aligning right stores right`() = sourcePanel(Fixture.bible()) { get ->
        roleButtons()[Align.RIGHT].performScrollTo().performClick()
        waitForIdle()

        assertEquals("right", (get() as SceneSource.BibleSource).horizontalAlignment)
    }

    @Test
    fun `aligning to the top stores top`() = sourcePanel(Fixture.bible()) { get ->
        roleButtons()[Align.TOP].performScrollTo().performClick()
        waitForIdle()

        assertEquals("top", (get() as SceneSource.BibleSource).verticalAlignment)
        assertEquals(
            "center", (get() as SceneSource.BibleSource).horizontalAlignment,
            "the horizontal alignment is untouched",
        )
    }

    @Test
    fun `dragging line spacing to its near end is the tightest setting`() = sourcePanel(Fixture.bible()) { get ->
        tapSliderUnder("Line Spacing", fraction = 0f, gapDp = Gap.READOUT)

        assertEquals(50, (get() as SceneSource.BibleSource).lineSpacing, "the range starts at 50%")
        onNodeWithText("50%").assertExists()
    }

    @Test
    fun `dragging line spacing to its far end is the loosest setting`() = sourcePanel(Fixture.bible()) { get ->
        tapSliderUnder("Line Spacing", fraction = 1f, gapDp = Gap.READOUT)

        assertEquals(300, (get() as SceneSource.BibleSource).lineSpacing, "the range tops out at 300%")
        onNodeWithText("300%").assertExists()
    }

    // ── With a Bible module on disk ───────────────────────────────────────────

    @Test
    fun `a configured Bible replaces the notice with a full set of pickers`() {
        sourcePanel(Fixture.bible(), appSettings = settingsWithBible()) { _ ->
            awaitBibleLoaded()

            onNodeWithText("BIBLE VERSION").assertExists("the version picker is built from the storage folder")
            onNodeWithText("BOOK").assertExists()
            onNodeWithText("CHAPTER").assertExists()
            assertEquals(0, countOf("No Primary Bible Configured"), "and the notice is gone")
        }
    }

    /**
     * Two modules with no book in common, and a settings file whose stack is real.
     *
     * [settingsWithBible] deliberately builds the legacy shape — `primaryBible` with an empty
     * `translations` — which is the one configuration where the version picker used to appear to
     * work. `BibleViewModel` reads `translationList()`, and that only falls back to the legacy field
     * while the stack is empty, so a panel that hands its choice over as `primaryBible` moves nothing
     * at all on a machine that has been through the Bible settings tab. This fixture is that machine.
     */
    private fun settingsWithTwoBibles(): AppSettings {
        val dir = storage ?: Files.createTempDirectory("cp-canvas-bible").toFile().also { storage = it }
        SpbFixture.spbFile(dir, name = "kjv.spb", content = SpbFixture.sampleContent(title = "King James"))
        SpbFixture.spbFile(
            dir,
            name = "rst.spb",
            content = SpbFixture.buildContent(
                title = "Synodal",
                books = listOf(SpbFixture.Book(40, "Matthew", 1)),
                verses = listOf(SpbFixture.Verse(40, 1, 1, "The book of the generation of Jesus Christ.")),
            ),
        )
        return AppSettings(
            bibleSettings = BibleSettings(storageDirectory = dir.absolutePath).withTranslations(
                listOf(BibleTranslationSettings(fileName = "kjv.spb")),
            ),
        )
    }

    @Test
    fun `choosing another version loads that module`() {
        sourcePanel(Fixture.bible(), appSettings = settingsWithTwoBibles()) { _ ->
            awaitBibleLoaded()
            onNodeWithText("Genesis").assertExists("the stack's own bible is what loads first")

            chooseFromDropdown(showing = "King James", option = "Synodal")

            // Matthew belongs only to the second module, Psalms only to the first, so this cannot
            // pass on the first module still being loaded.
            waitUntil("the picked module's books must replace the previous module's", timeoutMillis = 5_000) {
                countOf("Matthew") >= 1 && countOf("Psalms") == 0 && countOf("Genesis") == 0
            }
        }
    }

    @Test
    fun `the version picker names the module by its own title, not its file name`() {
        sourcePanel(Fixture.bible(), appSettings = settingsWithBible(title = "King James")) { _ ->
            awaitBibleLoaded()

            onNodeWithText("King James").assertExists("the ##Title line inside the file is what is shown")
            assertEquals(0, countOf("kjv.spb"), "the file name is not shown")
        }
    }

    @Test
    fun `the book picker lists the module's books and starts on the first`() {
        sourcePanel(Fixture.bible(), appSettings = settingsWithBible()) { _ ->
            awaitBibleLoaded()

            onNodeWithText("Genesis").assertExists("the first book is selected to begin with")
            openDropdown(showing = "Genesis")
            listOf("Psalms", "John").forEach { book ->
                onNodeWithText(book).assertExists("\"$book\" must be offered")
            }
        }
    }

    @Test
    fun `choosing a book loads it`() {
        sourcePanel(Fixture.bible(), appSettings = settingsWithBible()) { _ ->
            awaitBibleLoaded()

            chooseFromDropdown(showing = "Genesis", option = "John")
            waitUntil("the chosen book must become the selection", timeoutMillis = 5_000) {
                countOf("John") >= 1 && countOf("Genesis") == 0
            }
        }
    }

    @Test
    fun `the verse range boxes appear once a chapter has verses`() {
        sourcePanel(Fixture.bible(), appSettings = settingsWithBible()) { _ ->
            awaitBibleLoaded()
            waitUntil("the verse range boxes must appear", timeoutMillis = 5_000) {
                countOf("START VERSE") == 1
            }

            onNodeWithText("END VERSE").assertExists()
            onNodeWithText("Insert Verse").assertExists()
            assertFieldShows("1", "the start verse box")
        }
    }

    @Test
    fun `Insert writes the chosen verse's text and a reference built from the selection`() {
        sourcePanel(Fixture.bible(), appSettings = settingsWithBible()) { get ->
            awaitBibleLoaded()
            waitUntil("the Insert button must appear", timeoutMillis = 5_000) { countOf("Insert Verse") == 1 }

            onNodeWithText("Insert Verse").performScrollTo().performClick()
            waitForIdle()

            val source = get() as SceneSource.BibleSource
            assertEquals(
                "In the beginning God created the heaven and the earth.", source.verseText,
                "Insert must pull the verse's own text out of the module",
            )
            assertEquals(
                "Genesis 1:1", source.referenceText,
                "and format a single-verse reference from the selection",
            )
        }
    }

    @Test
    fun `Insert joins a range of verses and formats a range reference`() {
        sourcePanel(Fixture.bible(), appSettings = settingsWithBible()) { get ->
            awaitBibleLoaded()
            waitUntil("the verse range boxes must appear", timeoutMillis = 5_000) { countOf("START VERSE") == 1 }

            typeField(LoadedField.END_VERSE, "3")
            onNodeWithText("Insert Verse").performScrollTo().performClick()
            waitForIdle()

            val source = get() as SceneSource.BibleSource
            assertEquals(
                "Genesis 1:1-3", source.referenceText,
                "a range reference names both ends",
            )
            assertTrue(
                source.verseText.startsWith("In the beginning") && source.verseText.endsWith("there was light."),
                "and the verses are joined in order, was \"${source.verseText}\"",
            )
        }
    }

    @Test
    fun `an end verse before the start is pulled up to it`() {
        sourcePanel(Fixture.bible(), appSettings = settingsWithBible()) { _ ->
            awaitBibleLoaded()
            waitUntil("the verse range boxes must appear", timeoutMillis = 5_000) { countOf("START VERSE") == 1 }

            typeField(LoadedField.START_VERSE, "3")

            assertFieldShows("3", "the end verse box, dragged up with the start")
        }
    }

    @Test
    fun `a verse past the end of the chapter is clamped to it`() {
        sourcePanel(Fixture.bible(), appSettings = settingsWithBible()) { _ ->
            awaitBibleLoaded()
            waitUntil("the verse range boxes must appear", timeoutMillis = 5_000) { countOf("START VERSE") == 1 }

            typeField(LoadedField.START_VERSE, "99")

            // The fixture's Genesis 1 has three verses.
            assertFieldShows("3", "the start verse box, clamped to the last verse in the chapter")
        }
    }

    @Test
    fun `the styling controls still work with a Bible loaded`() {
        sourcePanel(Fixture.bible(), appSettings = settingsWithBible()) { get ->
            awaitBibleLoaded()
            waitUntil("the verse range boxes must appear", timeoutMillis = 5_000) { countOf("START VERSE") == 1 }

            typeField(LoadedField.VERSE_TEXT, "Edited by hand")

            assertEquals(
                "Edited by hand", (get() as SceneSource.BibleSource).verseText,
                "an operator may always overwrite what Insert produced",
            )
        }
    }
}
