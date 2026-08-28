@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
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
 * reference, each with its own font size, colour and row of style buttons. What is different is
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
        const val LETTER_SPACING = 9
        const val CURVE = 10
        const val REFERENCE_FONT_SIZE = 11
    }

    /** With a Bible loaded, the two verse-range boxes come before the verse text. */
    private object LoadedField {
        const val START_VERSE = 6
        const val END_VERSE = 7
        const val VERSE_TEXT = 8
    }

    /**
     * Which row of style buttons a letter belongs to: the verse's comes first, the reference's
     * second, and both draw the same four letters.
     */
    private object StyleRow {
        const val VERSE = 0
        const val REFERENCE = 1
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
                "BACKGROUND COLOR", "Horizontal", "Vertical", "Letter Spacing", "Curve",
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
    fun `the unconfigured panel adds six fields, no checkbox and six alignment buttons`() =
        sourcePanel(Fixture.bible()) { _ ->
            textFields().assertCountEquals(12)
            checkboxes().assertCountEquals(0)
            // The style buttons publish no role of their own, so this counts the alignment ones.
            roleButtons().assertCountEquals(6)
        }

    @Test
    fun `every face is offered twice, once for each piece of text`() =
        sourcePanel(Fixture.bible()) { _ ->
            listOf("B", "I", "U", "S").forEach {
                assertEquals(2, countOf(it), "the verse and the reference each get their own \"$it\"")
            }
        }

    @Test
    fun `every stored value is shown by the control that owns it`() {
        val styled = Fixture.bible().copy(
            verseText = "The LORD is my shepherd", referenceText = "Psalm 23:1",
            fontSize = 60, fontColor = "#FFEE00",
            referenceFontSize = 28, referenceFontColor = "#88AAFF",
            backgroundColor = "#101010", letterSpacing = 30f, curve = -45f,
        )
        sourcePanel(styled) { _ ->
            assertFieldShows("The LORD is my shepherd", "the verse text box")
            assertFieldShows("Psalm 23:1", "the reference box")
            assertFieldShows("60", "the verse font size field")
            assertFieldShows("28", "the reference font size field")
            onNodeWithText("#FFEE00").assertExists("the verse colour reads out its hex")
            onNodeWithText("#88AAFF").assertExists("the reference colour reads out its hex")
            onNodeWithText("#101010").assertExists("the background colour reads out its hex")
            assertFieldShows("30", "the letter spacing input")
            assertFieldShows("-45", "the curve input")
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

    // ── The four style buttons, on each piece of text ─────────────────────────

    @Test
    fun `every face is off out of the box`() = sourcePanel(Fixture.bible()) { get ->
        val source = get() as SceneSource.BibleSource
        listOf(
            source.bold, source.italic, source.underline, source.strikethrough,
            source.referenceBold, source.referenceItalic,
            source.referenceUnderline, source.referenceStrikethrough,
        ).forEach { assertEquals(false, it) }
    }

    @Test
    fun `the verse's Bold flips only that flag`() = sourcePanel(Fixture.bible()) { get ->
        clickStyleButton("B", StyleRow.VERSE)

        assertEquals(Fixture.bible().copy(bold = true), get(), "the verse's B owns only its own flag")
    }

    @Test
    fun `the verse's Italic flips only that flag`() = sourcePanel(Fixture.bible()) { get ->
        clickStyleButton("I", StyleRow.VERSE)

        assertEquals(Fixture.bible().copy(italic = true), get())
    }

    @Test
    fun `the verse's Underline flips only that flag`() = sourcePanel(Fixture.bible()) { get ->
        clickStyleButton("U", StyleRow.VERSE)

        assertEquals(Fixture.bible().copy(underline = true), get())
    }

    @Test
    fun `the verse's Strikethrough flips only that flag`() = sourcePanel(Fixture.bible()) { get ->
        clickStyleButton("S", StyleRow.VERSE)

        assertEquals(Fixture.bible().copy(strikethrough = true), get())
    }

    @Test
    fun `the reference's Bold flips only that flag`() = sourcePanel(Fixture.bible()) { get ->
        clickStyleButton("B", StyleRow.REFERENCE)

        assertEquals(
            Fixture.bible().copy(referenceBold = true), get(),
            "the reference's B must not reach the verse's own flag",
        )
    }

    @Test
    fun `the reference's Italic flips only that flag`() = sourcePanel(Fixture.bible()) { get ->
        clickStyleButton("I", StyleRow.REFERENCE)

        assertEquals(Fixture.bible().copy(referenceItalic = true), get())
    }

    @Test
    fun `the reference's Underline flips only that flag`() = sourcePanel(Fixture.bible()) { get ->
        clickStyleButton("U", StyleRow.REFERENCE)

        assertEquals(Fixture.bible().copy(referenceUnderline = true), get())
    }

    @Test
    fun `the reference's Strikethrough flips only that flag`() = sourcePanel(Fixture.bible()) { get ->
        clickStyleButton("S", StyleRow.REFERENCE)

        assertEquals(Fixture.bible().copy(referenceStrikethrough = true), get())
    }

    @Test
    fun `a face stored on is turned back off by its own button`() {
        sourcePanel(Fixture.bible().copy(bold = true, referenceBold = true)) { get ->
            clickStyleButton("B", StyleRow.VERSE)

            val source = get() as SceneSource.BibleSource
            assertEquals(false, source.bold)
            assertEquals(true, source.referenceBold, "the other one is untouched")
        }
    }

    // ── Alignment, letter spacing and curve ───────────────────────────────────

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
    fun `dragging letter spacing to its far end tracks the verse out`() = sourcePanel(Fixture.bible()) { get ->
        tapSliderUnder("Letter Spacing", fraction = 1f, gapDp = Gap.INPUT)

        assertEquals(100f, (get() as SceneSource.BibleSource).letterSpacing, "the range tops out at 100%")
        assertFieldShows("100", "the letter spacing input follows the track")
    }

    @Test
    fun `dragging letter spacing to its near end tightens it past zero`() = sourcePanel(Fixture.bible()) { get ->
        tapSliderUnder("Letter Spacing", fraction = 0f, gapDp = Gap.INPUT)

        assertEquals(-20f, (get() as SceneSource.BibleSource).letterSpacing, "the range starts at -20%")
    }

    @Test
    fun `typing a letter spacing stores it`() = sourcePanel(Fixture.bible()) { get ->
        commitField(Field.LETTER_SPACING, "40")

        assertEquals(40f, (get() as SceneSource.BibleSource).letterSpacing)
    }

    @Test
    fun `dragging the curve arches the verse and its reference together`() = sourcePanel(Fixture.bible()) { get ->
        tapSliderUnder("Curve", fraction = 1f, gapDp = Gap.INPUT)

        assertEquals(200f, (get() as SceneSource.BibleSource).curve, "the curve runs to two full turns")
    }

    @Test
    fun `the verse is straight out of the box`() = sourcePanel(Fixture.bible()) { get ->
        val source = get() as SceneSource.BibleSource
        assertEquals(0f, source.curve)
        assertEquals(0f, source.letterSpacing)
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
