package org.churchpresenter.settings

import org.churchpresenter.settings.utils.Constants
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Versions 19 and 20: a Bible translation's X/Y offsets becoming text boxes, and the preview panel's
 * groups becoming a layout.
 *
 * What is worth pinning is what would be silent if wrong: an offset has to become a box that is on
 * and placed where it put its text, on the document and inside every profile; a page without offsets
 * has to be left without boxes; and groups have to become a layout that shows what they showed and
 * leaves out what they left out.
 */
class TextBoxMigrationTest {

    private lateinit var home: File
    private var realHome: String? = null

    @BeforeTest
    fun isolateHome() {
        realHome = System.getProperty("user.home")
        home = Files.createTempDirectory("cp-text-box-migration-test").toFile()
        System.setProperty("user.home", home.absolutePath)
    }

    @AfterTest
    fun restoreHome() {
        realHome?.let { System.setProperty("user.home", it) }
        home.deleteRecursively()
    }

    private fun decode(raw: String): AppSettings = SettingsManager().migrateAndDecode(raw)

    /**
     * Where the document's Bible settings end up: on the profile that carried none of its own, which
     * version 22 gives the document's copy before dropping it.
     */
    private fun AppSettings.documentBible(): BibleSettings =
        projectionSettings.outputProfiles.first { it.id == "default" }.bibleSettings

    // ── Version 19: offsets become boxes ────────────────────────────────────────────────────────

    private fun v18(documentTranslation: String) = """
        {"settingsVersion":18,
         "bibleSettings":{"translations":[$documentTranslation]},
         "projectionSettings":{"outputProfiles":[
           {"id":"default","name":"Default"},
           {"id":"lobby","name":"Lobby","bibleSettings":{"translations":[
             {"fileName":"rst.spb","lowerThirdReferenceOffset":{"xPercent":50,"yPercent":0}}]}}
         ]}}
    """.trimIndent()

    @Test
    fun `each offset becomes a box that is on and covers the frame`() {
        val translation = """{"fileName":"kjv.spb","textOffset":{"xPercent":50,"yPercent":100}}"""
        val bible = decode(v18(translation)).documentBible()
        val box = bible.textBoxes.boxAt(textBoxKey(BIBLE_TEXT_BOX, lowerThird = false, language = "kjv.spb"))
        assertTrue(box.enabled)
        assertEquals(0f, box.xPercent)
        assertEquals(TextBox.FULL_PERCENT, box.widthPercent)
        assertEquals(TextBox.FULL_PERCENT, box.heightPercent)
    }

    @Test
    fun `the old vertical percentage decides where in the box the text sits`() {
        fun verticalFor(y: Int): String {
            val translation = """{"fileName":"kjv.spb","textOffset":{"xPercent":50,"yPercent":$y}}"""
            val bible = decode(v18(translation)).documentBible()
            return bible.textBoxes.boxAt(textBoxKey(BIBLE_TEXT_BOX, false, "kjv.spb")).vertical
        }
        assertEquals(Constants.TOP, verticalFor(0))
        assertEquals(Constants.MIDDLE, verticalFor(50))
        assertEquals(Constants.BOTTOM, verticalFor(100))
    }

    @Test
    fun `a page that had an offset measures its boxes inside the margins, as the offset was`() {
        val translation = """{"fileName":"kjv.spb","referenceOffset":{"xPercent":10,"yPercent":10}}"""
        val bible = decode(v18(translation)).documentBible()
        assertTrue(bible.textBoxOptions.insideMargins)
    }

    @Test
    fun `every profile's own offsets become its own boxes`() {
        val profile = decode(v18("""{"fileName":"kjv.spb"}""")).projectionSettings.outputProfiles
            .first { it.id == "lobby" }
        val key = textBoxKey(BIBLE_REFERENCE_BOX, lowerThird = true, language = "rst.spb")
        val box = profile.bibleSettings.textBoxes.boxAt(key)
        assertTrue(box.enabled)
        assertEquals(Constants.TOP, box.vertical)
    }

    @Test
    fun `a page without offsets is left without boxes`() {
        val bible = decode(v18("""{"fileName":"kjv.spb"}""")).documentBible()
        assertTrue(bible.textBoxes.isEmpty())
        assertFalse(bible.textBoxOptions.insideMargins)
    }

    // ── Version 20: groups become a layout ──────────────────────────────────────────────────────

    private val bs0 = Constants.previewOutputKey(Constants.PREVIEW_OUTPUT_BROWSER_SOURCE, 0)

    @Test
    fun `groups become one layout, drawn, that leaves out what they left out`() {
        val raw = """
            {"settingsVersion":19,"projectionSettings":{"previewGroups":[
              {"id":"g","shape":"ONE_BY_ONE","members":["$bs0"]}]}}
        """.trimIndent()
        val proj = decode(raw).projectionSettings
        val layout = proj.activeLayout()
        assertEquals(listOf(bs0), layout?.root?.outputs())
        assertFalse(proj.listUnplacedOutputs, "outputs no group held stay out of the panel, as they did")
    }

    @Test
    fun `a panel without groups keeps listing every output`() {
        val proj = decode("""{"settingsVersion":19,"projectionSettings":{}}""").projectionSettings
        assertNull(proj.activeLayout())
        assertTrue(proj.previewLayouts.isEmpty())
    }

    @Test
    fun `a document that already has layouts keeps them`() {
        val raw = """
            {"settingsVersion":19,"projectionSettings":{
              "previewGroups":[{"id":"g","members":["$bs0"]}],
              "previewLayouts":[{"id":"mine","name":"Mine"}]}}
        """.trimIndent()
        val proj = decode(raw).projectionSettings
        assertEquals(listOf("mine"), proj.previewLayouts.map { it.id })
    }
}
